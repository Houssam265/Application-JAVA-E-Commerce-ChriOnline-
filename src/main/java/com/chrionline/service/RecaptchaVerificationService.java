package com.chrionline.service;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class RecaptchaVerificationService {

    private static final URI VERIFY_URI = URI.create("https://www.google.com/recaptcha/api/siteverify");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public boolean isConfigured() {
        return RecaptchaConfig.isServerConfigured();
    }

    public VerificationResult verify(String token, String remoteIp) {
        if (!isConfigured()) {
            return VerificationResult.disabled();
        }
        if (token == null || token.isBlank()) {
            return VerificationResult.failure("missing-input-response");
        }

        String secret = RecaptchaConfig.getSecretKey();
        StringBuilder body = new StringBuilder();
        body.append("secret=").append(encode(secret));
        body.append("&response=").append(encode(token));
        if (remoteIp != null && !remoteIp.isBlank()) {
            body.append("&remoteip=").append(encode(remoteIp));
        }

        HttpRequest request = HttpRequest.newBuilder(VERIFY_URI)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JSONObject json = new JSONObject(response.body());
            boolean success = json.optBoolean("success", false);
            String errorCodes = json.has("error-codes") ? json.getJSONArray("error-codes").toString() : "";
            return success
                    ? VerificationResult.success()
                    : VerificationResult.failure(errorCodes.isBlank() ? "recaptcha-verification-failed" : errorCodes);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return VerificationResult.failure("recaptcha-verification-unavailable");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public static final class VerificationResult {
        private final boolean success;
        private final boolean disabled;
        private final String message;

        private VerificationResult(boolean success, boolean disabled, String message) {
            this.success = success;
            this.disabled = disabled;
            this.message = message;
        }

        public static VerificationResult success() {
            return new VerificationResult(true, false, "");
        }

        public static VerificationResult disabled() {
            return new VerificationResult(true, true, "");
        }

        public static VerificationResult failure(String message) {
            return new VerificationResult(false, false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isDisabled() {
            return disabled;
        }

        public String getMessage() {
            return message;
        }
    }
}
