package com.chrionline.service;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Pure utility class for password hashing and validation.
 * <p>
 * BCrypt is ONLY called inside this class — never referenced directly from
 * anywhere else in the codebase.
 * <p>
 * Not instantiable.
 */
public final class PasswordUtils {

    /** Prevent instantiation. */
    private PasswordUtils() {
        throw new UnsupportedOperationException("PasswordUtils is a utility class");
    }

    // ── Hashing ─────────────────────────────────────────────────────────────

    /**
     * Hashes a plain-text password using BCrypt with a cost factor of 12.
     *
     * @param plainPassword the raw password (never stored or logged)
     * @return the BCrypt hash string (60 chars)
     */
    public static String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
    }

    // ── Verification ────────────────────────────────────────────────────────

    /**
     * Verifies a plain-text password against a stored BCrypt hash.
     *
     * @param plainPassword the candidate password
     * @param storedHash    the hash retrieved from the database
     * @return {@code true} if they match, {@code false} otherwise
     */
    public static boolean verify(String plainPassword, String storedHash) {
        return BCrypt.checkpw(plainPassword, storedHash);
    }

    // ── Strength check ──────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the password meets minimum strength requirements:
     * <ul>
     *   <li>At least 8 characters long</li>
     *   <li>Contains at least one letter</li>
     *   <li>Contains at least one digit</li>
     * </ul>
     *
     * @param plainPassword the password to check
     * @return {@code true} if strong enough
     */
    public static boolean isStrongEnough(String plainPassword) {
        if (plainPassword == null || plainPassword.length() < 8) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasDigit  = false;
        for (char c : plainPassword.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c))  hasDigit  = true;
            if (hasLetter && hasDigit) return true;
        }
        return false;
    }
}
