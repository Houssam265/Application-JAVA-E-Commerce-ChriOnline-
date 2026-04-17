package com.chrionline.model;

import java.time.LocalDateTime;

public class Admin {
    private int adminId;
    private String username;
    private String publicKey;
    private boolean isActive;
    private LocalDateTime createdAt;

    public Admin() {}

    public Admin(int adminId, String username, String publicKey, boolean isActive, LocalDateTime createdAt) {
        this.adminId = adminId;
        this.username = username;
        this.publicKey = publicKey;
        this.isActive = isActive;
        this.createdAt = createdAt;
    }

    public int getAdminId() { return adminId; }
    public void setAdminId(int adminId) { this.adminId = adminId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
