package com.chrionline.ui.notifications;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Background daemon thread that listens for UDP server push notifications (KAN-31).
 *
 * Listens on CLIENT_PORT (9091) — distinct from the server's sender socket (9090) —
 * so that both server and client can run on the same machine without port conflicts.
 */
public final class UdpNotificationClient {
    private static final Logger LOG = Logger.getLogger(UdpNotificationClient.class.getName());

    /** Port the CLIENT listens on. Must differ from the server's bind port (9090). */
    public static final int CLIENT_PORT = 9091;

    private static volatile Thread thread;
    private static volatile boolean running;

    private UdpNotificationClient() {}

    public static void startIfNeeded() {
        startIfNeeded(CLIENT_PORT);
    }

    public static void startIfNeeded(int port) {
        if (running) return;
        running = true;

        thread = new Thread(() -> runLoop(port), "udp-notification-listener");
        thread.setDaemon(true);
        thread.start();
    }

    public static void stop() {
        running = false;
        Thread t = thread;
        if (t != null) t.interrupt();
        thread = null;
    }

    private static void runLoop(int port) {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            socket.setSoTimeout(1000);
            byte[] buf = new byte[8192];

            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    if (packet.getLength() <= 0) {
                        continue; // ignore empty packets
                    }
                    String raw = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                    if (!raw.isBlank()) {
                        String humanMessage = parseNotificationMessage(raw);
                        NotificationCenter.getInstance().addNotification(humanMessage);
                    }
                } catch (SocketTimeoutException ignored) {
                    // keep listening, allows graceful shutdown checks
                } catch (Exception malformed) {
                    // malformed packet or transient decoding/network issue: keep thread alive
                    LOG.log(Level.FINE, "Ignoring malformed UDP packet: " + malformed.getMessage(), malformed);
                }
            }
        } catch (SocketException se) {
            // Port may be unavailable; keep UI running without surfacing noisy toasts.
            LOG.log(Level.INFO, "UDP listener unavailable on port " + port + ": " + se.getMessage());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "UDP listener terminated unexpectedly: " + e.getMessage(), e);
        }
    }

    /**
     * Parses the JSON notification payload and returns a clean, human-readable string.
     * Falls back to the raw string if parsing fails.
     */
    private static String parseNotificationMessage(String raw) {
        try {
            JSONObject obj = new JSONObject(raw);
            String type    = obj.optString("type", "");
            String message = obj.optString("message", "");
            String orderId = obj.optString("orderId", "");

            // Build a user-friendly label based on the notification type
            String prefix = switch (type) {
                case "ORDER_STATUS_UPDATED" -> "🔔 Mise à jour commande";
                case "ORDER_VALIDATED"      -> "✅ Commande validée";
                case "PAYMENT_CONFIRMED"    -> "💳 Paiement confirmé";
                default                     -> "📣 Notification";
            };

            StringBuilder sb = new StringBuilder(prefix);
            if (!orderId.isBlank()) {
                // Show short order id (first 8 chars) to keep it readable
                String shortId = orderId.length() > 8 ? orderId.substring(0, 8) + "…" : orderId;
                sb.append(" #").append(shortId);
            }
            if (!message.isBlank()) {
                sb.append(" — ").append(message);
            }
            return sb.toString();
        } catch (Exception e) {
            return raw; // fallback: show raw text
        }
    }
}

