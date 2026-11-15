/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.server;

import dev.stolym.vyne.util.VyneLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;

public final class ServerLagMitigationConfig {
    public static final Path CONFIG_PATH = Paths.get("config", "vyne", "lag-mitigation.properties");

    private static volatile boolean loaded;
    private static volatile long lastReloadMs;
    private static volatile long fileMtime;

    private static volatile boolean enabledFromFile;
    private static volatile long triggerTickTimeMs = 500;
    private static volatile int maxDiscardsPerSpike = 8;
    private static volatile long cooldownMs = 10_000;
    private static volatile long minEntityTickNanos = 100_000L;

    private ServerLagMitigationConfig() {}

    public static boolean shouldSampleEntityTicks() {
        reloadIfStale();
        return enabled();
    }

    public static boolean enabled() {
        reloadIfStale();
        String sp = System.getProperty("vyne.server.lagMitigation");
        if (sp != null) {
            return Boolean.parseBoolean(sp.trim());
        }
        return enabledFromFile;
    }

    public static long triggerTickTimeMs() {
        reloadIfStale();
        return Math.max(1, triggerTickTimeMs);
    }

    public static int maxDiscardsPerSpike() {
        reloadIfStale();
        return Math.max(1, maxDiscardsPerSpike);
    }

    public static long cooldownMs() {
        reloadIfStale();
        return Math.max(0, cooldownMs);
    }

    public static long minEntityTickNanos() {
        reloadIfStale();
        return Math.max(0, minEntityTickNanos);
    }

    private static void reloadIfStale() {
        long now = System.currentTimeMillis();
        if (loaded && now - lastReloadMs < 2000) {
            return;
        }
        lastReloadMs = now;
        try {
            Path dir = CONFIG_PATH.getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            if (!Files.isRegularFile(CONFIG_PATH)) {
                writeDefaults();
            }
            long mtime = Files.getLastModifiedTime(CONFIG_PATH).toMillis();
            if (loaded && mtime == fileMtime) {
                return;
            }
            fileMtime = mtime;
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                p.load(in);
            }
            enabledFromFile = parseBool(p.getProperty("enabled"), false);
            triggerTickTimeMs = parseLong(p.getProperty("triggerTickTimeMs"), 500);
            maxDiscardsPerSpike = (int) parseLong(p.getProperty("maxDiscardsPerSpike"), 8);
            cooldownMs = parseLong(p.getProperty("cooldownMs"), 10_000);
            minEntityTickNanos = parseLong(p.getProperty("minEntityTickNanos"), 100_000);
            loaded = true;
        } catch (IOException e) {
            VyneLogger.debug("[LagMitigation] config load failed: " + e.getMessage());
            enabledFromFile = false;
            loaded = true;
        }
    }

    private static void writeDefaults() throws IOException {
        String content = """
            # Vyne server lag mitigation — OFF by default.
            # When a single server tick exceeds triggerTickTimeMs, the slowest non-player entities
            # sampled during that tick may be removed (cooldown applies).
            enabled=false
            triggerTickTimeMs=500
            maxDiscardsPerSpike=8
            cooldownMs=10000
            minEntityTickNanos=100000
            """;
        try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        VyneLogger.info("[LagMitigation] Wrote default config: " + CONFIG_PATH);
    }

    private static boolean parseBool(String s, boolean def) {
        if (s == null) {
            return def;
        }
        String t = s.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(t) || "yes".equals(t) || "1".equals(t)) {
            return true;
        }
        if ("false".equals(t) || "no".equals(t) || "0".equals(t)) {
            return false;
        }
        return def;
    }

    private static long parseLong(String s, long def) {
        if (s == null) {
            return def;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
