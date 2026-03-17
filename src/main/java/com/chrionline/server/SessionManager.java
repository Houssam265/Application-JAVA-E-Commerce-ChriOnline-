package com.chrionline.server;

import com.chrionline.dao.UserDAO;
import com.chrionline.database.DatabaseConnection;
import com.chrionline.model.Session;
import com.chrionline.model.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private static final Logger LOG = Logger.getLogger(SessionManager.class.getName());

    /** Session duration: 30 minutes. */
    private static final long SESSION_DURATION_MINUTES = 30;

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final UserDAO userDAO;

    // ── Runtime cache ─────────────────────────────────────────────────────────

    /** Keyed by token string. Accessed from multiple threads — all methods are synchronized. */
    private final HashMap<String, Session> activeSessions = new HashMap<>();

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

        Session session = new Session(sessionId, user.getUserId(), token, now, expiresAt, true);

        // 1. Persist to DB (source of truth)
        insertSessionToDb(session);

        // 2. Put in runtime cache
        activeSessions.put(token, session);

        LOG.info("[SESSION] Created for userId=" + user.getUserId() + " → token=" + token);
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
            // Session is in cache but expired/invalidated — remove it
            activeSessions.remove(token);
            return false;
        }

        // ② Cache miss → check DB
        Optional<Session> fromDb = findSessionInDb(token);
        if (fromDb.isPresent() && fromDb.get().isValid()) {
            // Reload into cache
            activeSessions.put(token, fromDb.get());
            return true;
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
            return cached.isValid() ? Optional.of(cached) : Optional.empty();
        }

        // ② Cache miss → DB
        Optional<Session> fromDb = findSessionInDb(token);
        if (fromDb.isPresent() && fromDb.get().isValid()) {
            activeSessions.put(token, fromDb.get());
            return fromDb;
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
            LOG.log(Level.WARNING, "[SESSION] invalidateSession DB update failed: " + e.getMessage(), e);
        }

        // 2. Remove from cache
        activeSessions.remove(token);
        LOG.info("[SESSION] Invalidated token=" + token);
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
            LOG.log(Level.WARNING, "[SESSION] cleanExpiredSessions DB delete failed: " + e.getMessage(), e);
        }

        LOG.info("[SESSION] Cleanup done — removed from cache: " + removedFromCache
                 + ", removed from DB: " + removedFromDb);
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
                return Optional.of(s);
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[SESSION] findSessionInDb failed for token=" + token + ": " + e.getMessage(), e);
            return Optional.empty();
        }
    }
}