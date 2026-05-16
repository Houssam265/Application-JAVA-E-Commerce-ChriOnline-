package com.chrionline.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class PaymentCardCrypto {
    private static final String KEY_ENV = "CHRIONLINE_CARD_AES_KEY";
    private static final String DEFAULT_DEMO_SECRET = "ChriOnline-MiniProjet2-card-storage-demo-key";

    private PaymentCardCrypto() {}

    public static AESUtil.Sealed encryptCardNumber(String cardNumber) {
        try {
            return AESUtil.encrypt(storageKey(), cardNumber);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de chiffrer la carte bancaire.", e);
        }
    }

    public static String decryptCardNumber(String encryptedCardNumber, String ivBase64) {
        try {
            return AESUtil.decrypt(storageKey(), encryptedCardNumber, ivBase64);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de dechiffrer la carte bancaire.", e);
        }
    }

    private static SecretKey storageKey() throws Exception {
        String configured = System.getenv(KEY_ENV);
        String material = configured == null || configured.isBlank() ? DEFAULT_DEMO_SECRET : configured.trim();
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(hash, AESUtil.ALGORITHM);
    }

    public static String detectBrand(String digits) {
        if (digits == null || digits.isBlank()) return "CARD";
        if (digits.startsWith("4")) return "VISA";
        if (digits.matches("^(5[1-5]|2[2-7]).*")) return "MASTERCARD";
        if (digits.startsWith("34") || digits.startsWith("37")) return "AMEX";
        if (digits.startsWith("6011") || digits.startsWith("65")) return "DISCOVER";
        return "CARD";
    }

    public static String last4(String digits) {
        if (digits == null || digits.length() < 4) return "";
        return digits.substring(digits.length() - 4);
    }

    public static String normalizedExpiry(String expiry) {
        return expiry == null ? "" : expiry.trim().toUpperCase(Locale.ROOT);
    }
}
