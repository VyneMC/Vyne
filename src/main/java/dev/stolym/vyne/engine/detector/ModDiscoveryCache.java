/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.detector;

import dev.stolym.vyne.util.VyneLogger;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class ModDiscoveryCache {
    private static final Path CACHE_FILE = Paths.get("config", "vyne", "discovery_cache.bin");
    private static final Map<String, CacheEntry> cache = new HashMap<>();
    private static boolean modified = false;

    public record CacheEntry(long timestamp, long size, List<String> classes) implements Serializable {}

    static {
        load();
    }

    public static List<String> getCachedClasses(String modId, Path jarPath) {
        CacheEntry entry = cache.get(modId);
        if (entry != null) {
            try {
                if (entry.timestamp == Files.getLastModifiedTime(jarPath).toMillis() && entry.size == Files.size(jarPath)) {
                    return entry.classes;
                }
            } catch (IOException ignored) {}
        }
        return null;
    }

    public static void updateCache(String modId, Path jarPath, List<String> classes) {
        try {
            cache.put(modId, new CacheEntry(
                Files.getLastModifiedTime(jarPath).toMillis(),
                Files.size(jarPath),
                classes
            ));
            modified = true;
        } catch (IOException e) {
            VyneLogger.warn("[Cache] Failed to update cache for " + modId + ": " + e.getMessage());
        }
    }

    public static void save() {
        if (!modified) return;
        try {
            Files.createDirectories(CACHE_FILE.getParent());
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CACHE_FILE.toFile()))) {
                oos.writeObject(cache);
            }
            modified = false;
            VyneLogger.info("[Cache] Discovery cache saved successfully.");
        } catch (IOException e) {
            VyneLogger.error("[Cache] Failed to save discovery cache", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void load() {
        if (!Files.exists(CACHE_FILE)) return;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(CACHE_FILE.toFile()))) {
            Map<String, CacheEntry> loaded = (Map<String, CacheEntry>) ois.readObject();
            cache.putAll(loaded);
            VyneLogger.info("[Cache] Loaded " + cache.size() + " cached mod discovery entries.");
        } catch (Exception e) {
            VyneLogger.warn("[Cache] Failed to load discovery cache: " + e.getMessage());
        }
    }
}
