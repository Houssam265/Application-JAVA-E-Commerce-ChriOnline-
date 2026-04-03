package com.chrionline.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/**
 * Loads a local .env file into Java system properties for this process.
 */
public final class EnvFileLoader {

    private static final Logger LOG = Logger.getLogger(EnvFileLoader.class.getName());

    private EnvFileLoader() {}

    public static void loadFromProjectRoot() {
        load(Path.of(".env"));
    }

    public static void load(Path path) {
        if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int separator = line.indexOf('=');
                if (separator <= 0) {
                    continue;
                }

                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                if (key.isEmpty() || System.getenv(key) != null || System.getProperty(key) != null) {
                    continue;
                }

                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }

                System.setProperty(key, value);
            }
            LOG.info("[ENV] Loaded configuration from " + path.toAbsolutePath().normalize());
        } catch (IOException e) {
            LOG.warning("[ENV] Failed to load .env file: " + e.getMessage());
        }
    }
}
