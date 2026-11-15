/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.world;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.core.VyneLauncher;
import dev.stolym.vyne.util.VyneLogger;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class WorldModGuard {
    private static final String SNAPSHOT_FILE = ".vyne-mod-snapshot.txt";
    private static final String WARNING_FILE = ".vyne-worldguard-warning.txt";

    private WorldModGuard() {}

    public static void verifyAndSnapshot(Object server) {
        try {
            Path worldRoot = resolveWorldRoot(server);
            if (worldRoot == null) {
                VyneLogger.debug("[WorldGuard] Could not resolve world root path; skipping snapshot.");
                return;
            }
            Files.createDirectories(worldRoot);

            Set<String> currentMods = currentModIds();
            Path snapshotPath = worldRoot.resolve(SNAPSHOT_FILE);
            Set<String> previousMods = readSnapshot(snapshotPath);
            Set<String> missingMods = new LinkedHashSet<>(previousMods);
            missingMods.removeAll(currentMods);

            if (!missingMods.isEmpty()) {
                String msg = "[WorldGuard] Missing previously-present mods for world '"
                    + worldRoot.getFileName() + "': " + String.join(", ", missingMods)
                    + ". Reinstall them to avoid missing blocks/entities.";
                VyneLogger.warn(msg);
                writeWarning(worldRoot.resolve(WARNING_FILE), msg, missingMods);
            }

            writeSnapshot(snapshotPath, currentMods);
            VyneLogger.info("[WorldGuard] Snapshot saved for world '" + worldRoot.getFileName()
                + "' (" + currentMods.size() + " mods).");
        } catch (Throwable t) {
            VyneLogger.warn("[WorldGuard] snapshot check failed: " + t);
        }
    }

    private static Set<String> currentModIds() {
        Set<String> out = new LinkedHashSet<>();
        for (DetectedMod mod : VyneLauncher.getDetectedMods()) {
            if (mod != null && mod.getModId() != null && !mod.getModId().isBlank()) {
                out.add(mod.getModId());
            }
        }
        return out;
    }

    private static Set<String> readSnapshot(Path snapshotPath) {
        try {
            if (!Files.exists(snapshotPath)) return new LinkedHashSet<>();
            List<String> lines = Files.readAllLines(snapshotPath);
            Set<String> out = new LinkedHashSet<>();
            for (String line : lines) {
                String id = line == null ? "" : line.trim();
                if (id.isEmpty() || id.startsWith("#")) continue;
                out.add(id);
            }
            return out;
        } catch (IOException ignored) {
            return new LinkedHashSet<>();
        }
    }

    private static void writeSnapshot(Path snapshotPath, Set<String> mods) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# Vyne world snapshot");
        lines.add("# One mod id per line");
        lines.addAll(mods);
        Files.write(snapshotPath, lines,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static void writeWarning(Path warningPath, String msg, Set<String> missingMods) {
        try {
            List<String> lines = new ArrayList<>();
            lines.add("Vyne World Guard Warning");
            lines.add(msg);
            lines.add("");
            lines.add("Missing mod ids:");
            for (String id : missingMods) {
                lines.add(" - " + id);
            }
            Files.write(warningPath, lines,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException ignored) {
        }
    }

    private static Path resolveWorldRoot(Object server) {
        if (server == null) return null;
        try {
            for (Method m : server.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                if (!Path.class.isAssignableFrom(m.getReturnType())) continue;
                String name = m.getName();
                if (!name.contains("Path") && !name.contains("path") && !name.contains("World")) {
                    continue;
                }
                Object out = m.invoke(server);
                if (out instanceof Path p && looksLikeWorldRoot(p)) {
                    return p;
                }
            }

            for (java.lang.reflect.Field f : server.getClass().getDeclaredFields()) {
                if (!Path.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object out = f.get(server);
                if (out instanceof Path p && looksLikeWorldRoot(p)) {
                    return p;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean looksLikeWorldRoot(Path path) {
        if (path == null) return false;
        try {
            if (Files.exists(path.resolve("level.dat"))) return true;
            Path name = path.getFileName();
            if (name == null) return false;
            String n = name.toString().toLowerCase();
            return n.contains("world") || n.contains("save");
        } catch (Throwable ignored) {
            return false;
        }
    }
}
