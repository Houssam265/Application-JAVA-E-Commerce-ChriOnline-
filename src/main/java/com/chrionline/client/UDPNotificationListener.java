package com.chrionline.client;

import org.json.JSONObject;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * UDP daemon listener for server notifications (KAN-8).
 */
public class UDPNotificationListener implements Closeable {

    public static final int DEFAULT_PORT = 9090;
    private static final int BUFFER_SIZE = 4096;

    private DatagramSocket socket;
    private Thread thread;
    private volatile boolean running;

    public boolean isRunning() {
        return running;
    }

    public void start() throws SocketException {
        if (running) return;

        DatagramSocket ds = new DatagramSocket(null);
        ds.setReuseAddress(true);
        ds.bind(new InetSocketAddress(DEFAULT_PORT));
        this.socket = ds;

        running = true;
        thread = new Thread(this::listenLoop, "udp-notification-listener");
        thread.setDaemon(true);
        thread.start();
    }

    private void listenLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (running && socket != null && !socket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                String json = new String(
                        packet.getData(),
                        packet.getOffset(),
                        packet.getLength(),
                        StandardCharsets.UTF_8);
                handleNotification(json);
            } catch (SocketException e) {
                if (!running) break;
            } catch (IOException ignored) {
                // Best effort: ignore malformed/partial packets
            }
        }
    }

    private void handleNotification(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            String type = obj.optString("type", "NOTIF");
            String message = obj.optString("message", "");
            String orderId = obj.optString("orderId", "");
            String timestamp = obj.optString("timestamp", "");

            StringBuilder sb = new StringBuilder("[UDP] ");
            if (!timestamp.isBlank()) sb.append(timestamp).append(" ");
            sb.append(type);
            if (!orderId.isBlank()) sb.append(" order=").append(orderId);
            if (!message.isBlank()) sb.append(" - ").append(message);
            System.out.println(sb);
        } catch (Exception e) {
            System.out.println("[UDP] Notification brute: " + json);
        }
    }

    @Override
    public void close() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        socket = null;
        thread = null;
    }
}
