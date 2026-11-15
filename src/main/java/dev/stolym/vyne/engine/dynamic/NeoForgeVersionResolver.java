/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.util.VyneLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class NeoForgeVersionResolver {
    public record ResolutionResult(
        String selectedVersion,
        List<String> incompatibleModIds
    ) {}

    private NeoForgeVersionResolver() {}

    public static ResolutionResult resolve(List<DetectedMod> mods, String preferredVersion) {
        List<String> incompatible = new ArrayList<>();

        for (DetectedMod mod : mods) {
            if (mod.getLoaderType() == DetectedMod.LoaderType.FORGE) {
                continue;
            }

            String range = mod.getDependencies().get("neoforge");

            if (range != null && !satisfies(preferredVersion, range)) {
                VyneLogger.warn("[VersionResolver] Mod " + mod.getModId() + " requires NeoForge " + range 
                    + ", but Vyne is using " + preferredVersion + ". Mod will be disabled to prevent crashes.");
                incompatible.add(mod.getModId());
            }
        }

        return new ResolutionResult(preferredVersion, incompatible);
    }

    public static boolean satisfies(String version, String range) {
        if (range == null || range.equals("*") || range.isEmpty()) return true;

        try {
            if (!range.contains("[") && !range.contains("(")) {
                return version.equals(range);
            }

            boolean inclusiveMin = range.startsWith("[");
            boolean inclusiveMax = range.endsWith("]");

            String inner = range.substring(1, range.length() - 1);
            String[] parts = inner.split(",", -1);

            if (parts.length == 1) {
                return version.equals(parts[0].trim());
            }

            String min = parts[0].trim();
            String max = parts[1].trim();

            if (!min.isEmpty()) {
                int cmp = compareVersions(version, min);
                if (inclusiveMin) {
                    if (cmp < 0) return false;
                } else {
                    if (cmp <= 0) return false;
                }
            }

            if (!max.isEmpty()) {
                int cmp = compareVersions(version, max);
                if (inclusiveMax) {
                    if (cmp > 0) return false;
                } else {
                    if (cmp >= 0) return false;
                }
            }

            return true;
        } catch (Exception e) {
            VyneLogger.warn("[VersionResolver] Failed to parse version range: " + range);
            return true;
        }
    }

    private static int compareVersions(String v1, String v2) {
        String[] p1 = v1.split("\\.");
        String[] p2 = v2.split("\\.");
        int length = Math.max(p1.length, p2.length);
        for (int i = 0; i < length; i++) {
            int part1 = i < p1.length ? parsePart(p1[i]) : 0;
            int part2 = i < p2.length ? parsePart(p2[i]) : 0;
            if (part1 < part2) return -1;
            if (part1 > part2) return 1;
        }
        return 0;
    }

    private static int parsePart(String part) {
        try {
            String numeric = part.split("-")[0].replaceAll("[^0-9]", "");
            return numeric.isEmpty() ? 0 : Integer.parseInt(numeric);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
