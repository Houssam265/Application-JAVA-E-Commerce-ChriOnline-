package com.chrionline.ui;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

public final class OrderDisplayUtil {

    private OrderDisplayUtil() {
    }

    public static String toDisplayNumber(String orderId) {
        if (orderId == null) return "—";
        String normalized = orderId.trim();
        if (normalized.isBlank()) return "—";
        if (normalized.chars().allMatch(Character::isDigit)) return normalized;

        CRC32 crc32 = new CRC32();
        crc32.update(normalized.getBytes(StandardCharsets.UTF_8));
        long value = crc32.getValue() % 1_000_000_000L;
        if (value <= 0) value += 1_000_000L;
        return String.valueOf(value);
    }

    public static String formatLabel(String orderId) {
        return "Order #" + toDisplayNumber(orderId);
    }
}
