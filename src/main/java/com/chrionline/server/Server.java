package com.chrionline.server;

import com.chrionline.dao.UserDAO;
import com.chrionline.security.RSAUtil;
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
            sessionRsaKeyPair = RSAUtil.generateKeyPair();
            LOG.info("[CRYPTO] Cle RSA serveur (2048 bits) generee pour les sessions hybrides RSA->AES");
        } catch (Exception e) {
            LOG.error("[CRYPTO] Impossible de generer la cle RSA serveur", e);
            return;
        }

        // ── Thread pool ───────────────────────────────────────────────────────
        int poolSize = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);

        try (ServerSocket serverSocket = TlsSupport.createServerSocket(port)) {
            LOG.info("Serveur TCP securise demarre sur le port {} (pool={} threads)", port, poolSize);
            LOG.info("{}", TlsSupport.describeServerConfiguration());

            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(serverSocket, pool)));

            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    // Protection SYN Flood / DoS simple
                    if (!securityMonitor.isSafeToAccept(clientSocket)) {
                        try {
                            clientSocket.close();
                        } catch (IOException ignored) {
                        }
                        continue;
                    }
                    // Verification basique de l'adresse IP source (IP spoofing / cas anormaux)
                    if (IpSpoofingDetector.isSuspicious(clientSocket)) {
                        try {
                            clientSocket.close();
                        } catch (IOException ignored) {
                        }
                        continue;
                    }
                    // Keep idle client connections alive longer to avoid frequent read timeouts.
                    clientSocket.setSoTimeout(300_000);

                    // Handshake TLS immédiat : rejette les clients invalides avant le pool (T2 robustesse).
                    if (clientSocket instanceof SSLSocket sslSocket) {
                        try {
                            sslSocket.startHandshake();
                        } catch (SSLHandshakeException e) {
                            String ip = safeRemoteIp(clientSocket);
                            SecurityAuditLogger.logTlsHandshakeFailure(ip, e.getMessage());
                            LOG.warn("[TLS] SSLHandshakeException from {}: {}", ip, e.getMessage(), e);
                            try {
                                clientSocket.close();
                            } catch (IOException ignored) {
                            }
                            continue;
                        }
                    }

                    pool.execute(new ClientHandler(clientSocket, userDAO, authService, sessionManager, productService, cartService, orderService, paymentService, adminService, udpNotificationService, adminAuthService, sessionRsaKeyPair));
                } catch (SocketTimeoutException e) {
                    if (serverSocket.isClosed()) break;
                    LOG.debug("accept() timeout (normal si SO_TIMEOUT configure): {}", e.getMessage());
                } catch (SocketException e) {
                    if (serverSocket.isClosed()) break;
                    LOG.info("SocketException pendant accept(): {}", e.getMessage(), e);
                } catch (IOException e) {
                    if (serverSocket.isClosed()) break;
                    LOG.warn("Erreur lors de accept()", e);
                }
            }

        } catch (IOException | GeneralSecurityException e) {
            LOG.error("Impossible de demarrer le serveur", e);
        } finally {
            udpNotificationService.close();
            shutdown(null, pool);
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
