package com.chrionline.service;

public final class RecaptchaConfig {

    private RecaptchaConfig() {}

    public static String getSiteKey() {
        return readConfig("CHRIONLINE_RECAPTCHA_SITE_KEY", "chrionline.recaptcha.siteKey");
    }

    public static String getSecretKey() {
        return readConfig("CHRIONLINE_RECAPTCHA_SECRET_KEY", "chrionline.recaptcha.secretKey");
    }

    public static boolean isClientConfigured() {
        String siteKey = getSiteKey();
        return siteKey != null && !siteKey.isBlank();
    }

    public static boolean isServerConfigured() {
        String secretKey = getSecretKey();
        return secretKey != null && !secretKey.isBlank();
    }

    private static String readConfig(String envKey, String propertyKey) {
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String envStyleProperty = System.getProperty(envKey);
        if (envStyleProperty != null && !envStyleProperty.isBlank()) {
            return envStyleProperty.trim();
        }
        String property = System.getProperty(propertyKey);
        if (property != null && !property.isBlank()) {
            return property.trim();
        }
        return null;
    }
}
