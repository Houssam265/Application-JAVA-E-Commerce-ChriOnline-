package com.chrionline.server;

import com.chrionline.model.Session;
import com.chrionline.model.User;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.service.AuthService;
import com.chrionline.service.ProductService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final Gson   GSON = new GsonBuilder().create();

    // ── Injected dependencies (shared across all threads) ────────────────────
    private final AuthService    authService;
    private final SessionManager sessionManager;
    private final ProductService productService;

    // ── Per-connection state ──────────────────────────────────────────────────
    private final Socket socket;

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
                         ProductService productService) {
        this.socket         = socket;
        this.authService    = authService;
        this.sessionManager = sessionManager;
        this.productService = productService;
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

        } catch (IOException e) {
            LOG.log(Level.INFO, "Connexion terminee avec " + clientId + ": " + e.getMessage());
        } finally {
            this.out = null;
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
        return sessionManager.isTokenValid(req.getToken());
    }

    // ── AUTH handlers ─────────────────────────────────────────────────────────

    /**
     * LOGIN — payload: {@code username}, {@code password}.
     *
     * <p>Authenticates via {@link AuthService#login(String, String)} (no
     * password logic here), creates a session, and returns the token + role.
     */
    private Response handleLogin(Request req) {
        String username = getPayloadString(req, "username");
        String password = getPayloadString(req, "password");

        if (username == null || username.isBlank()) {
            return Response.error("Le champ 'username' est requis.");
        }
        if (password == null || password.isBlank()) {
            return Response.error("Le champ 'password' est requis.");
        }

        try {
            User    user    = authService.login(username, password);
            Session session = sessionManager.createSession(user);

            Map<String, Object> payload = new HashMap<>();
            payload.put("userId",   user.getUserId());
            payload.put("username", user.getUsername());
            payload.put("email",    user.getEmail());
            payload.put("role",     user.getRole().name());

            // token is sent as the top-level Response.token field
            return new Response(true, "LOGIN_SUCCESS", payload, session.getToken());

        } catch (IllegalArgumentException e) {
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

            return new Response(true, "REGISTER_SUCCESS", payload, session.getToken());

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
        return Response.ok("LOGOUT_SUCCESS", null);
    }

    // ── CATALOGUE handlers ────────────────────────────────────────────────────

    /**
     * GET_PRODUCTS — optional payload.category_id for filtering.
     */
    private Response handleGetProducts(Request req) {
        Integer categoryId = req.getPayloadInt("category_id");
        List<?> products   = productService.getProducts(categoryId);
        return Response.ok(products);
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
        return productService.getProductDetails(productId)
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
}