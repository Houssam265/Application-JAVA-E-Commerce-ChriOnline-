package com.chrionline.ui.notifications;

import java.time.LocalDateTime;

public class AppNotification {
    private final String message;
    private final LocalDateTime timestamp;
    private boolean read;

    public AppNotification(String message, LocalDateTime timestamp) {
        this.message = message == null ? "" : message;
        this.timestamp = timestamp == null ? LocalDateTime.now() : timestamp;
        this.read = false;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }
}

