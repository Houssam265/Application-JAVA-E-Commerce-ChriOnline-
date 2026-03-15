package com.chrionline.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server {
    public static final int PORT = 8081;
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

        int poolSize = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            LOG.info("Serveur TCP demarre sur le port " + port);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(serverSocket, pool)));

            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    pool.execute(new ClientHandler(clientSocket));
                } catch (IOException e) {
                    if (serverSocket.isClosed()) {
                        break;
                    }
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
            } catch (IOException ignored) {
                // ignore close error during shutdown
            }
        }

        if (pool == null) {
            return;
        }

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
