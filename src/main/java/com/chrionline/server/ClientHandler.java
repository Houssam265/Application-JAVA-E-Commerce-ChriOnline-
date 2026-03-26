package com.chrionline.server;

import com.chrionline.model.Session;
import com.chrionline.model.User;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.service.AuthService;
import com.chrionline.service.AdminService;
import com.chrionline.service.CartService;
import com.chrionline.service.OrderService;
import com.chrionline.service.PaymentService;
import com.chrionline.service.ProductService;
import com.chrionline.service.LogService;
import com.chrionline.dao.LogDAO;
import com.chrionline.model.ServerLog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles one TCP client connection on its own thread.
 *
 * <p>Reads newline-delimited JSON {@link Request}s, dispatches them to the
 * appropriate service, and writes JSON {@link Response}s back.
 *
 * <p>Actions that do NOT require a session token: {@code LOGIN}, {@code REGISTER}.
 * Every other action calls {@link #requireValidToken(Request)} before processing.
 *
 * <p>Dependencies ({@link AuthService} and {@link SessionManager}) are injected via
 * constructor so that all client threads share the same singleton instances — no new
 * service object is created per connection.
 */
public class ClientHandler implements Runnable {

    private static final Logger LOG  = Logger.getLogger(ClientHandler.class.getName());
    private static final Gson   GSON = new GsonBuilder()
        .registerTypeAdapter(java.time.LocalDateTime.class, new com.google.gson.JsonSerializer<java.time.LocalDateTime>() {
            @Override
            public com.google.gson.JsonElement serialize(java.time.LocalDateTime src, java.lang.reflect.Type typeOfSrc, com.google.gson.JsonSerializationContext context) {
                return new com.google.gson.JsonPrimitive(src.toString());
            }
        })
        .registerTypeAdapter(java.time.LocalDateTime.class, new com.google.gson.JsonDeserializer<java.time.LocalDateTime>() {
            @Override
            public java.time.LocalDateTime deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type typeOfT, com.google.gson.JsonDeserializationContext context) throws com.google.gson.JsonParseException {
                return java.time.LocalDateTime.parse(json.getAsString());
            }
        })
        .create();

    // ── Injected dependencies (shared across all threads) ────────────────────
    private final AuthService    authService;
    private final SessionManager sessionManager;
    private final ProductService productService;
    private final CartService    cartService;
    private final OrderService   orderService;
    private final PaymentService paymentService;
    private final AdminService   adminService;
    private final UDPNotificationService udpNotificationService;
    private final LogService     logService;

    // ── Per-connection state ──────────────────────────────────────────────────
    private final Socket socket;
    private final InetAddress clientAddress;

    /** Token associated to the current TCP connection (revoked on disconnect). */
    private String connectionToken;

    /** userId of the authenticated user for this connection (0 = not yet authenticated). */
    private int connectedUserId = 0;

    /** Output writer — kept as a field so helper methods can write responses. */
    private PrintWriter out;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * @param socket         accepted client socket
     * @param authService    shared AuthService instance (created once in {@link Server})
     * @param sessionManager shared SessionManager instance (created once in {@link Server})
     * @param productService shared ProductService instance (created once in {@link Server})
     */
    public ClientHandler(Socket socket,
                         AuthService authService,
                         SessionManager sessionManager,
                         ProductService productService,
                         CartService cartService,
                         OrderService orderService,
                         PaymentService paymentService,
                         AdminService adminService,
                         LogService logService,
                         UDPNotificationService udpNotificationService) {
        this.socket         = socket;
        this.clientAddress  = socket.getInetAddress();
        this.authService    = authService;
        this.sessionManager = sessionManager;
        this.productService = productService;
        this.cartService    = cartService;
        this.orderService   = orderService;
        this.paymentService = paymentService;
        this.adminService   = adminService;
        this.logService     = logService;
        this.udpNotificationService = udpNotificationService;
    }

    public ClientHandler(Socket socket,
                         AuthService authService,
                         SessionManager sessionManager,
                         ProductService productService,
                         CartService cartService,
                         OrderService orderService,
                         PaymentService paymentService,
                         AdminService adminService,
                         UDPNotificationService udpNotificationService) {
        this(socket, authService, sessionManager, productService, cartService, orderService, paymentService, adminService, new LogService(new LogDAO()), udpNotificationService);
    }

    // ── Runnable ──────────────────────────────────────────────────────────────

    @Override
    public void run() {
        String clientId = socket.getRemoteSocketAddress().toString();
        LOG.info("Client connecte: " + clientId);

        try (Socket s = socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(
                     new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {

            this.out = writer;

            String line;
            while ((line = in.readLine()) != null) {
                String msg = line.trim();
                if (msg.isEmpty()) continue;
                LOG.info("[" + clientId + "] << " + msg);

                Response response = processRequest(msg);
                String   jsonOut  = GSON.toJson(response);
                out.println(jsonOut);
                LOG.info("[" + clientId + "] >> " + jsonOut);
            }

        } catch (SocketException e) {
            LOG.log(Level.INFO, "Connexion terminee avec " + clientId + ": " + e.getMessage());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Erreur reseau avec " + clientId + ": " + e.getMessage(), e);
        } finally {
            this.out = null;
            if (connectionToken != null) {
                sessionManager.invalidateSession(connectionToken);
                connectionToken = null;
            }
            if (connectedUserId > 0) {
                ClientRegistry.getInstance().unregister(connectedUserId);
                connectedUserId = 0;
            }
            LOG.info("Client deconnecte: " + clientId);
        }
    }

    // ── Request dispatcher ────────────────────────────────────────────────────

    /**
     * Parses the raw JSON line, identifies the action, and dispatches to the
     * appropriate handler method.
     */
    private Response processRequest(String jsonLine) {
        Request req;
        try {
            req = GSON.fromJson(jsonLine, Request.class);
        } catch (JsonSyntaxException e) {
            LOG.warning("Invalid JSON received: " + e.getMessage());
            return Response.error("Invalid JSON");
        }

        if (req == null || req.getAction() == null || req.getAction().isBlank()) {
            return Response.error("Missing action");
        }

        String action = req.getAction().trim();

        switch (action) {

            // ── Auth (no token required) ──────────────────────────────────
            case MessageProtocol.ACTION_LOGIN:
                return handleLogin(req);
            case MessageProtocol.ACTION_REGISTER:
                return handleRegister(req);

            // ── Auth (token required) ─────────────────────────────────────
            case MessageProtocol.ACTION_LOGOUT:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleLogout(req);

            // ── Catalogue (token required) ────────────────────────────────
            case MessageProtocol.ACTION_GET_PRODUCTS:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleGetProducts(req);
            case MessageProtocol.ACTION_GET_PRODUCT:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleGetProduct(req);
            case MessageProtocol.ACTION_GET_CATEGORIES:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleGetCategories(req);
            case MessageProtocol.GET_LOGS:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleGetLogs(req);
            case MessageProtocol.GET_LOGS_BY_USER:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleGetLogsByUser(req);

            // ── Cart (token required) ─────────────────────────────────────
            case MessageProtocol.ACTION_GET_CART:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleGetCart(req);
            case MessageProtocol.ACTION_ADD_TO_CART:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleAddToCart(req);
            case MessageProtocol.ACTION_UPDATE_CART_ITEM:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleUpdateCartItem(req);
            case MessageProtocol.ACTION_REMOVE_FROM_CART:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleRemoveFromCart(req);
            case MessageProtocol.ACTION_CLEAR_CART:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleClearCart(req);

            // ── Orders (token required) ───────────────────────────────────
            case MessageProtocol.ACTION_PLACE_ORDER:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handlePlaceOrder(req);
            case MessageProtocol.ACTION_PAYMENT:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handlePayment(req);
            case MessageProtocol.ACTION_GET_ORDERS:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleGetOrders(req);
            case MessageProtocol.ACTION_UPDATE_ORDER_STATUS:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleUpdateOrderStatus(req);
            case MessageProtocol.ACTION_GET_ORDER_DETAILS:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleGetOrderDetails(req);
            case MessageProtocol.ACTION_ADMIN_LIST_ORDERS:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleAdmin(req);

            // ── Profile (token required) ──────────────────────────────────
            case MessageProtocol.ACTION_UPDATE_PROFILE:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleUpdateProfile(req);
            case MessageProtocol.ACTION_CHANGE_PASSWORD:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleChangePassword(req);

            // ── Admin (token required) ─────────────────────────────────────
            case MessageProtocol.ACTION_ADMIN_CREATE_PRODUCT:
            case MessageProtocol.ACTION_ADMIN_UPDATE_PRODUCT:
            case MessageProtocol.ACTION_ADMIN_DELETE_PRODUCT:
            case MessageProtocol.ACTION_ADMIN_LIST_USERS:
            case MessageProtocol.ACTION_ADMIN_SET_USER_SUSPENDED:
            case MessageProtocol.ACTION_ADMIN_ADD_CATEGORY:
            case MessageProtocol.ACTION_ADMIN_UPDATE_CATEGORY:
            case MessageProtocol.ACTION_ADMIN_DELETE_CATEGORY:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleAdmin(req);

            default:
                return Response.error("Unknown or unsupported action: " + action);
        }
    }

    // ── Token validation helper ───────────────────────────────────────────────

    /**
     * Returns {@code true} when the request carries a valid, non-expired token.
     * Used as a gate at the top of every protected action handler.
     */
    private boolean requireValidToken(Request req) {
        String token = req.getToken();
        boolean valid = sessionManager.isTokenValid(token);
        if (valid && token != null) {
            connectionToken = token;
            // Register this client's IP so order-status notifications can reach it
            sessionManager.getUserFromToken(token).ifPresent(u -> {
                connectedUserId = u.getUserId();
                ClientRegistry.getInstance().register(u.getUserId(), clientAddress);
            });
        }
        return valid;
    }

    /** Best-effort UDP notification to the connected client (current connection). */
    private void sendUdpNotification(String type, String message, String orderId) {
        if (udpNotificationService == null || clientAddress == null) return;
        udpNotificationService.sendNotification(clientAddress, type, message, orderId);
    }

    /** Best-effort UDP notification to a specific user by their userId. */
    private void sendUdpNotificationToUser(int userId, String type, String message, String orderId) {
        if (udpNotificationService == null) return;
        java.net.InetAddress targetAddress = ClientRegistry.getInstance().getAddress(userId);
        if (targetAddress == null) {
            LOG.fine("[UDP] userId=" + userId + " not connected — notification skipped.");
            return;
        }
        udpNotificationService.sendNotification(targetAddress, type, message, orderId);
    }

    // ── AUTH handlers ─────────────────────────────────────────────────────────

    /**
     * LOGIN — payload: {@code email}, {@code password}.
     *
     * <p>Authenticates via {@link AuthService#login(String, String)} (no
     * password logic here), creates a session, and returns the token + role.
     */
    private Response handleLogin(Request req) {
        String email    = getPayloadString(req, "email");
        String password = getPayloadString(req, "password");

        if (email == null || email.isBlank()) {
            return Response.error("Le champ 'email' est requis.");
        }
        if (password == null || password.isBlank()) {
            return Response.error("Le champ 'password' est requis.");
        }

        try {
            User    user    = authService.login(email, password);
            Session session = sessionManager.createSession(user);

            Map<String, Object> payload = new HashMap<>();
            payload.put("userId",   user.getUserId());
            payload.put("username", user.getUsername());
            payload.put("email",    user.getEmail());
            payload.put("role",     user.getRole().name());

            // token is sent as the top-level Response.token field
            connectionToken = session.getToken();
            // Register client IP so UDP notifications can reach this user
            connectedUserId = user.getUserId();
            ClientRegistry.getInstance().register(user.getUserId(), clientAddress);
            Response r = new Response(true, "LOGIN_SUCCESS", payload, session.getToken());
            logService.logSuccess("LOGIN", user.getUserId());
            return r;

        } catch (IllegalArgumentException e) {
            logService.logError("LOGIN", null, "Invalid credentials from IP: " + clientAddress);
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[LOGIN] Unexpected error: " + e.getMessage(), e);
            return Response.error("Erreur serveur lors de la connexion.");
        }
    }

    /**
     * REGISTER — payload: {@code username}, {@code email}, {@code password}.
     *
     * <p>Registers via {@link AuthService#register(String, String, String)},
     * then immediately creates a session (auto-login after signup).
     */
    private Response handleRegister(Request req) {
        String username = getPayloadString(req, "username");
        String email    = getPayloadString(req, "email");
        String password = getPayloadString(req, "password");

        if (username == null || username.isBlank()) {
            return Response.error("Le champ 'username' est requis.");
        }
        if (email == null || email.isBlank()) {
            return Response.error("Le champ 'email' est requis.");
        }
        if (password == null || password.isBlank()) {
            return Response.error("Le champ 'password' est requis.");
        }

        try {
            User    user    = authService.register(username, email, password);
            Session session = sessionManager.createSession(user);

            Map<String, Object> payload = new HashMap<>();
            payload.put("userId",   user.getUserId());
            payload.put("username", user.getUsername());
            payload.put("email",    user.getEmail());
            payload.put("role",     user.getRole().name());

            connectionToken = session.getToken();
            Response r = new Response(true, "REGISTER_SUCCESS", payload, session.getToken());
            logService.logSuccess("REGISTER", null);
            return r;

        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[REGISTER] Unexpected error: " + e.getMessage(), e);
            return Response.error("Erreur serveur lors de l'inscription.");
        }
    }

    /**
     * LOGOUT — token from {@code request.getToken()}.
     *
     * <p>Invalidates the session in the SessionManager (HashMap + DB).
     */
    private Response handleLogout(Request req) {
        sessionManager.invalidateSession(req.getToken());
        if (req.getToken() != null && req.getToken().equals(connectionToken)) {
            connectionToken = null;
        }
        return Response.ok("LOGOUT_SUCCESS", null);
    }

    // ── CATALOGUE handlers ────────────────────────────────────────────────────

    /**
     * GET_PRODUCTS — optional payload.category_id for filtering.
     */
    private Response handleGetProducts(Request req) {
        Integer categoryId = req.getPayloadInt("category_id");
        User user = sessionManager.getUserFromToken(req.getToken()).orElse(null);
        boolean adminCatalog = user != null && authService.isAdmin(user);
        List<?> products = productService.getProducts(categoryId, adminCatalog);
        Response r = Response.ok(products);
        if (user != null) {
            logService.logSuccess("GET_PRODUCTS", user.getUserId());
        }
        return r;
    }

    /**
     * GET_PRODUCT — payload.product_id required.
     * Returns a map with "product" and "category" keys.
     */
    private Response handleGetProduct(Request req) {
        Integer productId = req.getPayloadInt("product_id");
        if (productId == null) {
            return Response.error("Missing product_id in payload");
        }
        User user = sessionManager.getUserFromToken(req.getToken()).orElse(null);
        boolean admin = user != null && authService.isAdmin(user);
        return productService.getProductDetails(productId, admin)
                .map(Response::ok)
                .orElse(Response.error("Product not found: " + productId));
    }

    /**
     * GET_CATEGORIES — no payload required.
     */
    private Response handleGetCategories(Request req) {
        List<?> categories = productService.getCategories();
        return Response.ok(categories);
    }

    // ── CART handlers ─────────────────────────────────────────────────────────

    private Response handleGetCart(Request req) {
        try {
            int userId = sessionManager.getUserFromToken(req.getToken())
                    .map(User::getUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
            return Response.ok(cartService.getCartView(userId));
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[CART] Unexpected error: " + e.getMessage(), e);
            return Response.error("Erreur serveur lors du chargement du panier.");
        }
    }

    private Response handleAddToCart(Request req) {
        try {
            int userId = sessionManager.getUserFromToken(req.getToken())
                    .map(User::getUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
            Integer productId = req.getPayloadInt("product_id");
            Integer quantity  = req.getPayloadInt("quantity");
            if (productId == null || quantity == null) {
                return Response.error("Missing product_id or quantity");
            }
            // Never trust client-provided price: server always reads product.price from DB
            cartService.addToCart(userId, productId, quantity);
            Response r = Response.ok("ADDED_TO_CART", cartService.getCartView(userId));
            logService.logSuccess("ADD_TO_CART", userId);
            return r;
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[CART] Unexpected error: " + e.getMessage(), e);
            return Response.error("Erreur serveur lors de l'ajout au panier.");
        }
    }

    private Response handleUpdateCartItem(Request req) {
        try {
            Integer cartItemId = req.getPayloadInt("cart_item_id");
            Integer quantity   = req.getPayloadInt("quantity");
            if (cartItemId == null || quantity == null) {
                return Response.error("Missing cart_item_id or quantity");
            }
            int userId = sessionManager.getUserFromToken(req.getToken())
                    .map(User::getUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
            cartService.updateCartItemQuantity(userId, cartItemId, quantity);
            return Response.ok(cartService.getCartView(userId));
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[CART] Unexpected error: " + e.getMessage(), e);
            return Response.error("Erreur serveur lors de la mise à jour du panier.");
        }
    }

    private Response handleRemoveFromCart(Request req) {
        try {
            Integer cartItemId = req.getPayloadInt("cart_item_id");
            if (cartItemId == null) {
                return Response.error("Missing cart_item_id");
            }
            int userId = sessionManager.getUserFromToken(req.getToken())
                    .map(User::getUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
            cartService.removeCartItem(userId, cartItemId);
            return Response.ok(cartService.getCartView(userId));
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[CART] Unexpected error: " + e.getMessage(), e);
            return Response.error("Erreur serveur lors de la suppression.");
        }
    }

    private Response handleClearCart(Request req) {
        try {
            int userId = sessionManager.getUserFromToken(req.getToken())
                    .map(User::getUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
            cartService.clearCartForUser(userId);
            return Response.ok(cartService.getCartView(userId));
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[CART] Unexpected error: " + e.getMessage(), e);
            return Response.error("Erreur serveur lors du vidage du panier.");
        }
    }

    // ── ORDER handlers ────────────────────────────────────────────────────────

    private Response handlePlaceOrder(Request req) {
        try {
            int userId = sessionManager.getUserFromToken(req.getToken())
                    .map(User::getUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
            com.chrionline.model.Order order = orderService.placeOrderFromCart(userId);
            sendUdpNotification(
                    "ORDER_VALIDATED",
                    "Commande " + order.getOrderId() + " validee.",
                    String.valueOf(order.getOrderId()));
            Response r = Response.ok(order);
            logService.logSuccess("PLACE_ORDER", userId);
            logService.logSuccess("CHECKOUT", userId);
            return r;
        } catch (IllegalArgumentException e) {
            int uid = sessionManager.getUserFromToken(req.getToken()).map(User::getUserId).orElse(0);
            Integer u = uid > 0 ? uid : null;
            logService.logError("PLACE_ORDER", u, e.getMessage());
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[ORDER] Unexpected error: " + e.getMessage(), e);
            return Response.error("Erreur serveur lors de la validation de la commande.");
        }
    }

    /**
     * KAN-7 — Paiement simulé : payload {@code order_id}, {@code card_number}, {@code expiry} (MM/YY), {@code cvv}.
     */
    private Response handlePayment(Request req) {
        try {
            int userId = sessionManager.getUserFromToken(req.getToken())
                    .map(User::getUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
            Integer orderId = firstNonBlankInt(
                    req.getPayloadInt("order_id"),
                    req.getPayloadInt("orderId"));
            String cardNumber = firstNonBlank(
                    getPayloadString(req, "card_number"),
                    getPayloadString(req, "cardNumber"));
            String expiry = firstNonBlank(
                    getPayloadString(req, "expiry"),
                    getPayloadString(req, "expiryMmYy"));
            String cvv = payloadValueAsString(req, "cvv");

            if (orderId == null || orderId <= 0
                    || cardNumber == null || cardNumber.isBlank()
                    || expiry == null || expiry.isBlank()
                    || cvv == null || cvv.isBlank()) {
                return Response.error("Champs requis : order_id, card_number, expiry (MM/YY), cvv.");
            }

            Map<String, Object> result = paymentService.processSimulatedCardPayment(
                    userId, orderId, cardNumber, expiry, cvv);

            if (Boolean.TRUE.equals(result.get("success"))) {
                sendUdpNotification(
                        "PAYMENT_CONFIRMED",
                        "Paiement confirme pour la commande " + orderId + ".",
                        String.valueOf(orderId));
                Response r = Response.ok("PAYMENT_OK", result);
                logService.logSuccess("PAYMENT", userId);
                return r;
            }
            String msg = result.get("message") != null ? String.valueOf(result.get("message")) : "Paiement refusé.";
            logService.logError("PAYMENT", userId, "Payment failed: " + msg);
            return Response.error(msg, result);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[PAYMENT] Unexpected error: " + e.getMessage(), e);
            return Response.error("Erreur serveur lors du paiement.");
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return null;
    }

    private static Integer firstNonBlankInt(Integer a, Integer b) {
        if (a != null && a > 0) return a;
        if (b != null && b > 0) return b;
        return null;
    }

    /** Chaîne ou nombre JSON (ex. CVV). */
    private static String payloadValueAsString(Request req, String key) {
        if (req.getPayload() == null) return null;
        Object v = req.getPayload().get(key);
        if (v == null) return null;
        return String.valueOf(v).trim();
    }

    private Response handleGetOrders(Request req) {
        try {
            User user = sessionManager.getUserFromToken(req.getToken())
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
            return Response.ok(orderService.getOrdersForUser(user.getUserId()));
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[ORDER] Unexpected error: " + e.getMessage(), e);
            return Response.error("Erreur serveur lors du chargement des commandes.");
        }
    }

    private Response handleUpdateOrderStatus(Request req) {
        try {
            User admin = sessionManager.getUserFromToken(req.getToken())
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
            if (!authService.isAdmin(admin)) {
                return Response.error("Accès refusé (ADMIN uniquement).");
            }
            Integer orderId = req.getPayloadInt("order_id");
            String status  = getPayloadString(req, "status");
            if (orderId == null || orderId <= 0 || status == null) {
                return Response.error("Missing order_id or status");
            }
            orderService.updateOrderStatus(orderId, com.chrionline.model.OrderStatus.valueOf(status));

            // Look up the order owner to send the UDP notification to THEM (not the admin)
            orderService.getOrderDetails(orderId).ifPresent(order -> {
                int ownerId = order.getUserId();
                String friendlyStatus = switch (status) {
                    case "VALIDATED" -> "Validée";
                    case "SHIPPED"   -> "Expédiée";
                    case "DELIVERED" -> "Livrée";
                    case "CANCELLED" -> "Annulée";
                    default          -> status;
                };
                String msg = "Votre commande est maintenant : " + friendlyStatus + ".";
                sendUdpNotificationToUser(ownerId, "ORDER_STATUS_UPDATED", msg, String.valueOf(orderId));
            });

            Response r = Response.ok("STATUS_UPDATED", null);
            logService.logSuccess("UPDATE_ORDER_STATUS", admin.getUserId(), "new status: " + status);
            return r;
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[ORDER] Unexpected error: " + e.getMessage(), e);
            return Response.error("Erreur serveur lors de la mise à jour du statut.");
        }
    }

    // ── ORDER DETAILS handler ─────────────────────────────────────────────

    /**
     * GET_ORDER_DETAILS — payload.order_id required.
     * Returns a map with "order" and "items" keys.
     */
    private Response handleGetOrderDetails(Request req) {
        Integer orderId = req.getPayloadInt("order_id");
        if (orderId == null || orderId <= 0) {
            return Response.error("Missing order_id");
        }
        try {
            return orderService.getOrderDetailsWithTimeline(orderId)
                    .map(Response::ok)
                    .orElse(Response.error("Order not found: " + orderId));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[ORDER_DETAILS] Unexpected error: " + e.getMessage(), e);
            return Response.error("Erreur serveur lors du chargement de la commande.");
        }
    }

    private Response handleGetLogs(Request req) {
        User admin = sessionManager.getUserFromToken(req.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
        if (!authService.isAdmin(admin)) {
            return Response.error("Accès refusé (ADMIN uniquement).");
        }
        List<com.chrionline.model.ServerLog> logs = logService.getRecentLogs(50);
        return Response.ok(logs);
    }

    private Response handleGetLogsByUser(Request req) {
        User admin = sessionManager.getUserFromToken(req.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
        if (!authService.isAdmin(admin)) {
            return Response.error("Accès refusé (ADMIN uniquement).");
        }
        Integer uid = req.getPayloadInt("user_id");
        if (uid == null || uid <= 0) {
            return Response.error("Missing user_id");
        }
        List<com.chrionline.model.ServerLog> logs = logService.getLogsByUser(uid);
        return Response.ok(logs);
    }

    // ── PROFILE handlers ──────────────────────────────────────────────────

    /**
     * UPDATE_PROFILE — payload: "username", "email".
     * Returns updated username + email so the client can refresh ClientSession.
     */
    private Response handleUpdateProfile(Request req) {
        try {
            int userId = sessionManager.getUserFromToken(req.getToken())
                    .map(com.chrionline.model.User::getUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
            String username = getPayloadString(req, "username");
            String email    = getPayloadString(req, "email");
            if (username == null || username.isBlank()) {
                return Response.error("Le champ 'username' est requis.");
            }
            if (email == null || email.isBlank()) {
                return Response.error("Le champ 'email' est requis.");
            }
            com.chrionline.model.User updated = authService.updateProfile(userId, username, email);
            Map<String, Object> payload = new HashMap<>();
            payload.put("username", updated.getUsername());
            payload.put("email",    updated.getEmail());
            return Response.ok("PROFILE_UPDATED", payload);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[PROFILE] Unexpected error: " + e.getMessage(), e);
            return Response.error("Erreur serveur lors de la mise à jour du profil.");
        }
    }

    /**
     * CHANGE_PASSWORD — payload: "old_password", "new_password".
     */
    private Response handleChangePassword(Request req) {
        try {
            int userId = sessionManager.getUserFromToken(req.getToken())
                    .map(com.chrionline.model.User::getUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
            String oldPwd = getPayloadString(req, "old_password");
            String newPwd = getPayloadString(req, "new_password");
            if (oldPwd == null || oldPwd.isBlank()) {
                return Response.error("Le champ 'old_password' est requis.");
            }
            if (newPwd == null || newPwd.isBlank()) {
                return Response.error("Le champ 'new_password' est requis.");
            }
            authService.changePassword(userId, oldPwd, newPwd);
            return Response.ok("PASSWORD_CHANGED", null);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[PASSWORD] Unexpected error: " + e.getMessage(), e);
            return Response.error("Erreur serveur lors du changement de mot de passe.");
        }
    }

    private Response handleAdmin(Request req) {
        try {
            User admin = sessionManager.getUserFromToken(req.getToken())
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
            if (!authService.isAdmin(admin)) {
                return Response.error("Accès refusé (ADMIN uniquement).");
            }

            switch (req.getAction()) {
                case MessageProtocol.ACTION_ADMIN_CREATE_PRODUCT: {
                    com.chrionline.model.Product p = new com.chrionline.model.Product();
                    Integer categoryId = req.getPayloadInt("category_id");
                    String name = getPayloadString(req, "name");
                    Object desc = req.getPayload() != null ? req.getPayload().get("description") : null;
                    Object img  = req.getPayload() != null ? req.getPayload().get("image_url") : null;
                    Object price = req.getPayload() != null ? req.getPayload().get("price") : null;
                    Object stock = req.getPayload() != null ? req.getPayload().get("stock") : null;

                    if (categoryId == null || name == null) return Response.error("Missing category_id or name");
                    p.setCategoryId(categoryId);
                    p.setName(name);
                    p.setDescription(desc != null ? String.valueOf(desc) : "");
                    applyProductImages(req, p, img);
                    if (price instanceof Number) p.setPrice(((Number) price).doubleValue());
                    if (stock instanceof Number) p.setStock(((Number) stock).intValue());
                    return Response.ok(adminService.createProduct(p));
                }
                case MessageProtocol.ACTION_ADMIN_UPDATE_PRODUCT: {
                    Integer productId = req.getPayloadInt("product_id");
                    Integer categoryId = req.getPayloadInt("category_id");
                    String name = getPayloadString(req, "name");
                    Object desc = req.getPayload() != null ? req.getPayload().get("description") : null;
                    Object img  = req.getPayload() != null ? req.getPayload().get("image_url") : null;
                    Object price = req.getPayload() != null ? req.getPayload().get("price") : null;
                    Object stock = req.getPayload() != null ? req.getPayload().get("stock") : null;
                    if (productId == null || categoryId == null || name == null) return Response.error("Missing fields");
                    com.chrionline.model.Product p = new com.chrionline.model.Product();
                    p.setProductId(productId);
                    p.setCategoryId(categoryId);
                    p.setName(name);
                    p.setDescription(desc != null ? String.valueOf(desc) : "");
                    applyProductImages(req, p, img);
                    if (price instanceof Number) p.setPrice(((Number) price).doubleValue());
                    if (stock instanceof Number) p.setStock(((Number) stock).intValue());
                    adminService.updateProduct(p);
                    return Response.ok("UPDATED", null);
                }
                case MessageProtocol.ACTION_ADMIN_DELETE_PRODUCT: {
                    Integer productId = req.getPayloadInt("product_id");
                    if (productId == null) return Response.error("Missing product_id");
                    adminService.deleteProduct(productId);
                    return Response.ok("DELETED", null);
                }
                case MessageProtocol.ACTION_ADMIN_LIST_USERS: {
                    return Response.ok(adminService.listUsers());
                }
                case MessageProtocol.ACTION_ADMIN_SET_USER_SUSPENDED: {
                    Integer userId = req.getPayloadInt("user_id");
                    Object suspended = req.getPayload() != null ? req.getPayload().get("suspended") : null;
                    boolean isSuspended = suspended instanceof Boolean ? (Boolean) suspended : Boolean.parseBoolean(String.valueOf(suspended));
                    if (userId == null) return Response.error("Missing user_id");
                    if (userId == admin.getUserId()) return Response.error("Impossible de suspendre votre propre compte.");
                    adminService.setUserSuspended(userId, isSuspended);
                    return Response.ok("USER_UPDATED", null);
                }
                case MessageProtocol.ACTION_ADMIN_LIST_ORDERS: {
                    return Response.ok(adminService.listOrders());
                }
                // ── Admin Category CRUD (KAN-18) ──────────────────────────
                case MessageProtocol.ACTION_ADMIN_ADD_CATEGORY: {
                    String name = getPayloadString(req, "name");
                    String desc = getPayloadString(req, "description");
                    return adminService.addCategory(name, desc);
                }
                case MessageProtocol.ACTION_ADMIN_UPDATE_CATEGORY: {
                    Integer id  = req.getPayloadInt("id");
                    String name = getPayloadString(req, "name");
                    String desc = getPayloadString(req, "description");
                    if (id == null || id <= 0) return Response.error("Missing id");
                    return adminService.updateCategory(id, name, desc);
                }
                case MessageProtocol.ACTION_ADMIN_DELETE_CATEGORY: {
                    Integer id = req.getPayloadInt("id");
                    if (id == null || id <= 0) return Response.error("Missing id");
                    return adminService.deleteCategory(id);
                }
                default:
                    return Response.error("Unsupported admin action");
            }
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[ADMIN] Unexpected error: " + e.getMessage(), e);
            return Response.error("Erreur serveur admin.");
        }
    }

    // ── Payload extraction helpers ────────────────────────────────────────────

    /**
     * Safe extraction of a String value from {@code request.getPayload()}.
     * Returns {@code null} if the key is missing or the value is not a String.
     */
    private String getPayloadString(Request req, String key) {
        if (req.getPayload() == null) return null;
        Object v = req.getPayload().get(key);
        return v instanceof String ? ((String) v).trim() : null;
    }

    private void applyProductImages(Request req, com.chrionline.model.Product product, Object currentImageUrlObj) {
        List<String> imageUrls = resolveProductImageUrls(req, currentImageUrlObj);
        product.setImageUrls(imageUrls);
        product.setImageUrl(imageUrls.isEmpty() ? null : imageUrls.get(0));
    }

    /**
     * If image_base64 is provided by admin UI, stores the file under uploads/images
     * and returns the absolute path to persist in image_url.
     * Otherwise, falls back to payload.image_url.
     */
    private String resolveProductImageUrl(Request req, Object currentImageUrlObj) {
        if (req.getPayload() != null) {
            Object b64Obj = req.getPayload().get("image_base64");
            if (b64Obj instanceof String b64 && !b64.isBlank()) {
                try {
                    byte[] bytes = Base64.getDecoder().decode(b64);
                    String originalName = getPayloadString(req, "image_filename");
                    String ext = extractImageExtension(originalName);

                    Path uploadDir = Paths.get("uploads", "images").toAbsolutePath().normalize();
                    Files.createDirectories(uploadDir);

                    String storedName = UUID.randomUUID() + ext;
                    Path target = uploadDir.resolve(storedName).normalize();
                    Files.write(target, bytes);
                    return target.toString();
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Image invalide (base64).");
                } catch (Exception e) {
                    throw new RuntimeException("Erreur lors de l'enregistrement de l'image: " + e.getMessage(), e);
                }
            }
        }
        return currentImageUrlObj == null || "null".equals(String.valueOf(currentImageUrlObj))
                ? null
                : String.valueOf(currentImageUrlObj);
    }

    private List<String> resolveProductImageUrls(Request req, Object currentImageUrlObj) {
        List<String> resolved = new ArrayList<>();
        if (req.getPayload() == null) {
            String single = resolveProductImageUrl(req, currentImageUrlObj);
            if (single != null && !single.isBlank()) resolved.add(single);
            return resolved;
        }

        Object imagesObj = req.getPayload().get("images");
        Integer primaryIndex = req.getPayloadInt("primary_image_index");
        if (imagesObj instanceof List<?> images && !images.isEmpty()) {
            for (Object item : images) {
                if (!(item instanceof Map<?, ?> map)) continue;
                Object kindObj = map.get("kind");
                String kind = kindObj == null ? "" : String.valueOf(kindObj);
                if ("existing".equalsIgnoreCase(kind)) {
                    Object urlObj = map.get("image_url");
                    if (urlObj != null) {
                        String url = String.valueOf(urlObj).trim();
                        if (!url.isBlank() && !"null".equalsIgnoreCase(url)) {
                            resolved.add(url);
                        }
                    }
                    continue;
                }
                if ("new".equalsIgnoreCase(kind)) {
                    Object b64Obj = map.get("image_base64");
                    if (b64Obj == null) continue;
                    String b64 = String.valueOf(b64Obj).trim();
                    if (b64.isBlank()) continue;
                    String filename = map.get("image_filename") == null ? null : String.valueOf(map.get("image_filename"));
                    resolved.add(storeProductImage(b64, filename));
                }
            }
        }

        if (resolved.isEmpty()) {
            String single = resolveProductImageUrl(req, currentImageUrlObj);
            if (single != null && !single.isBlank()) {
                resolved.add(single);
            }
        }

        if (!resolved.isEmpty() && primaryIndex != null && primaryIndex >= 0 && primaryIndex < resolved.size()) {
            String primary = resolved.remove((int) primaryIndex);
            resolved.add(0, primary);
        }

        return resolved;
    }

    private String storeProductImage(String base64, String originalName) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            String ext = extractImageExtension(originalName);

            Path uploadDir = Paths.get("uploads", "images").toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);

            String storedName = UUID.randomUUID() + ext;
            Path target = uploadDir.resolve(storedName).normalize();
            Files.write(target, bytes);
            return target.toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Image invalide (base64).");
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'enregistrement de l'image: " + e.getMessage(), e);
        }
    }

    private String extractImageExtension(String filename) {
        if (filename == null) return ".png";
        String name = filename.trim().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return ".png";
        String ext = name.substring(dot);
        return switch (ext) {
            case ".png", ".jpg", ".jpeg", ".gif", ".webp" -> ext;
            default -> ".png";
        };
    }
}
