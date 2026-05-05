package com.chrionline.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Un moniteur de sécurité qui limite le nombre de connexions par adresse IP dans un intervalle de temps court
 * pour se protéger contre les attaques SYN Flood, DoS simples, ou brute force.
 */
public class SecurityMonitor {
    private static final Logger LOG = LogManager.getLogger(SecurityMonitor.class);

    // IP -> Moment de la dernière tentative en millisecondes
    private final ConcurrentHashMap<String, Long> lastConnectionTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> connectionWindowStart = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> connectionCountInWindow = new ConcurrentHashMap<>();

    // Limite de temps entre deux connexions depuis la même IP (en millisecondes)
    private static final long MIN_CONNECTION_INTERVAL_MS = 500;
    private static final long CONNECTION_WINDOW_MS = 60_000L;
    private static final int MAX_CONNECTIONS_PER_WINDOW = 100;

    /**
     * Vérifie s'il est sûr d'accepter ce client ou si c'est une connexion trop rapide (Flood potentiel).
     */
    public boolean isSafeToAccept(Socket clientSocket) {
        if (clientSocket == null || clientSocket.getInetAddress() == null) {
            return false;
        }
        
        String ip = clientSocket.getInetAddress().getHostAddress();
        long now = System.currentTimeMillis();

        Long previousTime = lastConnectionTime.get(ip);
        if (previousTime != null) {
            long timeSinceLastConnection = now - previousTime;
            if (timeSinceLastConnection < MIN_CONNECTION_INTERVAL_MS) {
                LOG.warn("[ALERTE SÉCURITÉ] Bloqué : L'IP {} se connecte trop vite ({} ms) ! Attaque DoS/Flood potentielle.", ip, timeSinceLastConnection);
                lastConnectionTime.put(ip, now);
                return false;
            }
        }

        Long windowStart = connectionWindowStart.get(ip);
        Integer count = connectionCountInWindow.get(ip);
        if (windowStart == null || now - windowStart > CONNECTION_WINDOW_MS) {
            connectionWindowStart.put(ip, now);
            connectionCountInWindow.put(ip, 1);
        } else {
            int newCount = (count == null ? 1 : count + 1);
            if (newCount > MAX_CONNECTIONS_PER_WINDOW) {
                LOG.warn("[ALERTE SÉCURITÉ] Bloqué : L'IP {} dépasse la limite de {} connexions par minute (compteur={}).", ip, MAX_CONNECTIONS_PER_WINDOW, newCount);
                return false;
            }
            connectionCountInWindow.put(ip, newCount);
        }

        lastConnectionTime.put(ip, now);
        return true;
    }
}
