/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.server;

import dev.stolym.vyne.util.VyneLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ServerPropertiesResilience {
    public static final int VYNE_MIN_MAX_TICK_MS = 120_000;

    private ServerPropertiesResilience() {}

    public static void ensureMaxTickTime(Path gameDirectory) {
        if (Boolean.parseBoolean(System.getProperty("vyne.server.skipPropertiesPatch", "false"))) {
            return;
        }
        Path props = gameDirectory.resolve("server.properties");
        if (!Files.isRegularFile(props)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(props, StandardCharsets.UTF_8);
            int idx = -1;
            int current = -1;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                if (!"max-tick-time".equals(key)) {
                    continue;
                }
                idx = i;
                String val = line.substring(eq + 1).trim();
                try {
                    current = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {
                    current = -1;
                }
                break;
            }
            int target = VYNE_MIN_MAX_TICK_MS;
            if (current >= 0) {
                target = Math.max(current, VYNE_MIN_MAX_TICK_MS);
            }
            if (current >= 0 && current == target && idx >= 0) {
                return;
            }
            List<String> out = new ArrayList<>(lines);
            String entry = "max-tick-time=" + target;
            if (idx >= 0) {
                out.set(idx, entry);
                VyneLogger.info("[ServerResilience] Raised max-tick-time from " + current + "ms to " + target
                    + "ms in server.properties (Vyne watchdog headroom).");
            } else {
                out.add("");
                out.add("# Vyne: minimum max-tick-time for recovery headroom (override with higher value)");
                out.add(entry);
                VyneLogger.info("[ServerResilience] Added max-tick-time=" + target
                    + "ms to server.properties (Vyne watchdog headroom).");
            }
            Files.write(props, out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            VyneLogger.warn("[ServerResilience] Could not adjust server.properties max-tick-time: " + e);
        }
    }
}
