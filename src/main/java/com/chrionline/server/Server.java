package com.chrionline.server;

import com.chrionline.dao.UserDAO;
import com.chrionline.security.RSAUtil;
import com.chrionline.security.KeyStoreUtil;
import com.chrionline.security.TlsSupport;
import com.chrionline.security.SecurityAuditLogger;
import com.chrionline.security.SecurityMonitor;
import com.chrionline.security.IpSpoofingDetector;
import com.chrionline.service.AuthService;
import com.chrionline.service.AdminAuthService;
import com.chrionline.service.AdminService;
import com.chrionline.service.CartService;
import com.chrionline.service.EnvFileLoader;
import com.chrionline.service.OrderService;
import com.chrionline.service.PaymentService;
import com.chrionline.service.ProductService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * TCP server entry-point for ChriOnline.
 *
 * <p>Lifecycle at startup:
 * <ol>
 *   <li>Create shared service singletons: {@link AuthService}, {@link SessionManager},
 *       {@link ProductService}.</li>
 *   <li>Seed default admin account if it does not already exist.</li>
 *   <li>Clean stale/expired sessions from the DB cache.</li>
 *   <li>Accept one TCP connection per client; dispatch each to a pooled {@link ClientHandler}.</li>
 * </ol>
 *
 * <p>The same {@code AuthService}, {@code SessionManager}, and {@code ProductService}
 * instances are shared across all {@link ClientHandler} threads — no new service
 * object is created per connection.
 */
public class Server {

    public static final int PORT = 8080;
    public static final int PAYMENT_TLS_PORT = 8443;
    private static final Logger LOG = LogManager.getLogger(Server.class);

    public static void main(String[] args) {
        EnvFileLoader.loadFromProjectRoot();

        int port = PORT;
        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                LOG.warn("Port invalide '{}', utilisation de {}", args[0], PORT);
            }
        }

        // ── Shared service instances — created ONCE, passed to every handler ──
        UserDAO        userDAO        = new UserDAO();
        userDAO.ensureEmailVerificationSchema();
        userDAO.ensureLoginIpVerificationSchema();
        userDAO.ensurePasswordResetSchema();
        userDAO.ensureLoginSecuritySchema();
        userDAO.ensureRoleAndPublicKeySchema();
        SessionManager sessionManager = new SessionManager(userDAO);
        AuthService    authService    = new AuthService(userDAO);
        ProductService productService = new ProductService();
        CartService    cartService    = new CartService();
        OrderService   orderService   = new OrderService();
        PaymentService paymentService = new PaymentService();
        AdminService   adminService   = new AdminService(userDAO);
        AdminAuthService adminAuthService = new AdminAuthService(userDAO);

        // ── Startup tasks ─────────────────────────────────────────────────────
        LOG.info("[SERVER] Cleaning expired sessions...");
        sessionManager.cleanExpiredSessions();

        UDPNotificationService udpNotificationService = new UDPNotificationService(UDPNotificationService.DEFAULT_PORT);
        SecurityMonitor securityMonitor = new SecurityMonitor();

        // ── Cle RSA serveur (Task 2 : RSA->AES) ───────────────────────────────
        // Generee au demarrage et envoyee au client via HELLO juste apres le handshake TLS.
        KeyPair sessionRsaKeyPair;
        try {
            sessionRsaKeyPair = KeyStoreUtil.getOrGenerateKeyPair();
            LOG.info("[CRYPTO] Cle RSA serveur (2048 bits) chargee depuis le KeyStore PKCS12 pour les sessions hybrides RSA->AES");
        } catch (Exception e) {
            LOG.error("[CRYPTO] Impossible de charger/generer la cle RSA serveur depuis le KeyStore", e);
            return;
        }

        // ── Thread pool ───────────────────────────────────────────────────────
        int poolSize = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);

        int paymentTlsPort = resolvePaymentTlsPort();
        try (ServerSocket serverSocket = new ServerSocket(port);
             ServerSocket paymentTlsSocket = TlsSupport.createServerSocket(paymentTlsPort)) {
            LOG.info("Serveur TCP applicatif demarre sur le port {} (pool={} threads)", port, poolSize);
            LOG.info("Serveur TLS paiement demarre sur le port {} ({})", paymentTlsPort, TlsSupport.describeServerConfiguration());

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                shutdown(serverSocket, pool);
                shutdown(paymentTlsSocket, null);
            }));

            Thread paymentTlsThread = new Thread(() -> acceptLoop(
                    paymentTlsSocket, pool, securityMonitor, userDAO, authService, sessionManager,
                    productService, cartService, orderService, paymentService, adminService,
                    udpNotificationService, adminAuthService, sessionRsaKeyPair, true),
                    "chrionline-payment-tls-acceptor");
            paymentTlsThread.setDaemon(true);
            paymentTlsThread.start();

            acceptLoop(serverSocket, pool, securityMonitor, userDAO, authService, sessionManager,
                    productService, cartService, orderService, paymentService, adminService,
                    udpNotificationService, adminAuthService, sessionRsaKeyPair, false);
        } catch (IOException | GeneralSecurityException e) {
            LOG.error("Impossible de demarrer le serveur", e);
        } finally {
            udpNotificationService.close();
            shutdown(null, pool);
        }
    }

    private static int resolvePaymentTlsPort() {
        String value = System.getProperty("CHRIONLINE_PAYMENT_TLS_PORT");
        if (value == null || value.isBlank()) {
            value = System.getenv("CHRIONLINE_PAYMENT_TLS_PORT");
        }
        if (value == null || value.isBlank()) {
            return PAYMENT_TLS_PORT;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOG.warn("Port TLS paiement invalide '{}', utilisation de {}", value, PAYMENT_TLS_PORT);
            return PAYMENT_TLS_PORT;
        }
    }

    private static void acceptLoop(ServerSocket serverSocket,
                                   ExecutorService pool,
                                   SecurityMonitor securityMonitor,
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
                                   KeyPair sessionRsaKeyPair,
                                   boolean paymentTlsOnly) {
        while (!serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                if (!securityMonitor.isSafeToAccept(clientSocket)) {
                    closeQuietly(clientSocket);
                    continue;
                }
                if (IpSpoofingDetector.isSuspicious(clientSocket)) {
                    closeQuietly(clientSocket);
                    continue;
                }
                clientSocket.setSoTimeout(paymentTlsOnly ? 120_000 : 300_000);

                if (clientSocket instanceof SSLSocket sslSocket) {
                    try {
                        sslSocket.startHandshake();
                    } catch (SSLHandshakeException e) {
                        String ip = safeRemoteIp(clientSocket);
                        SecurityAuditLogger.logTlsHandshakeFailure(ip, e.getMessage());
                        LOG.warn("[TLS] SSLHandshakeException from {}: {}", ip, e.getMessage(), e);
                        closeQuietly(clientSocket);
                        continue;
                    }
                }

                pool.execute(new ClientHandler(clientSocket, userDAO, authService, sessionManager,
                        productService, cartService, orderService, paymentService, adminService,
                        udpNotificationService, adminAuthService, sessionRsaKeyPair, paymentTlsOnly));
            } catch (SocketTimeoutException e) {
                if (serverSocket.isClosed()) break;
                LOG.debug("accept() timeout: {}", e.getMessage());
            } catch (SocketException e) {
                if (serverSocket.isClosed()) break;
                LOG.info("SocketException pendant accept(): {}", e.getMessage(), e);
            } catch (IOException e) {
                if (serverSocket.isClosed()) break;
                LOG.warn("Erreur lors de accept()", e);
            }
        }
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private static void shutdown(ServerSocket serverSocket, ExecutorService pool) {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }

        if (pool == null) return;

        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static String safeRemoteIp(Socket socket) {
        if (socket == null || socket.getInetAddress() == null) {
            return "?";
        }
        return socket.getInetAddress().getHostAddress();
    }
}
