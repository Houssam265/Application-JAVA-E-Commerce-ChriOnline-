package com.chrionline.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;

/**
 * Logger d'audit de securite centralise.
 * <p>
 * Utilise un logger Log4j2 dedie nomme "SECURITY_AUDIT" afin de separer
 * les journaux sensibles (spoofing, acces admin, etc.) des logs applicatifs.
 */
public final class SecurityAuditLogger {

    /**
     * Logger dedie pour l'audit de securite.
     * <p>
     * La configuration est definie dans log4j2.xml avec un appender specifique.
     */
    private static final Logger AUDIT_LOG = LogManager.getLogger("SECURITY_AUDIT");

    private SecurityAuditLogger() {
        throw new UnsupportedOperationException("SecurityAuditLogger est une classe utilitaire statique");
    }

    /**
     * Logue une tentative d'usurpation d'adresse IP.
     *
     * @param realIp    IP observee par le serveur (remoteAddr)
     * @param spoofedIp IP revendiquee via les en-tetes (X-Forwarded-For, etc.)
     * @param uri       URI ciblee par la requete
     */
    public static void logIpSpoofingAttempt(String realIp, String spoofedIp, String uri) {
        AUDIT_LOG.warn("[IP_SPOOFING_ATTEMPT] ts={} real_ip={} spoofed_ip={} uri={}",
                Instant.now(), safe(realIp), safe(spoofedIp), safe(uri));
    }

    /**
     * Logue un acces admin reussi.
     *
     * @param username nom d'utilisateur admin
     * @param ip       adresse IP verifiee
     */
    public static void logAdminAccessSuccess(String username, String ip) {
        AUDIT_LOG.info("[ADMIN_ACCESS_SUCCESS] ts={} username={} ip={}",
                Instant.now(), safe(username), safe(ip));
    }

    /**
     * Logue un acces admin echoue.
     *
     * @param username nom d'utilisateur (si connu)
     * @param ip       adresse IP verifiee
     * @param reason   raison fonctionnelle du refus
     */
    public static void logAdminAccessFailure(String username, String ip, String reason) {
        AUDIT_LOG.warn("[ADMIN_ACCESS_FAILURE] ts={} username={} ip={} reason={}",
                Instant.now(), safe(username), safe(ip), safe(reason));
    }

    /**
     * Logue le blocage d'une connexion admin provenant d'une IP externe.
     *
     * @param username nom d'utilisateur admin
     * @param ip       adresse IP externe detectee
     */
    public static void logAdminExternalIpBlocked(String username, String ip) {
        AUDIT_LOG.warn("[ADMIN_EXTERNAL_IP_BLOCKED] ts={} username={} ip={}",
                Instant.now(), safe(username), safe(ip));
    }

    /**
     * Logue la detection d'un changement d'adresse IP en cours de session.
     *
     * @param username nom d'utilisateur (si connu)
     * @param oldIp    IP d'origine enregistree pour la session
     * @param newIp    IP actuelle du socket
     */
    public static void logSessionIpChangeDetected(String username, String oldIp, String newIp) {
        AUDIT_LOG.warn("[SESSION_IP_CHANGE_DETECTED] ts={} username={} old_ip={} new_ip={}",
                Instant.now(), safe(username), safe(oldIp), safe(newIp));
    }

    /**
     * Logue qu'une verification 2FA supplementaire est requise pour une IP non reconnue.
     *
     * @param username nom d'utilisateur
     * @param ip       adresse IP pour laquelle le 2FA est impose
     * @param admin    true si le compte est admin
     */
    public static void logUnrecognizedIp2faRequired(String username, String ip, boolean admin) {
        AUDIT_LOG.info("[UNRECOGNIZED_IP_2FA_REQUIRED] ts={} username={} ip={} admin={}",
                Instant.now(), safe(username), safe(ip), admin);
    }

    /**
     * Connexion TLS acceptée et handshake réussi (côté serveur).
     */
    public static void logTlsClientConnected(String remoteIp, String protocol, String cipherSuite) {
        AUDIT_LOG.info("[TLS_CLIENT_CONNECTED] ts={} ip={} protocol={} cipher={}",
                Instant.now(), safe(remoteIp), safe(protocol), safe(cipherSuite));
    }

    /**
     * Fin de connexion TLS / TCP (normale ou erreur).
     */
    public static void logTlsClientDisconnected(String remoteIp, String reason) {
        AUDIT_LOG.info("[TLS_CLIENT_DISCONNECTED] ts={} ip={} reason={}",
                Instant.now(), safe(remoteIp), safe(reason));
    }

    /**
     * Échec de handshake TLS (événement sécurité).
     */
    public static void logTlsHandshakeFailure(String remoteIp, String message) {
        AUDIT_LOG.warn("[TLS_HANDSHAKE_FAILURE] ts={} ip={} message={}",
                Instant.now(), safe(remoteIp), safe(message));
    }

    /**
     * Normalise une valeur potentiellement nulle pour les logs.
     */
    private static String safe(String value) {
        return value == null ? "-" : value;
    }
}
