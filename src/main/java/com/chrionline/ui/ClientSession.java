package com.chrionline.ui;

import com.chrionline.model.User;
import com.chrionline.model.Session;

/**
 * Client-side session cache for the JavaFX app.
 * Stores identity information returned by the server on LOGIN/REGISTER.
 */
public final class ClientSession {

    private static volatile ClientSession instance;

    public static ClientSession getInstance() {
        if (instance == null) {
            synchronized (ClientSession.class) {
                if (instance == null) {
                    instance = new ClientSession();
                }
            }
        }
        return instance;
    }

    private Integer userId;
    private String username;
    private String email;
    private Session.Role role;
    /** Last order created from cart (used by Checkout screen). */
    private String currentOrderId;

    private ClientSession() {}

    public void clear() {
        this.userId = null;
        this.username = null;
        this.email = null;
        this.role = null;
        this.currentOrderId = null;
    }

    public boolean isLoggedIn() {
        return userId != null;
    }

    public boolean isAdmin() {
        return role == Session.Role.ADMIN;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Session.Role getRole() {
        return role;
    }

    public void setRole(Session.Role role) {
        this.role = role;
    }

    public String getCurrentOrderId() {
        return currentOrderId;
    }

    public void setCurrentOrderId(String currentOrderId) {
        this.currentOrderId = currentOrderId;
    }
}

