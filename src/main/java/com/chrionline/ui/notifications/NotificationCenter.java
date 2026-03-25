package com.chrionline.ui.notifications;

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.time.LocalDateTime;

/**
 * UI-side store for UDP notifications (KAN-31).
 * Keeps an unread counter and a list of notifications.
 */
public final class NotificationCenter {

    private static volatile NotificationCenter instance;

    public static NotificationCenter getInstance() {
        if (instance == null) {
            synchronized (NotificationCenter.class) {
                if (instance == null) instance = new NotificationCenter();
            }
        }
        return instance;
    }

    private final ObservableList<AppNotification> notifications = FXCollections.observableArrayList();
    private final IntegerProperty unreadCount = new SimpleIntegerProperty(0);

    private NotificationCenter() {
        notifications.addListener((ListChangeListener<AppNotification>) c -> recomputeUnread());
    }

    public ObservableList<AppNotification> getNotifications() {
        return notifications;
    }

    public IntegerProperty unreadCountProperty() {
        return unreadCount;
    }

    public void addNotification(String message) {
        Runnable r = () -> notifications.add(0, new AppNotification(message, LocalDateTime.now()));
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    public void markAllAsRead() {
        Runnable r = () -> {
            for (AppNotification n : notifications) n.setRead(true);
            recomputeUnread();
        };
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    public void markAsRead(AppNotification n) {
        if (n == null) return;
        Runnable r = () -> {
            n.setRead(true);
            recomputeUnread();
        };
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    private void recomputeUnread() {
        int unread = 0;
        for (AppNotification n : notifications) {
            if (!n.isRead()) unread++;
        }
        unreadCount.set(unread);
    }
}

