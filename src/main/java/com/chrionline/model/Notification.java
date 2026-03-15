package com.chrionline.model;

import java.time.LocalDateTime;

/**
 * Maps to the {@code notifications} table.
 *
 * columns: notification_id (PK), user_id (FK), message TEXT,
 *          sent_at DATETIME, is_read BOOLEAN
 */
public class Notification {

    // ── Fields ─────────────────────────────────────────────────────────────
    private int           notificationId;
    private int           userId;
    private String        message;
    private LocalDateTime sentAt;
    private boolean       isRead;

    // ── Constructors ────────────────────────────────────────────────────────

    public Notification() {}

    public Notification(int notificationId, int userId, String message,
                        LocalDateTime sentAt, boolean isRead) {
        this.notificationId = notificationId;
        this.userId         = userId;
        this.message        = message;
        this.sentAt         = sentAt;
        this.isRead         = isRead;
    }

    // ── Getters & Setters ───────────────────────────────────────────────────

    public int           getNotificationId()          { return notificationId; }
    public void          setNotificationId(int v)     { this.notificationId = v; }

    public int           getUserId()       { return userId; }
    public void          setUserId(int v)  { this.userId = v; }

    public String        getMessage()          { return message; }
    public void          setMessage(String v)  { this.message = v; }

    public LocalDateTime getSentAt()               { return sentAt; }
    public void          setSentAt(LocalDateTime v){ this.sentAt = v; }

    public boolean       isRead()           { return isRead; }
    public void          setRead(boolean v) { this.isRead = v; }

    // ── toString ────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "Notification{" +
               "notificationId=" + notificationId +
               ", userId="       + userId         +
               ", message='"     + message        + '\'' +
               ", sentAt="       + sentAt         +
               ", isRead="       + isRead         +
               '}';
    }
}
