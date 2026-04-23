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

    public enum Role {
        CLIENT,
        ADMIN_PENDING,
        ADMIN,
        SUPER_ADMIN;

        public boolean isPrivileged() {
            return this == ADMIN || this == SUPER_ADMIN;
        }
    }

    // ── Fields ─────────────────────────────────────────────────────────────
    private String        sessionId;
    private Integer       userId;
    private Role          role;
    private String        token;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean       isActive;

    // ── Constructors ────────────────────────────────────────────────────────

    public Session() {}

    public Session(String sessionId, Integer userId, Role role, String token,
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

    public Integer       getUserId()                   { return userId; }
    public void          setUserId(Integer v)          { this.userId = v; }

    public Role          getRole()                     { return role; }
    public void          setRole(Role v)               { this.role = v; }

    public String        getToken()                    { return token; }
    public void          setToken(String v)            { this.token = v; }

    public LocalDateTime getCreatedAt()                { return createdAt; }
    public void          setCreatedAt(LocalDateTime v) { this.createdAt = v; }

    public LocalDateTime getExpiresAt()                { return expiresAt; }
    public void          setExpiresAt(LocalDateTime v) { this.expiresAt = v; }

    public boolean       isActive()                    { return isActive; }
    public void          setActive(boolean v)          { this.isActive = v; }

    // ── Convenience ─────────────────────────────────────────────────────────

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
