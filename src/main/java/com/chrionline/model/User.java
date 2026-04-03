package com.chrionline.model;

import java.time.LocalDateTime;

/**
 * Maps to the {@code users} table.
 */
public class User {

    private int userId;
    private String username;
    private String email;
    private String passwordHash;
    private Role role;
    private boolean suspended;
    private boolean emailVerified;
    private String emailVerificationCode;
    private LocalDateTime emailVerificationExpiresAt;
    private LocalDateTime emailVerificationSentAt;
    private LocalDateTime createdAt;

    public enum Role {
        CLIENT,
        ADMIN
    }

    public User() {}

    public User(int userId, String username, String email,
                String passwordHash, Role role, LocalDateTime createdAt) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public boolean isSuspended() { return suspended; }
    public void setSuspended(boolean suspended) { this.suspended = suspended; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public String getEmailVerificationCode() { return emailVerificationCode; }
    public void setEmailVerificationCode(String emailVerificationCode) { this.emailVerificationCode = emailVerificationCode; }

    public LocalDateTime getEmailVerificationExpiresAt() { return emailVerificationExpiresAt; }
    public void setEmailVerificationExpiresAt(LocalDateTime emailVerificationExpiresAt) {
        this.emailVerificationExpiresAt = emailVerificationExpiresAt;
    }

    public LocalDateTime getEmailVerificationSentAt() { return emailVerificationSentAt; }
    public void setEmailVerificationSentAt(LocalDateTime emailVerificationSentAt) {
        this.emailVerificationSentAt = emailVerificationSentAt;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "User{" +
               "userId=" + userId +
               ", username='" + username + '\'' +
               ", email='" + email + '\'' +
               ", role=" + role +
               ", suspended=" + suspended +
               ", emailVerified=" + emailVerified +
               ", createdAt=" + createdAt +
               '}';
    }
}
