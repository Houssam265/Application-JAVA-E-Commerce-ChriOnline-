package com.chrionline.server;

import com.chrionline.dao.UserDAO;
import com.chrionline.database.DatabaseConnection;
import com.chrionline.model.Session;
import com.chrionline.model.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages user sessions for the ChriOnline server.
 *
 * <p>Two-tier storage strategy:
 * <ol>
 *   <li>Runtime {@code HashMap<String, Session>} — fast O(1) lookup per request
 *       without touching the database. Used as the primary cache.</li>
 *   <li>MySQL {@code sessions} table — authoritative persistence layer. Sessions
 *       survive server restarts because this table is always written on creation
 *       and read on cache miss.</li>
 * </ol>
 *
 * <p>Thread-safety: all public methods are {@code synchronized} because multiple
 * {@link ClientHandler} threads call them concurrently.
 *
 * <p>This class never touches passwords or BCrypt — all auth logic lives in
 * {@link com.chrionline.service.AuthService}.
 */
public class SessionManager {

    private static final Logger LOG = LogManager.getLogger(SessionManager.class);

    /** Session inactivity timeout: 10 minutes. */
    private static final long SESSION_DURATION_MINUTES = 10;

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final UserDAO userDAO;

    // ── Runtime cache ─────────────────────────────────────────────────────────

    /** Keyed by token string. Accessed from multiple threads — all methods are synchronized. */
    private final HashMap<String, Session> activeSessions = new HashMap<>();

    /** IP d'origine associee a chaque token de session (en memoire uniquement). */
    private final HashMap<String, String> sessionIpByToken = new HashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * @param userDAO used by {@link #getUserFromToken(String)} to reload user objects
     */
    public SessionManager(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    // ── DB connection helper ──────────────────────────────────────────────────

    private Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Creates a new session for {@code user}, persists it to the DB, and caches it
     * in the HashMap.
     *
     * @param user the authenticated user
     * @return the created {@link Session} (token is set)
     */
    public synchronized Session createSession(User user) {
        String        sessionId = UUID.randomUUID().toString();
        String        token     = UUID.randomUUID().toString();
        LocalDateTime now       = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(SESSION_DURATION_MINUTES);

        Session session = new Session(sessionId, user.getUserId(), user.getRole(), token, now, expiresAt, true);

        // 1. Persist to DB (source of truth)
        insertSessionToDb(session);

        // 2. Put in runtime cache
        activeSessions.put(token, session);

        LOG.info("[SESSION] Created for userId={} -> token={}", user.getUserId(), token);
        return session;
    }

    /**
     * Checks whether {@code token} maps to a currently valid session.
     *
     * <p>Checks the HashMap first; falls back to DB on a cache miss, then re-caches it.
     *
     * @param token the session token to validate (may be null)
     * @return {@code true} if the session exists, is active, and has not expired
     */
    public synchronized boolean isTokenValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        // ① HashMap first
        Session cached = activeSessions.get(token);
        if (cached != null) {
            if (cached.isValid()) {
                return true;
            }
            // Session is in cache but expired/invalidated — revoke it
            invalidateSession(token);
            return false;
        }

        // ② Cache miss → check DB
        Optional<Session> fromDb = findSessionInDb(token);
        if (fromDb.isPresent()) {
            if (fromDb.get().isValid()) {
                // Reload into cache
                activeSessions.put(token, fromDb.get());
                return true;
            }
            invalidateSession(token);
        }

        return false;
    }

    /**
     * Returns the {@link Session} for {@code token}, checking cache then DB.
     *
     * @param token the session token
     * @return the Session, or empty if not found / expired
     */
    public synchronized Optional<Session> getSession(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        // ① HashMap first
        Session cached = activeSessions.get(token);
        if (cached != null) {
            if (cached.isValid()) {
                return Optional.of(cached);
            }
            invalidateSession(token);
            return Optional.empty();
        }

        // ② Cache miss → DB
        Optional<Session> fromDb = findSessionInDb(token);
        if (fromDb.isPresent()) {
            if (fromDb.get().isValid()) {
                activeSessions.put(token, fromDb.get());
                return fromDb;
            }
            invalidateSession(token);
        }
        return Optional.empty();
    }

    /**
     * Invalidates a session: marks {@code is_active = false} in the DB and removes
     * it from the cache.
     *
     * @param token the token to invalidate
     */
    public synchronized void invalidateSession(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        // 1. Update DB
        final String sql = "UPDATE sessions SET is_active = FALSE WHERE token = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, token);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.warn("[SESSION] invalidateSession DB update failed: {}", e.getMessage(), e);
        }

        // 2. Remove from caches
        activeSessions.remove(token);
        sessionIpByToken.remove(token);
        LOG.info("[SESSION] Invalidated token={}", token);
    }

    /**
     * Removes all expired sessions from the HashMap and from the DB.
     *
     * <p>Call once at server startup to clean stale rows left from previous runs.
     */
    public synchronized void cleanExpiredSessions() {
        // ① Clean HashMap
        LocalDateTime now = LocalDateTime.now();
        Iterator<Map.Entry<String, Session>> it = activeSessions.entrySet().iterator();
        int removedFromCache = 0;
        while (it.hasNext()) {
            Map.Entry<String, Session> entry = it.next();
            if (!entry.getValue().isValid()) {
                it.remove();
                removedFromCache++;
            }
        }

        // ② Clean DB
        final String sql = "DELETE FROM sessions WHERE expires_at < NOW()";
        int removedFromDb = 0;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            removedFromDb = ps.executeUpdate();
        } catch (SQLException e) {
            LOG.warn("[SESSION] cleanExpiredSessions DB delete failed: {}", e.getMessage(), e);
        }

        LOG.info("[SESSION] Cleanup done - removed from cache: {}, removed from DB: {}",
                removedFromCache, removedFromDb);
    }

    /**
     * Resolves the {@link User} that owns {@code token}.
     *
     * @param token the session token
     * @return the user, or empty if the session is invalid or the user no longer exists
     */
    public synchronized Optional<User> getUserFromToken(String token) {
        return getSession(token)
                .flatMap(session -> userDAO.findById(session.getUserId()));
    }

    /**
     * Associe l'adresse IP initiale a un token de session, si aucune IP
     * n'est encore enregistree pour ce token.
     *
     * @param token    token de session
     * @param clientIp IP courante du socket
     */
    public synchronized void bindSessionIpIfAbsent(String token, String clientIp) {
        if (token == null || token.isBlank() || clientIp == null || clientIp.isBlank()) {
            return;
        }
        sessionIpByToken.putIfAbsent(token, clientIp);
    }

    /**
     * Verifie la coherence entre l'IP actuelle et l'IP d'origine pour ce token.
     *
     * @param token       token de session
     * @param currentIp   IP courante du socket
     * @return true si aucune IP n'etait enregistree (desormais liee) ou si elle correspond,
     *         false si une IP differente est detectee.
     */
    public synchronized boolean isSessionIpConsistent(String token, String currentIp) {
        if (token == null || token.isBlank() || currentIp == null || currentIp.isBlank()) {
            return true;
        }
        String stored = sessionIpByToken.get(token);
        if (stored == null) {
            sessionIpByToken.put(token, currentIp);
            return true;
        }
        return stored.equals(currentIp);
    }

    /**
     * Retourne l'IP d'origine enregistree pour ce token, ou {@code null}
     * si aucune IP n'est connue.
     */
    public synchronized String getSessionIp(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return sessionIpByToken.get(token);
    }

    /**
     * Extends a valid session after each authenticated action.
     */
    public synchronized void refreshSession(String token) {
        Optional<Session> sessionOpt = getSession(token);
        if (sessionOpt.isEmpty()) {
            return;
        }

        Session session = sessionOpt.get();
        LocalDateTime newExpiresAt = LocalDateTime.now().plusMinutes(SESSION_DURATION_MINUTES);
        session.setExpiresAt(newExpiresAt);

        final String sql = "UPDATE sessions SET expires_at = ? WHERE token = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(newExpiresAt));
            ps.setString(2, token);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.warn("[SESSION] refreshSession DB update failed for token={}: {}", token, e.getMessage(), e);
        }
    }

    // ── Private DB helpers ────────────────────────────────────────────────────

    /** Inserts a new session row into the {@code sessions} table. */
    private void insertSessionToDb(Session session) {
        final String sql =
                "INSERT INTO sessions (session_id, user_id, token, created_at, expires_at, is_active) " +
                        "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString   (1, session.getSessionId());
            ps.setInt      (2, session.getUserId());
            ps.setString   (3, session.getToken());
            ps.setTimestamp(4, Timestamp.valueOf(session.getCreatedAt()));
            ps.setTimestamp(5, Timestamp.valueOf(session.getExpiresAt()));
            ps.setBoolean  (6, session.isActive());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(
                    "[SESSION] insertSessionToDb failed for userId=" + session.getUserId()
                            + ": " + e.getMessage(), e);
        }
    }

    /**
     * Loads a session from the DB by token.
     * Does NOT check validity — the caller must call {@link Session#isValid()}.
     */
    private Optional<Session> findSessionInDb(String token) {
        final String sql =
                "SELECT session_id, user_id, token, created_at, expires_at, is_active " +
                        "FROM sessions WHERE token = ? LIMIT 1";

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Session s = new Session();
                s.setSessionId(rs.getString  ("session_id"));
                s.setUserId   (rs.getInt     ("user_id"));
                s.setToken    (rs.getString  ("token"));

                Timestamp createdAt = rs.getTimestamp("created_at");
                s.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);

                Timestamp expiresAt = rs.getTimestamp("expires_at");
                s.setExpiresAt(expiresAt != null ? expiresAt.toLocalDateTime() : null);

                s.setActive(rs.getBoolean("is_active"));
                userDAO.findById(s.getUserId()).ifPresent(user -> s.setRole(user.getRole()));
                return Optional.of(s);
            }
        } catch (SQLException e) {
            LOG.warn("[SESSION] findSessionInDb failed for token={}: {}", token, e.getMessage(), e);
            return Optional.empty();
        }
    }
}
