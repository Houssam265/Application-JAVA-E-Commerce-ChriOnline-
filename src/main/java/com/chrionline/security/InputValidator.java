package com.chrionline.security;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Centralized input validation helpers for server-side request hardening.
 */
public final class InputValidator {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,30}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");
    private static final Pattern SHELL_META_PATTERN = Pattern.compile("[;|&`$()<>\\\\!#]");
    private static final Pattern SAFE_FILENAME_CHARS = Pattern.compile("[^A-Za-z0-9._-]");

    private InputValidator() {
    }

    public static String sanitize(String value, String fieldName) {
        if (value == null) {
            throw new ValidationException(fieldName + " is required.");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException(fieldName + " cannot be empty.");
        }
        if (SHELL_META_PATTERN.matcher(trimmed).find()) {
            throw new ValidationException(fieldName + " contains forbidden shell metacharacters.");
        }
        return trimmed;
    }

    public static int validateOrderId(String value) {
        String sanitized = sanitize(value, "order_id");
        if (!sanitized.matches("\\d{1,10}")) {
            throw new ValidationException("Invalid order ID: must be 1-10 digits.");
        }
        long parsed = Long.parseLong(sanitized);
        if (parsed < 1L || parsed > (long) Integer.MAX_VALUE) {
            throw new ValidationException("Invalid order ID: out of supported range.");
        }
        return (int) parsed;
    }

    public static String validateUsername(String value) {
        String sanitized = sanitize(value, "username");
        if (!USERNAME_PATTERN.matcher(sanitized).matches()) {
            throw new ValidationException("username must be 3-30 chars using letters, digits, '_' or '-'.");
        }
        return sanitized;
    }

    public static String validateEmail(String value) {
        String sanitized = sanitize(value, "email").toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(sanitized).matches()) {
            throw new ValidationException("email format is invalid.");
        }
        return sanitized;
    }

    public static int validateProductId(Integer value) {
        return validatePositiveInt(value, "product_id", 1, 1_000_000_000);
    }

    public static int validateQuantity(Integer value) {
        return validatePositiveInt(value, "quantity", 1, 10_000);
    }

    public static int validatePrice(Number value) {
        if (value == null) {
            throw new ValidationException("price is required.");
        }
        double price = value.doubleValue();
        if (Double.isNaN(price) || Double.isInfinite(price) || price < 0.0 || price > 1_000_000.0) {
            throw new ValidationException("price must be between 0 and 1000000.");
        }
        return (int) Math.round(price * 100.0d);
    }

    public static int validatePositiveInt(Integer value, String fieldName, int min, int max) {
        if (value == null) {
            throw new ValidationException(fieldName + " is required.");
        }
        String decimal = Integer.toString(value.intValue());
        if (!decimal.matches("^-?\\d+$")) {
            throw new ValidationException(fieldName + " must be a valid integer.");
        }
        long parsed;
        try {
            parsed = Long.parseLong(decimal);
        } catch (NumberFormatException e) {
            throw new ValidationException(fieldName + " must be a valid integer.");
        }
        if (parsed < (long) min || parsed > (long) max) {
            throw new ValidationException(fieldName + " must be between " + min + " and " + max + ".");
        }
        return value;
    }

    public static String validateFilename(String fileName) {
        String sanitized = sanitize(fileName, "filename");
        if (sanitized.contains("../") || sanitized.contains("..\\")) {
            throw new ValidationException("filename must not contain path traversal sequences.");
        }
        if (sanitized.startsWith("/") || sanitized.startsWith("\\")) {
            throw new ValidationException("filename must not be an absolute path.");
        }
        if (SAFE_FILENAME_CHARS.matcher(sanitized).find()) {
            throw new ValidationException("filename contains unsupported characters.");
        }
        return sanitized;
    }

    public static String sanitizeFileName(String fileName) {
        return validateFilename(fileName);
    }
}
