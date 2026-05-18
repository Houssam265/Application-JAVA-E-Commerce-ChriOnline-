package com.chrionline.ui;

import javafx.scene.image.Image;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared JavaFX image cache.
 *
 * Keeps original image quality while avoiding repeated disk/network decoding
 * when the same product image is shown in catalogue, details, cart, or admin.
 */
public final class ImageCache {

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private ImageCache() {
    }

    public static Image get(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return CACHE.computeIfAbsent(url, key -> new Image(key, true));
    }

    public static void clear() {
        CACHE.clear();
    }
}
