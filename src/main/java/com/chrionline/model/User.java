package com.chrionline.model;

import java.time.LocalDateTime;

/**
 * Maps to the {@code users} table.
 *
 * Schema ENUMs: role → ENUM('CLIENT','ADMIN')
 */
public class User {

    // ── Fields ─────────────────────────────────────────────────────────────
    private int           userId;
    private String        username;
    private String        email;
    private String        passwordHash;
    private Role          role;          // enum — see nested type below
    private LocalDateTime createdAt;

    // ── Nested enum matching schema ENUM('CLIENT','ADMIN') exactly ─────────
    public enum Role {
        CLIENT,
        ADMIN
    }

    // ── Constructors ────────────────────────────────────────────────────────

    /** No-arg constructor required by frameworks / DAO mapping. */
    public User() {}

    /** Full constructor. */
    public User(int userId, String username, String email,
                String passwordHash, Role role, LocalDateTime createdAt) {
        this.userId       = userId;
        this.username     = username;
        this.email        = email;
        this.passwordHash = passwordHash;
        this.role         = role;
        this.createdAt    = createdAt;
    }

    // ── Getters & Setters ───────────────────────────────────────────────────

    public int           getUserId()       { return userId; }
    public void          setUserId(int v)  { this.userId = v; }

    public String        getUsername()          { return username; }
    public void          setUsername(String v)  { this.username = v; }

    public String        getEmail()          { return email; }
    public void          setEmail(String v)  { this.email = v; }

    public String        getPasswordHash()          { return passwordHash; }
    public void          setPasswordHash(String v)  { this.passwordHash = v; }

    public Role          getRole()          { return role; }
    public void          setRole(Role v)    { this.role = v; }

    public LocalDateTime getCreatedAt()          { return createdAt; }
    public void          setCreatedAt(LocalDateTime v) { this.createdAt = v; }

    // ── toString ────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "User{" +
               "userId="       + userId       +
               ", username='"  + username     + '\'' +
               ", email='"     + email        + '\'' +
               ", role="       + role         +
               ", createdAt="  + createdAt    +
               '}';
    }
}
