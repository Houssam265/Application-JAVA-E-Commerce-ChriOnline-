package com.chrionline.security;

import java.net.InetAddress;
import java.net.Socket;

/**
 * Detection basique de tentatives d'usurpation ou d'adresses IP anormales
 * pour les connexions TCP brutes du serveur.
 * <p>
 * Dans cette architecture (TCP + TLS sans HTTP ni proxies), le serveur ne
 * recoit qu'une seule adresse IP fiable: celle de la connexion socket
 * {@link Socket#getInetAddress()}. On ne peut donc pas comparer une "IP
 * revendiquee" (X-Forwarded-For, etc.) a une IP reelle comme sur HTTP.
 * <p>
 * Ce detecteur se concentre donc sur les cas d'adresses clairement invalides
 * ou suspectes, et journalise ces evenements dans l'audit de securite.
 */
public final class IpSpoofingDetector {

    private IpSpoofingDetector() {
        throw new UnsupportedOperationException("IpSpoofingDetector est une classe utilitaire statique");
    }

    /**
     * Verifie si une connexion TCP semble provenir d'une adresse IP invalide
     * ou manifestement anormale.
     * <p>
     * Cette methode ne peut pas prouver une usurpation IP (TCP/IP se charge
     * deja d'eliminer la plupart des paquets spoofes non routables), mais
     * elle permet de:
     * <ul>
     *     <li>Filtrer les adresses nulles ou inconnues.</li>
     *     <li>Bloquer les adresses "0.0.0.0" ou "::" qui n'ont pas de sens
     *     comme adresse d'origine de client.</li>
     * </ul>
     *
     * @param clientSocket socket TCP accepte par le serveur
     * @return true si la connexion semble suspecte et doit etre refusee
     */
    public static boolean isSuspicious(Socket clientSocket) {
        if (clientSocket == null) {
            SecurityAuditLogger.logIpSpoofingAttempt(null, null, "TCP_CONNECT_NULL_SOCKET");
            return true;
        }

        InetAddress address = clientSocket.getInetAddress();
        if (address == null) {
            SecurityAuditLogger.logIpSpoofingAttempt(null, null, "TCP_CONNECT_NO_ADDRESS");
            return true;
        }

        String ip = address.getHostAddress();
        if (ip == null || ip.isBlank()) {
            SecurityAuditLogger.logIpSpoofingAttempt(null, null, "TCP_CONNECT_EMPTY_IP");
            return true;
        }

        // Adresses speciales qui ne devraient jamais apparaitre comme origine d'un client distant
        if ("0.0.0.0".equals(ip) || "::".equals(ip)) {
            SecurityAuditLogger.logIpSpoofingAttempt(ip, null, "TCP_CONNECT_INVALID_SOURCE");
            return true;
        }

        return false;
    }
}

