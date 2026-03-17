package com.chrionline.server;

import com.chrionline.dao.UserDAO;
import com.chrionline.service.AuthService;
import com.chrionline.service.ProductService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private static final Logger LOG = Logger.getLogger(Server.class.getName());

    public static void main(String[] args) {
        int port = PORT;
        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                LOG.warning("Port invalide '" + args[0] + "', utilisation de " + PORT);
            }
        }

        // ── Shared service instances — created ONCE, passed to every handler ──
        UserDAO        userDAO        = new UserDAO();
        SessionManager sessionManager = new SessionManager(userDAO);
        AuthService    authService    = new AuthService(userDAO);
        ProductService productService = new ProductService();

        // ── Startup tasks ─────────────────────────────────────────────────────
        LOG.info("[SERVER] Seeding default admin account if not present...");
        authService.seedAdminIfNotExists();

        LOG.info("[SERVER] Cleaning expired sessions...");
        sessionManager.cleanExpiredSessions();

        // ── Thread pool ───────────────────────────────────────────────────────
        int poolSize = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            LOG.info("Serveur TCP demarre sur le port " + port + " (pool=" + poolSize + " threads)");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(serverSocket, pool)));

            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    // Pass the SAME shared instances — no new service created per thread
                    pool.execute(new ClientHandler(clientSocket, authService, sessionManager, productService));
                } catch (IOException e) {
                    if (serverSocket.isClosed()) break;
                    LOG.log(Level.WARNING, "Erreur lors de accept()", e);
                }
            }

        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Impossible de demarrer le serveur", e);
        } finally {
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
}