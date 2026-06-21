package com.chrionline.security;

import java.net.InetAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple per-IP UDP rate limiter.
 *
 * <p>This class is intentionally small and easy to read for security labs:
 * an IP can be blocked manually, and each IP is limited to a fixed number
 * of packets during a short time window.</p>
 */
public class UdpFloodProtector {

    private static final int DEFAULT_MAX_PACKETS_PER_WINDOW = 100;
    private static final long DEFAULT_WINDOW_MS = 1_000L;

    private final int maxPacketsPerWindow;
    private final long windowMs;
    private final Set<String> blockedIps = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, PacketWindow> packetWindows = new ConcurrentHashMap<>();

    public UdpFloodProtector() {
        this(DEFAULT_MAX_PACKETS_PER_WINDOW, DEFAULT_WINDOW_MS);
    }

    public UdpFloodProtector(int maxPacketsPerWindow, long windowMs) {
        if (maxPacketsPerWindow <= 0) {
            throw new IllegalArgumentException("maxPacketsPerWindow must be > 0");
        }
        if (windowMs <= 0) {
            throw new IllegalArgumentException("windowMs must be > 0");
        }
        this.maxPacketsPerWindow = maxPacketsPerWindow;
        this.windowMs = windowMs;
    }

    public boolean shouldAccept(InetAddress address) {
        if (address == null) {
            return false;
        }

        String ip = address.getHostAddress();
        if (blockedIps.contains(ip)) {
            return false;
        }

        PacketWindow window = packetWindows.computeIfAbsent(ip, ignored -> new PacketWindow());
        return window.tryAccept(maxPacketsPerWindow, windowMs);
    }

    public void blockIp(String ip) {
        if (ip != null && !ip.isBlank()) {
            blockedIps.add(ip.trim());
        }
    }

    public void unblockIp(String ip) {
        if (ip != null && !ip.isBlank()) {
            blockedIps.remove(ip.trim());
        }
    }

    public boolean isBlocked(String ip) {
        return ip != null && blockedIps.contains(ip.trim());
    }

    private static final class PacketWindow {
        private long windowStart = System.currentTimeMillis();
        private int packetCount;

        private synchronized boolean tryAccept(int maxPacketsPerWindow, long windowMs) {
            long now = System.currentTimeMillis();
            if (now - windowStart >= windowMs) {
                windowStart = now;
                packetCount = 0;
            }

            packetCount++;
            return packetCount <= maxPacketsPerWindow;
        }
    }
}
