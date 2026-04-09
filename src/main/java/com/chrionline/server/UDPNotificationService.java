package com.chrionline.server;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * UDP notification sender (KAN-8).
 *
 * Sends JSON payloads to the CLIENT's listening port (9091).
 * Uses an ephemeral outgoing socket so it never conflicts with any bound port.
 */
public class UDPNotificationService implements Closeable {

    /** Legacy constant kept for API compatibility. */
    public static final int DEFAULT_PORT = 9090;

    private static final Logger LOG = LogManager.getLogger(UDPNotificationService.class);
    private static final Gson GSON = new Gson();

    private final DatagramSocket socket;

    /** @param ignoredPort kept for backward-compat; sender always uses an ephemeral port. */
    public UDPNotificationService(int ignoredPort) {
        DatagramSocket ds;
        try {
            ds = new DatagramSocket(); // ephemeral port — no conflict with client listener
        } catch (SocketException e) {
            throw new RuntimeException("[UDP] Unable to open sender UDP socket.", e);
        }
        this.socket = ds;
    }

    public void sendNotification(InetAddress address, String type, String message, String orderId) {
        if (address == null || socket == null || socket.isClosed()) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("message", message);
        payload.put("orderId", orderId);
        payload.put("timestamp", LocalDateTime.now().toString());

        byte[] data = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);

        // Must match UdpNotificationClient.CLIENT_PORT (9091)
        int clientPort = com.chrionline.ui.notifications.UdpNotificationClient.CLIENT_PORT;
        DatagramPacket packet = new DatagramPacket(data, data.length, address, clientPort);
        try {
            socket.send(packet);
            LOG.info("[UDP] Sent {} to {}:{}", type, address, clientPort);
        } catch (IOException e) {
            LOG.debug("[UDP] Failed to send notification to {}", address, e);
        }
    }

    @Override
    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
