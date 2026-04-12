package com.chrionline.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory CAPTCHA challenge store for the login screen.
 */
public final class LoginCaptchaService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CAPTCHA_LENGTH = 6;
    private static final long CAPTCHA_TTL_SECONDS = 120;

    private static final LoginCaptchaService INSTANCE = new LoginCaptchaService();

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();

    private LoginCaptchaService() {}

    public static LoginCaptchaService getInstance() {
        return INSTANCE;
    }

    public CaptchaChallenge issueChallenge(String clientIp) {
        cleanupExpired();
        String captchaText = generateCaptchaText();
        String challengeId = UUID.randomUUID().toString();
        long expiresAtEpochMs = Instant.now().plusSeconds(CAPTCHA_TTL_SECONDS).toEpochMilli();
        challenges.put(challengeId, new Challenge(challengeId, captchaText, normalizeIp(clientIp), expiresAtEpochMs));
        return new CaptchaChallenge(challengeId, captchaText, CAPTCHA_TTL_SECONDS);
    }

    public ValidationResult validate(String challengeId, String answer, String clientIp) {
        cleanupExpired();
        if (challengeId == null || challengeId.isBlank()) {
            return ValidationResult.failure("challenge manquant");
        }
        if (answer == null || answer.isBlank()) {
            return ValidationResult.failure("reponse manquante");
        }

        Challenge challenge = challenges.remove(challengeId);
        if (challenge == null) {
            return ValidationResult.failure("captcha expire ou introuvable");
        }
        if (challenge.expiresAtEpochMs < System.currentTimeMillis()) {
            return ValidationResult.failure("captcha expire");
        }
        String normalizedIp = normalizeIp(clientIp);
        if (challenge.clientIp != null && normalizedIp != null && !challenge.clientIp.equals(normalizedIp)) {
            return ValidationResult.failure("captcha non valide pour cette adresse IP");
        }
        if (!challenge.answer.equalsIgnoreCase(answer.trim())) {
            return ValidationResult.failure("texte incorrect");
        }
        return ValidationResult.success();
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        challenges.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochMs < now);
    }

    private String generateCaptchaText() {
        StringBuilder sb = new StringBuilder(CAPTCHA_LENGTH);
        for (int i = 0; i < CAPTCHA_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private String normalizeIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return null;
        }
        return clientIp.trim();
    }

    private record Challenge(String id, String answer, String clientIp, long expiresAtEpochMs) {}

    public record CaptchaChallenge(String challengeId, String captchaText, long expiresInSeconds) {}

    public static final class ValidationResult {
        private final boolean success;
        private final String message;

        private ValidationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, "");
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
