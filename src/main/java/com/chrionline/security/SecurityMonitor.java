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
    
    // Limite de temps entre deux connexions depuis la même IP (en millisecondes)
    private static final long MIN_CONNECTION_INTERVAL_MS = 500;

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
                // On met à jour l'heure de la dernière tentative pour prolonger le blocage si l'attaque continue
                lastConnectionTime.put(ip, now);
                return false;
            }
        }
        
        lastConnectionTime.put(ip, now);
        return true;
    }
}
