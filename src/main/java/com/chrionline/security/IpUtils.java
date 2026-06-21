package com.chrionline.security;

/**
 * Utilitaires reseau lies aux adresses IP.
 * <p>
 * Cette classe centralise la logique de detection des adresses IP privees
 * afin d'eviter la duplication de code dans plusieurs filtres / services.
 */
public final class IpUtils {

    private IpUtils() {
        throw new UnsupportedOperationException("IpUtils est une classe utilitaire statique");
    }

    /**
     * Indique si une adresse IP appartient a une plage privee ou loopback.
     * <p>
     * Couvre les cas suivants:
     * - 127.x.x.x (loopback IPv4)
     * - ::1 (loopback IPv6)
     * - 10.x.x.x (plage privee /8)
     * - 192.168.x.x (plage privee /16)
     * - 172.16.x.x a 172.31.x.x (plage privee /12)
     * - fc00::/7 et fd00::/8 (adresses IPv6 ULA)
     *
     * @param ip adresse IP en texte (IPv4 ou IPv6)
     * @return true si l'IP est consideree comme privee, false sinon
     */
    public static boolean isPrivateIp(String ip) {
        if (ip == null) {
            return false;
        }

        String normalized = ip.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return false;
        }

        // Loopback IPv4 et IPv6
        if (normalized.startsWith("127.") || "::1".equals(normalized)) {
            return true;
        }

        // 10.0.0.0/8
        if (normalized.startsWith("10.")) {
            return true;
        }

        // 192.168.0.0/16
        if (normalized.startsWith("192.168.")) {
            return true;
        }

        // 172.16.0.0/12 -> 172.16.x.x a 172.31.x.x
        if (normalized.startsWith("172.")) {
            String[] parts = normalized.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }

        // IPv6 ULA fc00::/7 (prefixes fc00::/8 et fd00::/8)
        if (normalized.startsWith("fc") || normalized.startsWith("fd")) {
            return true;
        }

        return false;
    }
}

