package com.chrionline.server;

import com.google.gson.Gson;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UDP notification sender (KAN-8).
 *
 * Sends JSON payloads to clients on a fixed UDP port.
 */
public class UDPNotificationService implements Closeable {

    public static final int DEFAULT_PORT = 9090;
    private static final Logger LOG = Logger.getLogger(UDPNotificationService.class.getName());
    private static final Gson GSON = new Gson();

    private final int port;
    private final DatagramSocket socket;

    public UDPNotificationService(int port) {
        this.port = port;
        DatagramSocket ds = null;
        try {
            ds = new DatagramSocket(null);
            ds.setReuseAddress(true);
            ds.bind(new InetSocketAddress(port));
        } catch (SocketException e) {
            if (ds != null && !ds.isClosed()) {
                ds.close();
            }
            LOG.log(Level.WARNING, "[UDP] Bind failed on port " + port + ", fallback to ephemeral.", e);
            try {
                ds = new DatagramSocket();
            } catch (SocketException ex) {
                throw new RuntimeException("[UDP] Unable to open UDP socket.", ex);
            }
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
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        try {
            socket.send(packet);
        } catch (IOException e) {
            LOG.log(Level.FINE, "[UDP] Failed to send notification to " + address, e);
        }
    }

    @Override
    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
