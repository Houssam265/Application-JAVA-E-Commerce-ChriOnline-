package com.chrionline.server;

import com.chrionline.dao.UserDAO;
import com.chrionline.model.Session;
import com.chrionline.model.User;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.service.AuthService;
import com.chrionline.service.AdminAuthService;
import com.chrionline.service.AdminService;
import com.chrionline.service.CartService;
import com.chrionline.service.LoginCaptchaService;
import com.chrionline.service.OrderService;
import com.chrionline.service.PaymentService;
import com.chrionline.service.ProductService;
import com.chrionline.security.AESUtil;
import com.chrionline.security.HybridCryptoUtil;
import com.chrionline.security.InputValidator;
import com.chrionline.security.RSAUtil;
import com.chrionline.security.ValidationException;
import com.chrionline.security.IpUtils;
import com.chrionline.security.SecurityAuditLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
import java.security.KeyPair;
import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    private static final Logger LOG  = LogManager.getLogger(ClientHandler.class);
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

    // ── Progressive anti-bruteforce (rate limiting + lockout + IP blocking) ────────────────────
    private static final int  MAX_LOGIN_ATTEMPTS = 12;  // Increased to 12 for progressive security
    private static final long LOGIN_WINDOW_MS    = 60_000L;   // 1 minute
    private static final long LOGIN_LOCKOUT_MS   = 5 * 60_000L; // 5 minutes
    private static final long ORDER_REPLAY_WINDOW_MS = 2 * 60_000L;
    private static final long PAYMENT_REPLAY_WINDOW_MS = 2 * 60_000L;
    private static final long OPERATION_NONCE_WINDOW_MS = 2 * 60_000L;
    private static final ConcurrentMap<String, Long> SEEN_ORDER_REQUESTS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Long> SEEN_PAYMENT_REQUESTS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, OperationNonce> ISSUED_OPERATION_NONCES = new ConcurrentHashMap<>();

    private static final class OperationNonce {
        final int userId;
        final String action;
        final String scope;
        final long expiresAt;

        OperationNonce(int userId, String action, String scope, long expiresAt) {
            this.userId = userId;
            this.action = action;
            this.scope = scope;
            this.expiresAt = expiresAt;
        }
    }

    // ── Injected dependencies (shared across all threads) ────────────────────
    private final AuthService    authService;
    private final UserDAO        userDAO;
    private final SessionManager sessionManager;
    private final ProductService productService;
    private final CartService    cartService;
    private final OrderService   orderService;
    private final PaymentService paymentService;
    private final AdminService   adminService;
    private final AdminAuthService adminAuthService;
    private final UDPNotificationService udpNotificationService;
    private final LoginCaptchaService loginCaptchaService = LoginCaptchaService.getInstance();

    /** Cle RSA serveur (partagee entre tous les threads) - utilisee pour le handshake hybride RSA -> AES. */
    private final KeyPair sessionRsaKeyPair;

    // ── Per-connection state ──────────────────────────────────────────────────
    private final Socket socket;
    private final InetAddress clientAddress;

    /** Token associated to the current TCP connection (revoked on disconnect). */
    private String connectionToken;

    /** userId of the authenticated user for this connection (0 = not yet authenticated). */
    private int connectedUserId = 0;

    /** Output writer — kept as a field so helper methods can write responses. */
    private PrintWriter out;

    /** Indique qu'il faut fermer la connexion TCP apres la prochaine reponse. */
    private boolean disconnectAfterResponse = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * @param socket         accepted client socket
     * @param authService    shared AuthService instance (created once in {@link Server})
     * @param sessionManager shared SessionManager instance (created once in {@link Server})
     * @param productService shared ProductService instance (created once in {@link Server})
     */
    public ClientHandler(Socket socket,
                         UserDAO userDAO,
                         AuthService authService,
                         SessionManager sessionManager,
                         ProductService productService,
                         CartService cartService,
                         OrderService orderService,
                         PaymentService paymentService,
                         AdminService adminService,
                         UDPNotificationService udpNotificationService,
                         AdminAuthService adminAuthService,
                         KeyPair sessionRsaKeyPair) {
        this.socket         = socket;
        this.clientAddress  = socket.getInetAddress();
        this.userDAO        = userDAO;
        this.authService    = authService;
        this.sessionManager = sessionManager;
        this.productService = productService;
        this.cartService    = cartService;
        this.orderService   = orderService;
        this.paymentService = paymentService;
        this.adminService   = adminService;
        this.udpNotificationService = udpNotificationService;
        this.adminAuthService = adminAuthService;
        this.sessionRsaKeyPair = sessionRsaKeyPair;
    }

    // ── Runnable ──────────────────────────────────────────────────────────────

    @Override
    public void run() {
        String clientId = socket.getRemoteSocketAddress().toString();
        LOG.info("Client connecte: {}", clientId);

        try (Socket s = socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(
                     new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {

            this.out = writer;

            sendHelloFrame();

            String line;
            while ((line = in.readLine()) != null) {
                String msg = line.trim();
                if (msg.isEmpty()) continue;
                LOG.info("[{}] << {}", clientId, msg);

                Response response = processRequest(msg);
                String   jsonOut  = GSON.toJson(response);
                out.println(jsonOut);
                LOG.info("[{}] >> {}", clientId, jsonOut);

                if (disconnectAfterResponse) {
                    break;
                }
            }

        } catch (SocketException e) {
            LOG.info("Connexion terminee avec {}: {}", clientId, e.getMessage());
        } catch (IOException e) {
            LOG.warn("Erreur reseau avec {}: {}", clientId, e.getMessage(), e);
        } finally {
            this.out = null;
            // Do not invalidate server session on transient socket disconnects.
            // Session must end on explicit LOGOUT or natural expiration.
            connectionToken = null;
            if (connectedUserId > 0) {
                ClientRegistry.getInstance().unregister(connectedUserId);
                connectedUserId = 0;
            }
            LOG.info("Client deconnecte: {}", clientId);
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
            LOG.warn("Invalid JSON received: {}", e.getMessage());
            return Response.error("Invalid JSON");
        }

        if (req == null || req.getAction() == null || req.getAction().isBlank()) {
            return Response.error("INVALID_INPUT: action is required.");
        }

        String action = req.getAction().trim();
        try {
            validateRequestInputForAction(action, req);
        } catch (ValidationException e) {
            return invalidInput(e.getMessage());
        }

        switch (action) {

            // ── Auth (no token required) ──────────────────────────────────
            case MessageProtocol.ACTION_LOGIN:
                if (isIpBlocked()) return Response.error("Votre adresse IP est bloquee pour securite. Contactez l'administrateur.");
                return handleLogin(req);
            case MessageProtocol.ACTION_REGISTER:
                if (isIpBlocked()) return Response.error("Votre adresse IP est bloquee pour securite. Contactez l'administrateur.");
                return handleRegister(req);
            case MessageProtocol.ACTION_VERIFY_EMAIL:
                if (isIpBlocked()) return Response.error("Votre adresse IP est bloquee pour securite. Contactez l'administrateur.");
                return handleVerifyEmail(req);
            case MessageProtocol.ACTION_RESEND_VERIFICATION_EMAIL:
                if (isIpBlocked()) return Response.error("Votre adresse IP est bloquee pour securite. Contactez l'administrateur.");
                return handleResendVerificationEmail(req);
            case MessageProtocol.ACTION_VERIFY_LOGIN_IP:
                if (isIpBlocked()) return Response.error("Votre adresse IP est bloquee pour securite. Contactez l'administrateur.");
                return handleVerifyLoginIp(req);
            case MessageProtocol.ACTION_RESEND_LOGIN_IP_VERIFICATION:
                if (isIpBlocked()) return Response.error("Votre adresse IP est bloquee pour securite. Contactez l'administrateur.");
                return handleResendLoginIpVerification(req);
            case MessageProtocol.ACTION_FORGOT_PASSWORD:
                if (isIpBlocked()) return Response.error("Votre adresse IP est bloquee pour securite. Contactez l'administrateur.");
                return handleForgotPassword(req);
            case MessageProtocol.ACTION_RESET_PASSWORD:
                if (isIpBlocked()) return Response.error("Votre adresse IP est bloquee pour securite. Contactez l'administrateur.");
                return handleResetPassword(req);
            case MessageProtocol.ACTION_GET_LOGIN_CAPTCHA:
                if (isIpBlocked()) return Response.error("Votre adresse IP est bloquee pour securite. Contactez l'administrateur.");
                return handleGetLoginCaptcha();
            case MessageProtocol.ACTION_GET_LOGIN_SECURITY_STATE:
                return handleGetLoginSecurityState(req);

            // ── Admin Auth (Challenge Response) ───────────────────────────
            case MessageProtocol.ACTION_ADMIN_CHALLENGE_REQUEST:
                return handleAdminChallengeRequest(req);
            case MessageProtocol.ACTION_ADMIN_CHALLENGE_VERIFY:
                return handleAdminChallengeVerify(req);

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
            case MessageProtocol.ACTION_GET_TOP_SELLING_PRODUCTS:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleGetTopSellingProducts(req);
            case MessageProtocol.ACTION_GET_RECENT_PRODUCTS:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleGetRecentProducts(req);
            case MessageProtocol.ACTION_GET_OPERATION_NONCE:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleGetOperationNonce(req);

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
                return wrapHybridResponseIfRequested(req, handlePayment(req));
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
            case MessageProtocol.ACTION_ACTIVATE_ADMIN_ACCESS:
                if (!requireValidToken(req)) return Response.error("Invalid or expired session");
                return handleActivateAdminAccess(req);

            // ── Admin (token required) ─────────────────────────────────────
            case MessageProtocol.ACTION_ADMIN_CREATE_PRODUCT:
            case MessageProtocol.ACTION_ADMIN_UPDATE_PRODUCT:
            case MessageProtocol.ACTION_ADMIN_DELETE_PRODUCT:
            case MessageProtocol.ACTION_ADMIN_LIST_USERS:
            case MessageProtocol.ACTION_ADMIN_UPDATE_USER_ROLE:
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
        if (!valid || token == null) {
            return false;
        }

        String currentIp = getClientIpAddress();

        // Verifie la coherence IP / session: si l'IP change en cours de session,
        // on invalide immediatement la session et on deconnecte.
        if (!sessionManager.isSessionIpConsistent(token, currentIp)) {
            String previousIp = sessionManager.getSessionIp(token);
            String username = sessionManager.getUserFromToken(token)
                    .map(User::getUsername)
                    .orElse(null);
            SecurityAuditLogger.logSessionIpChangeDetected(username, previousIp, currentIp);
            sessionManager.invalidateSession(token);
            return false;
        }

        sessionManager.refreshSession(token);
        connectionToken = token;

        // Register this client's IP so order-status notifications can reach it
        sessionManager.getUserFromToken(token).ifPresent(u -> {
            connectedUserId = u.getUserId();
            ClientRegistry.getInstance().register(u.getUserId(), clientAddress);
        });

        return true;
    }

    // ── Handshake hybride RSA -> AES ──────────────────────────────────────────

    /**
     * Envoie un message HELLO contenant la cle publique RSA du serveur.
     *
     * <p>Format JSON (newline-delimited) :
     * <pre>
     *   {"success":true,"message":"HELLO","payload":{"serverPublicKey":"&lt;base64-x509&gt;","algorithm":"RSA","keySize":2048}}
     * </pre>
     *
     * <p>Le client lit cette premiere ligne juste apres le handshake TLS, decode la cle
     * et l'utilise plus tard pour chiffrer une cle AES (cf. {@code HybridCryptoUtil}).
     */
    private void sendHelloFrame() {
        if (out == null || sessionRsaKeyPair == null) return;
        try {
            String publicKeyBase64 = RSAUtil.encodePublicKey(sessionRsaKeyPair.getPublic());
            Map<String, Object> payload = new HashMap<>();
            payload.put("serverPublicKey", publicKeyBase64);
            payload.put("algorithm", "RSA");
            payload.put("keySize", 2048);
            payload.put("transformation", HybridCryptoUtil.RSA_TRANSFORMATION);
            payload.put("aesTransformation", AESUtil.TRANSFORMATION);
            Response hello = Response.ok(MessageProtocol.MESSAGE_HELLO, payload);
            String json = GSON.toJson(hello);
            out.println(json);
            LOG.info("[HELLO] cle publique RSA envoyee au client {}", socket.getRemoteSocketAddress());
        } catch (Exception e) {
            LOG.warn("[HELLO] Impossible d'envoyer la cle publique RSA: {}", e.getMessage(), e);
        }
    }

    /**
     * Si la requete contient une cle AES chiffree, dechiffre cette cle et chiffre
     * le payload de la reponse en AES-GCM. Le client recoit alors :
     * <pre>
     *   payload = { "encryptedPayload": "...", "aesIv": "..." }
     * </pre>
     */
    private Response wrapHybridResponseIfRequested(Request req, Response response) {
        if (req == null || req.getPayload() == null) return response;
        Object encryptedAesKeyObj = req.getPayload().get(MessageProtocol.KEY_ENCRYPTED_AES_KEY);
        if (!(encryptedAesKeyObj instanceof String) || ((String) encryptedAesKeyObj).isBlank()) {
            return response;
        }
        if (!response.isSuccess() || sessionRsaKeyPair == null) {
            return response;
        }
        try {
            SecretKey aesKey = HybridCryptoUtil.unwrapAesKey((String) encryptedAesKeyObj, sessionRsaKeyPair.getPrivate());
            String plainPayloadJson = GSON.toJson(response.getPayload());
            AESUtil.Sealed sealed = AESUtil.encrypt(aesKey, plainPayloadJson);
            Map<String, Object> hybridPayload = new HashMap<>();
            hybridPayload.put(MessageProtocol.KEY_ENCRYPTED_PAYLOAD, sealed.cipherTextBase64());
            hybridPayload.put(MessageProtocol.KEY_AES_IV, sealed.ivBase64());
            hybridPayload.put("hybrid", true);
            return new Response(true, response.getMessage(), hybridPayload, null);
        } catch (Exception e) {
            LOG.warn("[HYBRID] Impossible de chiffrer la reponse: {}", e.getMessage(), e);
            return Response.error("Erreur de chiffrement de la reponse hybride.");
        }
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
            LOG.debug("[UDP] userId={} not connected - notification skipped.", userId);
            return;
        }
        udpNotificationService.sendNotification(targetAddress, type, message, orderId);
    }

    private void logActionSuccess(String action, Integer userId) {
        logActionSuccess(action, userId, null);
    }

    private void logActionSuccess(String action, Integer userId, String details) {
        if (details == null || details.isBlank()) {
            LOG.info("[AUDIT] action={} status=SUCCESS userId={}", action, userId);
            return;
        }
        LOG.info("[AUDIT] action={} status=SUCCESS userId={} details={}", action, userId, details);
    }

    private void logActionError(String action, Integer userId, String details) {
        if (details == null || details.isBlank()) {
            LOG.warn("[AUDIT] action={} status=ERROR userId={}", action, userId);
            return;
        }
        LOG.warn("[AUDIT] action={} status=ERROR userId={} details={}", action, userId, details);
    }

    private Response validateReplayProtectedPayment(Request req, Integer userId, Integer orderId) {
        String requestId = req.getRequestId();
        Long timestamp = req.getTimestamp();
        long now = System.currentTimeMillis();

        if (requestId == null || requestId.isBlank()) {
            logActionError("PAYMENT_REPLAY_CHECK", userId, "Missing requestId");
            return Response.error("Requete de paiement invalide: requestId manquant.");
        }
        if (timestamp == null || timestamp <= 0L) {
            logActionError("PAYMENT_REPLAY_CHECK", userId, "Missing timestamp");
            return Response.error("Requete de paiement invalide: timestamp manquant.");
        }

        cleanupSeenPaymentRequests(now);

        long drift = Math.abs(now - timestamp);
        if (drift > PAYMENT_REPLAY_WINDOW_MS) {
            logActionError("PAYMENT_REPLAY_CHECK", userId,
                    "Expired payment request requestId=" + requestId + " orderId=" + orderId + " driftMs=" + drift);
            return Response.error("Requete de paiement expiree ou horodatage invalide.");
        }

        String replayKey = (userId == null ? 0 : userId) + ":" + requestId;
        Long previous = SEEN_PAYMENT_REQUESTS.putIfAbsent(replayKey, now);
        if (previous != null) {
            logActionError("PAYMENT_REPLAY_CHECK", userId,
                    "Replay detected requestId=" + requestId + " orderId=" + orderId);
            return Response.error("Requete de paiement dupliquee detectee.");
        }

        logActionSuccess("PAYMENT_REPLAY_CHECK", userId,
                "Accepted requestId=" + requestId + " orderId=" + orderId);
        return null;
    }

    private Response validateReplayProtectedPlaceOrder(Request req, Integer userId) {
        String requestId = req.getRequestId();
        Long timestamp = req.getTimestamp();
        long now = System.currentTimeMillis();

        if (requestId == null || requestId.isBlank()) {
            logActionError("PLACE_ORDER_REPLAY_CHECK", userId, "Missing requestId");
            return Response.error("Requete de commande invalide: requestId manquant.");
        }
        if (timestamp == null || timestamp <= 0L) {
            logActionError("PLACE_ORDER_REPLAY_CHECK", userId, "Missing timestamp");
            return Response.error("Requete de commande invalide: timestamp manquant.");
        }

        cleanupSeenRequests(SEEN_ORDER_REQUESTS, now, ORDER_REPLAY_WINDOW_MS);

        long drift = Math.abs(now - timestamp);
        if (drift > ORDER_REPLAY_WINDOW_MS) {
            logActionError("PLACE_ORDER_REPLAY_CHECK", userId,
                    "Expired place-order request requestId=" + requestId + " driftMs=" + drift);
            return Response.error("Requete de commande expiree ou horodatage invalide.");
        }

        String replayKey = (userId == null ? 0 : userId) + ":" + requestId;
        Long previous = SEEN_ORDER_REQUESTS.putIfAbsent(replayKey, now);
        if (previous != null) {
            logActionError("PLACE_ORDER_REPLAY_CHECK", userId,
                    "Replay detected requestId=" + requestId);
            return Response.error("Requete de commande dupliquee detectee.");
        }

        logActionSuccess("PLACE_ORDER_REPLAY_CHECK", userId,
                "Accepted requestId=" + requestId);
        return null;
    }

    private void cleanupSeenPaymentRequests(long now) {
        cleanupSeenRequests(SEEN_PAYMENT_REQUESTS, now, PAYMENT_REPLAY_WINDOW_MS);
    }

    private Response validateOperationNonce(Request req, int userId, String expectedOperation, String expectedScope) {
        String nonce = req.getOperationNonce();
        if (nonce == null || nonce.isBlank()) {
            logActionError("OPERATION_NONCE", userId, "Missing operationNonce for " + expectedOperation);
            return Response.error("Nonce serveur manquant pour cette operation.");
        }

        long now = System.currentTimeMillis();
        cleanupExpiredOperationNonces(now);

        OperationNonce stored = ISSUED_OPERATION_NONCES.get(nonce);
        if (stored == null) {
            logActionError("OPERATION_NONCE", userId,
                    "Unknown or expired nonce for operation=" + expectedOperation);
            return Response.error("Nonce serveur invalide ou expire.");
        }

        if (stored.userId != userId
                || !expectedOperation.equals(stored.action)
                || !matchesScope(stored.scope, expectedScope)
                || stored.expiresAt < now) {
            logActionError("OPERATION_NONCE", userId,
                    "Nonce scope mismatch for operation=" + expectedOperation);
            return Response.error("Nonce serveur invalide ou expire.");
        }

        if (!ISSUED_OPERATION_NONCES.remove(nonce, stored)) {
            logActionError("OPERATION_NONCE", userId,
                    "Replay detected for operation=" + expectedOperation);
            return Response.error("Nonce serveur deja utilise.");
        }

        logActionSuccess("OPERATION_NONCE", userId,
                "Consumed nonce for operation=" + expectedOperation);
        return null;
    }

    private boolean matchesScope(String storedScope, String expectedScope) {
        if (storedScope == null || storedScope.isBlank()) {
            return expectedScope == null || expectedScope.isBlank();
        }
        return storedScope.equals(expectedScope);
    }

    private void cleanupExpiredOperationNonces(long now) {
        ISSUED_OPERATION_NONCES.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expiresAt < now);
    }

    private void cleanupSeenRequests(ConcurrentMap<String, Long> requests, long now, long windowMs) {
        for (Map.Entry<String, Long> entry : requests.entrySet()) {
            if (now - entry.getValue() > windowMs) {
                requests.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    // ── AUTH handlers ─────────────────────────────────────────────────────────

    /**
     * LOGIN — payload: {@code email}, {@code password}.
     *
     * <p>Authenticates via  (no
     * password logic here), creates a session, and returns the token + role.
     */
    private Response handleLogin(Request req) {
        String email    = getPayloadString(req, "email");
        String password = getPayloadString(req, "password");
        String captchaId = getPayloadString(req, "captchaId");
        String captchaAnswer = getPayloadString(req, "captchaAnswer");

        if (email == null || email.isBlank()) {
            return Response.error("Le champ 'email' est requis.");
        }
        if (password == null || password.isBlank()) {
            return Response.error("Le champ 'password' est requis.");
        }

        long nowMs = System.currentTimeMillis();
        long blockedForMs = getBlockedRemainingMs(email, nowMs);
        if (blockedForMs > 0) {
            long seconds = Math.max(1L, blockedForMs / 1000L);
            return Response.error("Trop de tentatives. Réessayez dans " + seconds + "s.");
        }

        boolean captchaRequired = isRecaptchaRequired(email, nowMs);
        if (captchaRequired) {
            LoginCaptchaService.ValidationResult captchaResult =
                    loginCaptchaService.validate(captchaId, captchaAnswer, getClientIpAddress());
            if (!captchaResult.isSuccess()) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("captchaRequired", true);
                return Response.error("CAPTCHA invalide ou manquant. " + captchaResult.getMessage(), payload);
            }
        }

        try {
            AuthService.LoginResult loginResult = authService.login(email, password, getClientIpAddress());
            if (loginResult.getStatus() == AuthService.LoginStatus.LOGIN_IP_VERIFICATION_REQUIRED) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("email", email);
                payload.put("verificationType", "login_ip");
                payload.put("message", "Vérification requise pour valider votre connexion.");
                return Response.error("Un code de vérification a été envoyé à votre adresse email.", payload);
            }

            User user = loginResult.getUser();

            String clientIp = getClientIpAddress();
            Session session = sessionManager.createSession(user);

            Map<String, Object> payload = new HashMap<>();
            payload.put("userId",   user.getUserId());
            payload.put("username", user.getUsername());
            payload.put("email",    user.getEmail());
            payload.put("role",     user.getRole().name());
            payload.put("emailVerified", user.isEmailVerified());

            // token is sent as the top-level Response.token field
            connectionToken = session.getToken();
            // Lie la session a l'IP courante pour permettre la detection de changements d'IP
            sessionManager.bindSessionIpIfAbsent(connectionToken, clientIp);
            // Register client IP so UDP notifications can reach this user
            connectedUserId = user.getUserId();
            ClientRegistry.getInstance().register(user.getUserId(), clientAddress);
            Response r = new Response(true, "LOGIN_SUCCESS", payload, session.getToken());
            logActionSuccess("LOGIN", user.getUserId());
            clearLoginAttempts(email);
            return r;

        } catch (IllegalArgumentException e) {
            registerFailedLogin(email, nowMs);
            logActionError("LOGIN", null, "Invalid credentials from IP: " + clientAddress);
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("[LOGIN] Unexpected error: {}", e.getMessage(), e);
            return Response.error("Erreur serveur lors de la connexion.");
        }
    }

    private Response handleGetLoginCaptcha() {
        LoginCaptchaService.CaptchaChallenge challenge = loginCaptchaService.issueChallenge(getClientIpAddress());
        Map<String, Object> payload = new HashMap<>();
        payload.put("captchaId", challenge.challengeId());
        payload.put("captchaText", challenge.captchaText());
        payload.put("expiresInSeconds", challenge.expiresInSeconds());
        return Response.ok("LOGIN_CAPTCHA_READY", payload);
    }

    /**
     * REGISTER — payload: {@code username}, {@code email}, {@code password}.
     *
     * <p>Registers via {@link AuthService#register(String, String, String, String)}
     * and sends an email verification code. No session is created yet.
     */
    private Response handleGetLoginSecurityState(Request req) {
        String email = getPayloadString(req, "email");
        long nowMs = System.currentTimeMillis();

        Map<String, Object> payload = new HashMap<>();
        long ipBlockedUntilMs = userDAO.getIpBlockedUntilMs(getClientIpAddress());
        long ipBlockedRemainingMs = Math.max(0L, ipBlockedUntilMs - nowMs);
        payload.put("ipBlocked", ipBlockedRemainingMs > 0L);
        payload.put("ipBlockedSecondsRemaining", ipBlockedRemainingMs > 0L ? Math.max(1L, ipBlockedRemainingMs / 1000L) : 0L);

        if (email == null || email.isBlank()) {
            payload.put("captchaRequired", false);
            payload.put("lockoutActive", false);
            payload.put("lockoutSecondsRemaining", 0L);
            payload.put("failures", 0);
            return Response.ok("LOGIN_SECURITY_STATE", payload);
        }

        UserDAO.LoginSecurityState state = userDAO.getLoginSecurityState(email.trim(), getClientIpAddress(), nowMs);
        long blockedRemainingMs = Math.max(0L, state.getBlockedUntilMs() - nowMs);
        payload.put("captchaRequired", state.getFailures() >= 3);
        payload.put("lockoutActive", blockedRemainingMs > 0L);
        payload.put("lockoutSecondsRemaining", blockedRemainingMs > 0L ? Math.max(1L, blockedRemainingMs / 1000L) : 0L);
        payload.put("failures", state.getFailures());
        return Response.ok("LOGIN_SECURITY_STATE", payload);
    }

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
            User user = authService.register(username, email, password, getClientIpAddress());

            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", user.getUserId());
            payload.put("username", user.getUsername());
            payload.put("email", user.getEmail());
            payload.put("role", Session.Role.CLIENT.name());
            payload.put("emailVerified", false);

            Response r = Response.ok("REGISTER_SUCCESS", payload);
            logActionSuccess("REGISTER", user.getUserId());
            return r;

        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("[REGISTER] Unexpected error: {}", e.getMessage(), e);
            return Response.error("Erreur serveur lors de l'inscription.");
        }
    }

    private Response handleVerifyEmail(Request req) {
        String email = getPayloadString(req, "email");
        String code = getPayloadString(req, "code");

        if (email == null || email.isBlank()) {
            return Response.error("Le champ 'email' est requis.");
        }
        if (code == null || code.isBlank()) {
            return Response.error("Le champ 'code' est requis.");
        }

        try {
            User user = authService.verifyEmail(email, code);
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", user.getUserId());
            payload.put("role", user.getRole().name());
            payload.put("email", user.getEmail());
            payload.put("emailVerified", true);
            logActionSuccess("VERIFY_EMAIL", user.getUserId());
            return Response.ok("EMAIL_VERIFIED", payload);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("[VERIFY_EMAIL] Unexpected error: {}", e.getMessage(), e);
            return Response.error("Erreur serveur lors de la verification de l'email.");
        }
    }

    private Response handleVerifyLoginIp(Request req) {
        String email = getPayloadString(req, "email");
        String code = getPayloadString(req, "code");

        if (email == null || email.isBlank()) {
            return Response.error("Le champ 'email' est requis.");
        }
        if (code == null || code.isBlank()) {
            return Response.error("Le champ 'code' est requis.");
        }

        try {
            User user = authService.verifyLoginIp(email, code, getClientIpAddress());
            Session session = sessionManager.createSession(user);

            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", user.getUserId());
            payload.put("username", user.getUsername());
            payload.put("email", user.getEmail());
            payload.put("role", session.getRole().name());
            payload.put("emailVerified", user.isEmailVerified());

            connectionToken = session.getToken();
            connectedUserId = user.getUserId();
            ClientRegistry.getInstance().register(user.getUserId(), clientAddress);
            clearLoginAttempts(email);
            return new Response(true, "LOGIN_IP_VERIFIED", payload, session.getToken());
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("[VERIFY_LOGIN_IP] Unexpected error: {}", e.getMessage(), e);
            return Response.error("Erreur serveur lors de la verification de connexion.");
        }
    }

    private Response handleResendLoginIpVerification(Request req) {
        String email = getPayloadString(req, "email");
        if (email == null || email.isBlank()) {
            return Response.error("Le champ 'email' est requis.");
        }

        try {
            authService.resendLoginIpVerificationCode(email, getClientIpAddress());
            return Response.ok("LOGIN_IP_VERIFICATION_RESENT", null);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("[RESEND_LOGIN_IP] Unexpected error: {}", e.getMessage(), e);
            return Response.error("Erreur serveur lors du renvoi du code de verification.");
        }
    }

    private Response handleForgotPassword(Request req) {
        String email = getPayloadString(req, "email");
        if (email == null || email.isBlank()) {
            return Response.error("Le champ 'email' est requis.");
        }

        try {
            authService.forgotPassword(email);
            logActionSuccess("FORGOT_PASSWORD", null);
            return Response.ok("PASSWORD_RESET_EMAIL_SENT", null);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("[FORGOT_PASSWORD] Unexpected error: {}", e.getMessage(), e);
            return Response.error("Erreur serveur lors de l'envoi de l'email de reinitialisation.");
        }
    }

    private Response handleResetPassword(Request req) {
        String token = getPayloadString(req, "token");
        String newPassword = getPayloadString(req, "newPassword");
        if (token == null || token.isBlank()) {
            return Response.error("Le champ 'token' est requis.");
        }
        if (newPassword == null || newPassword.isBlank()) {
            return Response.error("Le champ 'newPassword' est requis.");
        }

        try {
            authService.resetPassword(token, newPassword);
            logActionSuccess("RESET_PASSWORD", null);
            return Response.ok("PASSWORD_RESET_SUCCESS", null);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("[RESET_PASSWORD] Unexpected error: {}", e.getMessage(), e);
            return Response.error("Erreur serveur lors de la reinitialisation du mot de passe.");
        }
    }

    private Response handleResendVerificationEmail(Request req) {
        String email = getPayloadString(req, "email");

        if (email == null || email.isBlank()) {
            return Response.error("Le champ 'email' est requis.");
        }

        try {
            authService.resendVerificationCode(email);
            logActionSuccess("RESEND_VERIFICATION_EMAIL", null);
            return Response.ok("VERIFICATION_EMAIL_RESENT", null);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("[RESEND_VERIFICATION_EMAIL] Unexpected error: {}", e.getMessage(), e);
            return Response.error("Erreur serveur lors du renvoi du code.");
        }
    }

    // ── Admin Auth (Challenge Response) handlers ──────────────────────────────
    
    private Response handleAdminChallengeRequest(Request req) {
        String username = getPayloadString(req, "username");
        if (username == null || username.isBlank()) {
            return Response.error("Le username admin est requis.");
        }

        try {
            String challenge = adminAuthService.generateChallenge(username);
            Map<String, Object> payload = new HashMap<>();
            payload.put("challenge", challenge);
            return Response.ok("CHALLENGE_GENERATED", payload);
        } catch (IllegalArgumentException e) {
            SecurityAuditLogger.logAdminAccessFailure(username, getClientIpAddress(), "UNKNOWN_ADMIN_USERNAME");
            return Response.error(e.getMessage());
        } catch (Exception e) {
            LOG.warn("[ADMIN_AUTH] Erreur generation defi: ", e);
            return Response.error("Erreur serveur lors de la generation du defi");
        }
    }

    private Response handleAdminChallengeVerify(Request req) {
        String username = getPayloadString(req, "username");
        String signature = getPayloadString(req, "signature");

        if (username == null || username.isBlank() || signature == null || signature.isBlank()) {
            return Response.error("username et signature sont requis.");
        }

        try {
            // Optionnel : Blocage IP pour Admin
            String clientIp = getClientIpAddress();
            if (!IpUtils.isPrivateIp(clientIp)) {
                SecurityAuditLogger.logAdminExternalIpBlocked("AdminUser " + username, clientIp);
                return Response.error("Acces admin refuse depuis une adresse IP externe.");
            }

            User admin = adminAuthService.verifyChallenge(username, signature);
            Session session = sessionManager.createPrivilegedSession(admin);
            
            SecurityAuditLogger.logAdminAccessSuccess(username, clientIp);

            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", admin.getUserId());
            payload.put("username", admin.getUsername());
            payload.put("email", admin.getEmail());
            payload.put("role", session.getRole().name());

            connectionToken = session.getToken();
            connectedUserId = admin.getUserId();
            sessionManager.bindSessionIpIfAbsent(connectionToken, clientIp);
            
            return new Response(true, "ADMIN_LOGIN_SUCCESS", payload, session.getToken());
        } catch (IllegalArgumentException e) {
            SecurityAuditLogger.logAdminAccessFailure(username, getClientIpAddress(), "INVALID_RSA_SIGNATURE");
            LOG.warn("[ADMIN_AUTH] Failed verify: " + e.getMessage());
            return Response.error(e.getMessage());
        } catch (Exception e) {
            LOG.warn("[ADMIN_AUTH] Erreur verification defi: ", e);
            return Response.error("Erreur (serveur) " + e.getMessage());
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
        Session session = sessionManager.getSession(req.getToken()).orElse(null);
        boolean adminCatalog = session != null && isPrivilegedRole(session.getRole());
        List<?> products = productService.getProducts(categoryId, adminCatalog);
        Response r = Response.ok(products);
        if (session != null && session.getUserId() != null) {
            logActionSuccess("GET_PRODUCTS", session.getUserId());
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
        Session session = req.getToken() != null ? sessionManager.getSession(req.getToken()).orElse(null) : null;
        boolean admin = session != null && isPrivilegedRole(session.getRole());
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

    private Response handleGetTopSellingProducts(Request req) {
        Integer limit = req.getPayloadInt("limit");
        int resolvedLimit = limit != null && limit > 0 ? limit : 4;
        return Response.ok(productService.getTopSellingProducts(resolvedLimit));
    }

    private Response handleGetRecentProducts(Request req) {
        Integer limit = req.getPayloadInt("limit");
        int resolvedLimit = limit != null && limit > 0 ? limit : 4;
        return Response.ok(productService.getRecentProducts(resolvedLimit));
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
            LOG.warn("[CART] Unexpected error: {}", e.getMessage(), e);
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
            logActionSuccess("ADD_TO_CART", userId);
            return r;
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("[CART] Unexpected error: {}", e.getMessage(), e);
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
            LOG.warn("[CART] Unexpected error: {}", e.getMessage(), e);
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
            LOG.warn("[CART] Unexpected error: {}", e.getMessage(), e);
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
            LOG.warn("[CART] Unexpected error: {}", e.getMessage(), e);
            return Response.error("Erreur serveur lors du vidage du panier.");
        }
    }

    private Response handleGetOperationNonce(Request req) {
        try {
            int userId = sessionManager.getUserFromToken(req.getToken())
                    .map(User::getUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));

            String operation = payloadValueAsString(req, "operation");
            if (operation == null || operation.isBlank()) {
                return Response.error("operation manquant.");
            }

            String normalizedOperation = operation.trim();
            if (!MessageProtocol.ACTION_PLACE_ORDER.equals(normalizedOperation)
                    && !MessageProtocol.ACTION_PAYMENT.equals(normalizedOperation)) {
                return Response.error("Operation non supportee pour la generation de nonce.");
            }

            String scope = payloadValueAsString(req, "scope");
            if (scope == null || scope.isBlank()) {
                scope = firstNonBlank(
                        payloadValueAsString(req, "order_id"),
                        payloadValueAsString(req, "orderId"));
            }

            long now = System.currentTimeMillis();
            String nonce = UUID.randomUUID().toString().replace("-", "");
            ISSUED_OPERATION_NONCES.put(nonce,
                    new OperationNonce(userId, normalizedOperation, scope, now + OPERATION_NONCE_WINDOW_MS));
            cleanupExpiredOperationNonces(now);

            Map<String, Object> payload = new HashMap<>();
            payload.put("nonce", nonce);
            payload.put("operation", normalizedOperation);
            payload.put("scope", scope);
            payload.put("expiresAt", now + OPERATION_NONCE_WINDOW_MS);

            logActionSuccess("GET_OPERATION_NONCE", userId,
                    "Issued nonce for operation=" + normalizedOperation + " scope=" + scope);
            return Response.ok("OPERATION_NONCE_READY", payload);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("[NONCE] Unexpected error: {}", e.getMessage(), e);
            return Response.error("Erreur serveur lors de la generation du nonce.");
        }
    }

    // ── ORDER handlers ────────────────────────────────────────────────────────

    private Response handlePlaceOrder(Request req) {
        try {
            int userId = sessionManager.getUserFromToken(req.getToken())
                    .map(User::getUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
            Response nonceValidation = validateOperationNonce(req, userId, MessageProtocol.ACTION_PLACE_ORDER, null);
            if (nonceValidation != null) {
                return nonceValidation;
            }
            Response replayValidation = validateReplayProtectedPlaceOrder(req, userId);
            if (replayValidation != null) {
                return replayValidation;
            }
            com.chrionline.model.Order order = orderService.placeOrderFromCart(userId);
            sendUdpNotification(
                    "ORDER_VALIDATED",
                    "Commande " + order.getOrderId() + " validee.",
                    String.valueOf(order.getOrderId()));
            Response r = Response.ok(order);
            logActionSuccess("PLACE_ORDER", userId);
            logActionSuccess("CHECKOUT", userId);
            return r;
        } catch (IllegalArgumentException e) {
            int uid = sessionManager.getUserFromToken(req.getToken()).map(User::getUserId).orElse(0);
            Integer u = uid > 0 ? uid : null;
            logActionError("PLACE_ORDER", u, e.getMessage());
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("[ORDER] Unexpected error: {}", e.getMessage(), e);
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

            Response nonceValidation = validateOperationNonce(req, userId, MessageProtocol.ACTION_PAYMENT, String.valueOf(orderId));
            if (nonceValidation != null) {
                return nonceValidation;
            }

            Response replayValidation = validateReplayProtectedPayment(req, userId, orderId);
            if (replayValidation != null) {
                return replayValidation;
            }

            Map<String, Object> result = paymentService.processSimulatedCardPayment(
                    userId, orderId, cardNumber, expiry, cvv);

            if (Boolean.TRUE.equals(result.get("success"))) {
                sendUdpNotification(
                        "PAYMENT_CONFIRMED",
                        "Paiement confirme pour la commande " + orderId + ".",
                        String.valueOf(orderId));
                Response r = Response.ok("PAYMENT_OK", result);
                logActionSuccess("PAYMENT", userId);
                return r;
            }
            String msg = result.get("message") != null ? String.valueOf(result.get("message")) : "Paiement refusé.";
            logActionError("PAYMENT", userId, "Payment failed: " + msg);
            return Response.error(msg, result);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("[PAYMENT] Unexpected error: {}", e.getMessage(), e);
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
            LOG.warn("[ORDER] Unexpected error: {}", e.getMessage(), e);
            return Response.error("Erreur serveur lors du chargement des commandes.");
        }
    }

    private Response handleUpdateOrderStatus(Request req) {
        try {
            Session session = sessionManager.getSession(req.getToken())
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
            if (!isPrivilegedRole(session.getRole())) {
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
            logActionSuccess("UPDATE_ORDER_STATUS", session.getUserId() != null ? session.getUserId() : 0, "new status: " + status);
            return r;
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("[ORDER] Unexpected error: {}", e.getMessage(), e);
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
            LOG.warn("[ORDER_DETAILS] Unexpected error: {}", e.getMessage(), e);
            return Response.error("Erreur serveur lors du chargement de la commande.");
        }
    }

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
            LOG.warn("[PROFILE] Unexpected error: {}", e.getMessage(), e);
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
            LOG.warn("[PASSWORD] Unexpected error: {}", e.getMessage(), e);
            return Response.error("Erreur serveur lors du changement de mot de passe.");
        }
    }

    private Response handleActivateAdminAccess(Request req) {
        try {
            User user = sessionManager.getUserFromToken(req.getToken())
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
            if (user.getRole() != User.Role.ADMIN_PENDING) {
                return Response.error("Votre compte n'est pas en attente d'activation admin.");
            }

            String publicKey = getPayloadString(req, "public_key");
            if (publicKey == null || publicKey.isBlank()) {
                return Response.error("La cle publique RSA est requise.");
            }

            Map<String, Object> updated = adminService.changeUserRole(user.getUserId(), user.getUserId(), User.Role.ADMIN, publicKey);
            return Response.ok("ADMIN_ACCESS_ACTIVATED", updated);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("[ADMIN_ACTIVATION] Unexpected error: {}", e.getMessage(), e);
            return Response.error("Erreur serveur lors de l'activation admin.");
        }
    }

    private Response handleAdmin(Request req) {
        try {
            Session session = sessionManager.getSession(req.getToken())
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
            if (!isPrivilegedRole(session.getRole())) {
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
                    boolean available = adminService.toggleProductAvailability(productId);
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("available", available);
                    return Response.ok(available ? "ACTIVATED" : "DEACTIVATED", payload);
                }
                case MessageProtocol.ACTION_ADMIN_LIST_USERS: {
                    return Response.ok(adminService.listUsers());
                }
                case MessageProtocol.ACTION_ADMIN_UPDATE_USER_ROLE: {
                    if (!isSuperAdminRole(session.getRole())) {
                        return Response.error("AccÃ¨s refusÃ© (SUPER_ADMIN uniquement).");
                    }
                    Integer userId = req.getPayloadInt("user_id");
                    String roleValue = getPayloadString(req, "role");
                    String publicKey = getPayloadString(req, "public_key");
                    if (userId == null || roleValue == null || roleValue.isBlank()) {
                        return Response.error("Missing user_id or role");
                    }

                    User.Role targetRole = User.Role.fromDbValue(roleValue);
                    Map<String, Object> updated = adminService.changeUserRole(session.getUserId(), userId, targetRole, publicKey);
                    sessionManager.invalidateSessionsForUser(userId);
                    return Response.ok("USER_ROLE_UPDATED", updated);
                }
                case MessageProtocol.ACTION_ADMIN_SET_USER_SUSPENDED: {
                    Integer userId = req.getPayloadInt("user_id");
                    Object suspended = req.getPayload() != null ? req.getPayload().get("suspended") : null;
                    boolean isSuspended = suspended instanceof Boolean ? (Boolean) suspended : Boolean.parseBoolean(String.valueOf(suspended));
                    if (userId == null) return Response.error("Missing user_id");
                    if (userId.equals(session.getUserId())) return Response.error("Impossible de suspendre votre propre compte.");
                    User targetUser = userDAO.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
                    if (targetUser.getRole() == User.Role.SUPER_ADMIN && !isSuperAdminRole(session.getRole())) {
                        return Response.error("Seul un super admin peut suspendre un super admin.");
                    }
                    adminService.setUserSuspended(userId, isSuspended);
                    if (isSuspended) {
                        sessionManager.invalidateSessionsForUser(userId);
                    }
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
            LOG.warn("[ADMIN] Unexpected error: {}", e.getMessage(), e);
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

    private Response invalidInput(String detail) {
        return Response.error("INVALID_INPUT: " + detail);
    }

    private void validateRequestInputForAction(String action, Request req) {
        switch (action) {
            case MessageProtocol.ACTION_LOGIN:
            case MessageProtocol.ACTION_GET_LOGIN_SECURITY_STATE:
            case MessageProtocol.ACTION_VERIFY_EMAIL:
            case MessageProtocol.ACTION_VERIFY_LOGIN_IP:
            case MessageProtocol.ACTION_RESEND_LOGIN_IP_VERIFICATION:
            case MessageProtocol.ACTION_FORGOT_PASSWORD:
            case MessageProtocol.ACTION_RESEND_VERIFICATION_EMAIL:
                validateEmailPayloadIfPresent(req, "email");
                break;
            case MessageProtocol.ACTION_RESET_PASSWORD:
                sanitizeOptionalPayloadString(req, "token");
                sanitizeOptionalPayloadString(req, "newPassword");
                break;
            case MessageProtocol.ACTION_REGISTER:
            case MessageProtocol.ACTION_UPDATE_PROFILE:
                validateEmailPayloadIfPresent(req, "email");
                validateUsernamePayloadIfPresent(req, "username");
                break;
            case MessageProtocol.ACTION_GET_PRODUCT:
            case MessageProtocol.ACTION_ADMIN_DELETE_PRODUCT:
                InputValidator.validateProductId(req.getPayloadInt("product_id"));
                break;
            case MessageProtocol.ACTION_GET_PRODUCTS:
            case MessageProtocol.ACTION_GET_CATEGORIES:
                validateOptionalPositiveInt(req.getPayloadInt("category_id"), "category_id", 1, 1_000_000_000);
                break;
            case MessageProtocol.ACTION_GET_TOP_SELLING_PRODUCTS:
            case MessageProtocol.ACTION_GET_RECENT_PRODUCTS:
                validateOptionalPositiveInt(req.getPayloadInt("limit"), "limit", 1, 100);
                break;
            case MessageProtocol.ACTION_ADD_TO_CART:
                InputValidator.validateProductId(req.getPayloadInt("product_id"));
                InputValidator.validateQuantity(req.getPayloadInt("quantity"));
                break;
            case MessageProtocol.ACTION_UPDATE_CART_ITEM:
                InputValidator.validatePositiveInt(req.getPayloadInt("cart_item_id"), "cart_item_id", 1, 1_000_000_000);
                InputValidator.validateQuantity(req.getPayloadInt("quantity"));
                break;
            case MessageProtocol.ACTION_REMOVE_FROM_CART:
                InputValidator.validatePositiveInt(req.getPayloadInt("cart_item_id"), "cart_item_id", 1, 1_000_000_000);
                break;
            case MessageProtocol.ACTION_PAYMENT: {
                Integer orderId = firstNonBlankInt(req.getPayloadInt("order_id"), req.getPayloadInt("orderId"));
                InputValidator.validatePositiveInt(orderId, "order_id", 1, 1_000_000_000);
                InputValidator.sanitize(firstNonBlank(getPayloadString(req, "card_number"), getPayloadString(req, "cardNumber")), "card_number");
                InputValidator.sanitize(firstNonBlank(getPayloadString(req, "expiry"), getPayloadString(req, "expiryMmYy")), "expiry");
                InputValidator.sanitize(payloadValueAsString(req, "cvv"), "cvv");
                break;
            }
            case MessageProtocol.ACTION_PLACE_ORDER: {
                String rawOrderId = firstNonBlank(
                        payloadValueAsString(req, "order_id"),
                        payloadValueAsString(req, "orderId"));
                if (rawOrderId != null && !rawOrderId.isBlank()) {
                    InputValidator.validateOrderId(rawOrderId);
                }
                break;
            }
            case MessageProtocol.ACTION_GET_CART:
            case MessageProtocol.ACTION_GET_ORDERS:
            case MessageProtocol.ACTION_GET_LOGIN_CAPTCHA:
                // No required payload fields for these read actions.
                break;
            case MessageProtocol.ACTION_UPDATE_ORDER_STATUS: {
                Integer orderId = req.getPayloadInt("order_id");
                InputValidator.validatePositiveInt(orderId, "order_id", 1, 1_000_000_000);
                String status = getPayloadString(req, "status");
                String safeStatus = InputValidator.sanitize(status, "status");
                if (!"PENDING".equals(safeStatus)
                        && !"VALIDATED".equals(safeStatus)
                        && !"SHIPPED".equals(safeStatus)
                        && !"DELIVERED".equals(safeStatus)
                        && !"CANCELLED".equals(safeStatus)) {
                    throw new ValidationException("status is invalid.");
                }
                break;
            }
            case MessageProtocol.ACTION_GET_ORDER_DETAILS: {
                Integer orderId = req.getPayloadInt("order_id");
                if (orderId != null) {
                    InputValidator.validateOrderId(String.valueOf(orderId));
                }
                break;
            }
            case MessageProtocol.ACTION_GET_OPERATION_NONCE:
                InputValidator.sanitize(payloadValueAsString(req, "operation"), "operation");
                sanitizeOptionalPayloadString(req, "scope");
                sanitizeOptionalPayloadString(req, "order_id");
                sanitizeOptionalPayloadString(req, "orderId");
                break;
            case MessageProtocol.ACTION_ADMIN_CREATE_PRODUCT:
            case MessageProtocol.ACTION_ADMIN_UPDATE_PRODUCT:
                validateOptionalPositiveInt(req.getPayloadInt("category_id"), "category_id", 1, 1_000_000_000);
                sanitizeOptionalPayloadString(req, "name");
                validateOptionalPrice(req.getPayload() == null ? null : req.getPayload().get("price"));
                validateOptionalPositiveInt(req.getPayloadInt("stock"), "stock", 0, 1_000_000);
                sanitizeOptionalPayloadString(req, "image_filename");
                break;
            case MessageProtocol.ACTION_ADMIN_ADD_CATEGORY:
            case MessageProtocol.ACTION_ADMIN_UPDATE_CATEGORY:
                sanitizeOptionalPayloadString(req, "name");
                break;
            case MessageProtocol.ACTION_ADMIN_DELETE_CATEGORY:
                validateOptionalPositiveInt(req.getPayloadInt("id"), "id", 1, 1_000_000_000);
                break;
            case MessageProtocol.ACTION_ADMIN_SET_USER_SUSPENDED:
                InputValidator.validatePositiveInt(req.getPayloadInt("user_id"), "user_id", 1, 1_000_000_000);
                break;
            case MessageProtocol.ACTION_ADMIN_UPDATE_USER_ROLE:
                InputValidator.validatePositiveInt(req.getPayloadInt("user_id"), "user_id", 1, 1_000_000_000);
                sanitizeOptionalPayloadString(req, "public_key");
                break;
            case MessageProtocol.ACTION_ACTIVATE_ADMIN_ACCESS:
                sanitizeOptionalPayloadString(req, "public_key");
                break;
            case MessageProtocol.ACTION_ADMIN_LIST_USERS:
            case MessageProtocol.ACTION_ADMIN_LIST_ORDERS:
            case MessageProtocol.ACTION_ADMIN_CHALLENGE_REQUEST:
            case MessageProtocol.ACTION_ADMIN_CHALLENGE_VERIFY:
            case MessageProtocol.ACTION_LOGOUT:
                // No payload validation required at this level.
                break;
            default:
                throw new ValidationException("Unsupported action: " + action);
        }
    }

    private void validateEmailPayloadIfPresent(Request req, String key) {
        String value = getPayloadString(req, key);
        if (value != null && !value.isBlank()) {
            InputValidator.validateEmail(value);
        }
    }

    private void validateUsernamePayloadIfPresent(Request req, String key) {
        String value = getPayloadString(req, key);
        if (value != null && !value.isBlank()) {
            InputValidator.validateUsername(value);
        }
    }

    private boolean isPrivilegedRole(Session.Role role) {
        return role != null && role.isPrivileged();
    }

    private boolean isSuperAdminRole(Session.Role role) {
        return role == Session.Role.SUPER_ADMIN;
    }

    private void sanitizeOptionalPayloadString(Request req, String key) {
        String value = getPayloadString(req, key);
        if (value != null && !value.isBlank()) {
            InputValidator.sanitize(value, key);
        }
    }

    private void validateOptionalPositiveInt(Integer value, String fieldName, int min, int max) {
        if (value != null) {
            InputValidator.validatePositiveInt(value, fieldName, min, max);
        }
    }

    private void validateOptionalPrice(Object value) {
        if (value instanceof Number number) {
            InputValidator.validatePrice(number);
            return;
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                InputValidator.validatePrice(Double.parseDouble(s.trim()));
            } catch (NumberFormatException e) {
                throw new ValidationException("price must be numeric.");
            }
        }
    }

    private String buildLoginKey(String email) {
        String ip = getClientIpAddress();
        return ip + "|" + email.toLowerCase();
    }

    private String getClientIpAddress() {
    return clientAddress != null ? clientAddress.getHostAddress() : "unknown";
//        return "85.12.45.67";
    }

    private long getBlockedRemainingMs(String email, long nowMs) {
        UserDAO.LoginSecurityState state = userDAO.getLoginSecurityState(email, getClientIpAddress(), nowMs);
        long ipBlockedRemaining = Math.max(0L, state.getIpBlockedUntilMs() - nowMs);
        if (ipBlockedRemaining > 0L) {
            return ipBlockedRemaining;
        }
        return Math.max(0L, state.getBlockedUntilMs() - nowMs);
    }

    private boolean isRecaptchaRequired(String email, long nowMs) {
        UserDAO.LoginSecurityState state = userDAO.getLoginSecurityState(email, getClientIpAddress(), nowMs);
        return state.getFailures() >= 3;
    }

    private void registerFailedLogin(String email, long nowMs) {
        UserDAO.LoginSecurityState state = userDAO.registerFailedLoginAttempt(
            email,
            getClientIpAddress(),
            nowMs,
            LOGIN_WINDOW_MS,
            MAX_LOGIN_ATTEMPTS
        );
        if (state.getFailures() == 3) {
            authService.notifyFailedLoginAlert(email, getClientIpAddress(), state.getFailures());
        }
    }

    private void clearLoginAttempts(String email) {
        userDAO.clearLoginSecurityState(email, getClientIpAddress());
    }

    private boolean isIpBlocked() {
        long now = System.currentTimeMillis();
        long blockedUntil = userDAO.getIpBlockedUntilMs(getClientIpAddress());
        return blockedUntil > now;
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
                    String originalName = InputValidator.validateFilename(getPayloadString(req, "image_filename"));
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
                    String filename = map.get("image_filename") == null
                            ? null
                            : InputValidator.validateFilename(String.valueOf(map.get("image_filename")));
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
