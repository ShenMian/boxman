package my.boxman.compat;

import my.boxman.compat.graphics.PlatformGraphics;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ResourceLoader {
    private static final Map<String, BufferedImage> cache = new ConcurrentHashMap<>();

    public static BufferedImage getDrawable(String name) {
        if (name == null) return null;
        return cache.computeIfAbsent(name, PlatformGraphics::decodeResource);
    }

    public static BufferedImage getDrawable(int resId) {
        return null;
    }
}
