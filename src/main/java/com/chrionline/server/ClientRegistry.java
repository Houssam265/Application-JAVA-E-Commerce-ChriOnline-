package com.chrionline.server;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry that tracks the currently connected TCP client IP
 * for each authenticated user.
 *
 * When a user logs in, the ClientHandler registers their userId → InetAddress.
 * When the connection closes, the entry is removed.
 * The UDPNotificationService uses this to know which IP to send the UDP push to.
 */
public final class ClientRegistry {

    private static final ClientRegistry INSTANCE = new ClientRegistry();

    public static ClientRegistry getInstance() {
        return INSTANCE;
    }

    /** userId → last known client InetAddress */
    private final ConcurrentHashMap<Integer, InetAddress> addressByUserId = new ConcurrentHashMap<>();

    private ClientRegistry() {}

    /** Called by ClientHandler after a successful LOGIN or token validation. */
    public void register(int userId, InetAddress address) {
        if (userId > 0 && address != null) {
            addressByUserId.put(userId, address);
        }
    }

    /** Called by ClientHandler when the connection closes. */
    public void unregister(int userId) {
        addressByUserId.remove(userId);
    }

    /**
     * Returns the InetAddress of the connected client for the given userId,
     * or {@code null} if the user is not currently connected.
     */
    public InetAddress getAddress(int userId) {
        return addressByUserId.get(userId);
    }
}
