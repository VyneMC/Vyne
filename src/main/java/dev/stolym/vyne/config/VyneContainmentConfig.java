/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.config;

import dev.stolym.vyne.util.VyneLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class VyneContainmentConfig {
    private VyneContainmentConfig() {}

    private static final Path CONFIG_FILE = Paths.get(".vyne", "vyne.properties");
    private static volatile Properties props = null;

    private static final String KEY_SNAPSHOT_ENABLED   = "snapshot.enabled";
    private static final String KEY_SNAPSHOT_KEEP      = "snapshot.keepCount";
    private static final String KEY_GATE_ENABLED       = "gate.enabled";
    private static final String KEY_TRIAGE_ENABLED     = "triage.enabled";
    private static final String KEY_REPORT_DIR         = "report.directory";
    private static final String KEY_TIER3_SHUTDOWN     = "triage.tier3.cleanShutdown";
    private static final String KEY_VERBOSE_FREEZE_LOG = "gate.verboseFreezeLog";
    private static final String KEY_NEOFORGE_VERSION   = "neoforge.version";

    public static String getNeoForgeVersion() {
        return get(KEY_NEOFORGE_VERSION, "21.1.233");
    }

    public static boolean isSnapshotEnabled() {
        return getBool(KEY_SNAPSHOT_ENABLED, true);
    }

    public static int getSnapshotKeepCount() {
        return getInt(KEY_SNAPSHOT_KEEP, 3);
    }

    public static boolean isGateEnabled() {
        return getBool(KEY_GATE_ENABLED, true);
    }

    public static boolean isTriageEnabled() {
        return getBool(KEY_TRIAGE_ENABLED, true);
    }

    public static Path getReportDirectory() {
        String dir = get(KEY_REPORT_DIR, ".vyne/reports");
        return Paths.get(dir);
    }

    public static boolean isTier3ShutdownEnabled() {
        return getBool(KEY_TIER3_SHUTDOWN, true);
    }

    public static boolean isVerboseFreezeLogEnabled() {
        return getBool(KEY_VERBOSE_FREEZE_LOG, false);
    }

    public static void load() {
        props = new Properties();
        if (Files.exists(CONFIG_FILE)) {
            try (var in = Files.newInputStream(CONFIG_FILE)) {
                props.load(in);
                VyneLogger.info("[VyneConfig] Loaded containment config from " + CONFIG_FILE);
            } catch (IOException e) {
                VyneLogger.warn("[VyneConfig] Failed to load config, using defaults: " + e.getMessage());
            }
        } else {
            writeDefaults();
        }
    }

    private static void writeDefaults() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Properties defaults = new Properties();
            defaults.setProperty(KEY_SNAPSHOT_ENABLED,   "true");
            defaults.setProperty(KEY_SNAPSHOT_KEEP,      "3");
            defaults.setProperty(KEY_GATE_ENABLED,       "true");
            defaults.setProperty(KEY_TRIAGE_ENABLED,     "true");
            defaults.setProperty(KEY_REPORT_DIR,         ".vyne/reports");
            defaults.setProperty(KEY_TIER3_SHUTDOWN,     "true");
            defaults.setProperty(KEY_VERBOSE_FREEZE_LOG, "false");
            defaults.setProperty(KEY_NEOFORGE_VERSION,   "21.1.233");
            try (var out = Files.newOutputStream(CONFIG_FILE)) {
                defaults.store(out,
                    "Vyne Containment Config\n"
                    + "# snapshot.enabled     - Take startup world snapshots (true/false)\n"
                    + "# snapshot.keepCount   - Number of snapshots to keep (integer)\n"
                    + "# gate.enabled         - Enable execution gate (true/false)\n"
                    + "# triage.enabled       - Enable triage system (true/false)\n"
                    + "# report.directory     - Where to write vyne-report files\n"
                    + "# triage.tier3.cleanShutdown - Shutdown on Tier 3 failure (true/false)\n"
                    + "# gate.verboseFreezeLog - Log every blocked execution (true/false)\n"
                    + "# neoforge.version     - NeoForge version for reference bridging (string)");
            }
            props = defaults;
            VyneLogger.info("[VyneConfig] Created default config at " + CONFIG_FILE);
        } catch (IOException e) {
            VyneLogger.warn("[VyneConfig] Could not write default config: " + e.getMessage());
            props = new Properties();
        }
    }

    private static Properties props() {
        if (props == null) load();
        return props;
    }

    private static String get(String key, String defaultValue) {
        return props().getProperty(key, defaultValue);
    }

    private static boolean getBool(String key, boolean defaultValue) {
        String v = props().getProperty(key);
        if (v == null) return defaultValue;
        return "true".equalsIgnoreCase(v.trim());
    }

    private static int getInt(String key, int defaultValue) {
        String v = props().getProperty(key);
        if (v == null) return defaultValue;
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }
}
