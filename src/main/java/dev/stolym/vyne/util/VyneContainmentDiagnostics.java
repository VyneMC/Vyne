/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class VyneContainmentDiagnostics {
    private VyneContainmentDiagnostics() {}

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public static Path record(Throwable t, String channel) {
        try {
            Path dir = containmentDir();
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(TS);
            String base = "absorbed-" + stamp + "-" + safeChannel(channel);
            Path out = dir.resolve(base + ".log");
            StringWriter sw = new StringWriter();
            sw.append("channel=").append(channel).append('\n');
            sw.append("time=").append(LocalDateTime.now().toString()).append('\n');
            sw.append("--- summary ---\n");
            sw.append(VyneFabricCrashFormat.formatDeveloperSummary(t)).append('\n');
            sw.append("--- full trace ---\n");
            t.printStackTrace(new PrintWriter(sw));
            Files.writeString(out, sw.toString(), StandardCharsets.UTF_8);
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Path containmentDir() {
        String gd = System.getProperty("vyne.gameDir");
        if (gd != null && !gd.isEmpty()) {
            return Paths.get(gd, "vyne", "containment");
        }
        return Paths.get("vyne", "containment").toAbsolutePath().normalize();
    }

    private static String safeChannel(String channel) {
        if (channel == null) {
            return "unknown";
        }
        return channel.replace(' ', '_').replace('/', '_').replace('\\', '_');
    }
}
