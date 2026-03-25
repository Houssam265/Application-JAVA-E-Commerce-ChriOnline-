package com.chrionline.ui.notifications;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Background daemon thread that listens for UDP server push notifications (KAN-31).
 */
public final class UdpNotificationClient {
    private static final Logger LOG = Logger.getLogger(UdpNotificationClient.class.getName());

    private static final int DEFAULT_PORT = 9090;
    private static volatile Thread thread;
    private static volatile boolean running;

    private UdpNotificationClient() {}

    public static void startIfNeeded() {
        startIfNeeded(DEFAULT_PORT);
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
                    String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                    if (!msg.isBlank()) {
                        NotificationCenter.getInstance().addNotification(msg);
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
}

