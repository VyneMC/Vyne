/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl;

import dev.stolym.vyne.util.VyneLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RemapCache {
    public static final int CACHE_SCHEMA = 3;

    private static volatile String mappingsFingerprint = "init";
    private static final Map<String, String> JAR_SHA_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> MEMORY = new ConcurrentHashMap<>();
    private static volatile Path cacheRoot;

    private RemapCache() {}

    public static void setMappingsFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) return;
        if (fingerprint.equals(mappingsFingerprint)) return;
        mappingsFingerprint = fingerprint;
        MEMORY.clear();
    }

    public static void clearMemory() {
        MEMORY.clear();
    }

    private static Path root() {
        Path r = cacheRoot;
        if (r == null) {
            String gameDir = System.getProperty("vyne.gameDir");
            Path base = (gameDir != null && !gameDir.isBlank())
                ? Path.of(gameDir)
                : Path.of(System.getProperty("user.dir", "."));
            r = base.toAbsolutePath().normalize()
                    .resolve(".vyne").resolve("cache").resolve("remap");
            cacheRoot = r;
        }
        return r;
    }

    public static void setCacheRoot(Path path) {
        cacheRoot = path;
        MEMORY.clear();
    }

    private static Path bucket(String jarSha256) {
        String mode = dev.stolym.vyne.core.VyneMode.patchedGame() ? "-p" + dev.stolym.vyne.convert.Routing.fingerprint() : "";
        return root().resolve(CACHE_SCHEMA + "-" + mappingsFingerprint + mode).resolve(jarSha256);
    }

    public static byte[] get(String jarSha256, String internalName) {
        if (jarSha256 == null || internalName == null) return null;
        String key = jarSha256 + '/' + internalName;
        byte[] mem = MEMORY.get(key);
        if (mem != null) return mem;
        Path file = bucket(jarSha256).resolve(internalName + ".class");
        if (!Files.isRegularFile(file)) return null;
        try {
            byte[] bytes = Files.readAllBytes(file);
            MEMORY.put(key, bytes);
            return bytes;
        } catch (IOException e) {
            return null;
        }
    }

    public static void put(String jarSha256, String internalName, byte[] transformed) {
        if (jarSha256 == null || internalName == null || transformed == null) return;
        String key = jarSha256 + '/' + internalName;
        MEMORY.put(key, transformed);
        Path file = bucket(jarSha256).resolve(internalName + ".class");
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, transformed);
        } catch (IOException e) {
            VyneLogger.debug("[RemapCache] Failed to store " + internalName + ": " + e.getMessage());
        }
    }

    public static String jarSha256(Path jar) {
        if (jar == null) return null;
        String key = jar.toAbsolutePath().normalize().toString();
        return JAR_SHA_CACHE.computeIfAbsent(key, k -> {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                try (InputStream is = Files.newInputStream(jar)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) != -1) md.update(buf, 0, n);
                }
                byte[] digest = md.digest();
                StringBuilder sb = new StringBuilder(digest.length * 2);
                for (byte b : digest) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (IOException | NoSuchAlgorithmException e) {
                return null;
            }
        });
    }

    public static String computeMappingsFingerprint(dev.stolym.vyne.mappings.MappingComposer.ComposedMappings m) {
        if (m == null) return "empty";
        return (m.classMap().size() + "-" + m.methodMap().size() + "-" + m.fieldMap().size());
    }
}
