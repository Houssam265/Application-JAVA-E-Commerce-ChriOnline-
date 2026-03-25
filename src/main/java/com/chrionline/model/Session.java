package com.chrionline.model;

import java.time.LocalDateTime;

/**
 * Maps to the {@code sessions} table.
 *
 * session_id  VARCHAR(36)  — plain UUID (java.util.UUID)
 * user_id     INT          — FK → users(user_id)
 * token       VARCHAR(255) — unique token string
 * created_at  DATETIME
 * expires_at  DATETIME     — created_at + 30 minutes
 * is_active   BOOLEAN
 */
public class Session {

    // ── Fields ─────────────────────────────────────────────────────────────
    private String        sessionId;
    private int           userId;
    private User.Role     role;
    private String        token;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean       isActive;

    // ── Constructors ────────────────────────────────────────────────────────

    /** No-arg constructor required by DAO row mapping. */
    public Session() {}

    /** Full constructor (legacy, no role). */
    public Session(String sessionId, int userId, String token,
                   LocalDateTime createdAt, LocalDateTime expiresAt, boolean isActive) {
        this(sessionId, userId, null, token, createdAt, expiresAt, isActive);
    }

    /** Full constructor with role. */
    public Session(String sessionId, int userId, User.Role role, String token,
                   LocalDateTime createdAt, LocalDateTime expiresAt, boolean isActive) {
        this.sessionId = sessionId;
        this.userId    = userId;
        this.role      = role;
        this.token     = token;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.isActive  = isActive;
    }

    // ── Getters & Setters ───────────────────────────────────────────────────

    public String        getSessionId()               { return sessionId; }
    public void          setSessionId(String v)        { this.sessionId = v; }

    public int           getUserId()                   { return userId; }
    public void          setUserId(int v)              { this.userId = v; }

    public User.Role     getRole()                     { return role; }
    public void          setRole(User.Role v)          { this.role = v; }

    public String        getToken()                    { return token; }
    public void          setToken(String v)            { this.token = v; }

    public LocalDateTime getCreatedAt()                { return createdAt; }
    public void          setCreatedAt(LocalDateTime v) { this.createdAt = v; }

    public LocalDateTime getExpiresAt()                { return expiresAt; }
    public void          setExpiresAt(LocalDateTime v) { this.expiresAt = v; }

    public boolean       isActive()                    { return isActive; }
    public void          setActive(boolean v)          { this.isActive = v; }

    // ── Convenience ─────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the session is still usable right now:
     * is_active == true AND expiresAt is in the future.
     */
    public boolean isValid() {
        return isActive && expiresAt != null && LocalDateTime.now().isBefore(expiresAt);
    }

    // ── toString ────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "Session{" +
                "sessionId='" + sessionId + '\'' +
                ", userId="   + userId    +
                ", role="     + role      +
                ", isActive=" + isActive  +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
