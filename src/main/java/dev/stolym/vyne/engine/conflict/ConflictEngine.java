/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.conflict;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.util.VyneLogger;
import dev.stolym.vyne.util.VersionConstraintMatcher;

import java.util.*;
import java.util.stream.Collectors;

public final class ConflictEngine {
    private final List<DetectedMod> mods;

    public ConflictEngine(List<DetectedMod> mods) {
        this.mods = mods;
    }

    public void analyse() {
        VyneLogger.info("ConflictEngine: Starting dependency and compatibility analysis...");
        resolveVersionConflicts();
        checkCrossLoaderOverlaps();
        VyneLogger.success("Conflict analysis complete.");
    }

    public List<DetectedMod> getResolvedMods() {
        Map<String, DetectedMod> resolved = new LinkedHashMap<>();

        Map<String, List<DetectedMod>> byId = new LinkedHashMap<>();
        for (DetectedMod mod : mods) {
            byId.computeIfAbsent(mod.getModId(), k -> new ArrayList<>()).add(mod);
        }

        Map<String, String> wanted = new HashMap<>();
        for (DetectedMod mod : mods) {
            for (Map.Entry<String, String> dep : mod.getDependencies().entrySet()) {
                String depId = dep.getKey();
                String constraint = dep.getValue();
                if (constraint != null && !constraint.equals("*") && !constraint.isBlank()) {
                    wanted.merge(depId, constraint, (a, b) -> b);
                }
            }
        }

        for (Map.Entry<String, List<DetectedMod>> entry : byId.entrySet()) {
            String id = entry.getKey();
            List<DetectedMod> candidates = entry.getValue();
            if (candidates.size() == 1) {
                resolved.put(id, candidates.get(0));
                continue;
            }
            String constraint = wanted.get(id);
            DetectedMod chosen = null;
            if (constraint != null) {
                for (DetectedMod candidate : candidates) {
                    if (versionMatches(candidate.getVersion(), constraint)) {
                        chosen = candidate;
                        break;
                    }
                }
            }
            if (chosen == null) {
            chosen = candidates.stream()
                .max(Comparator.comparing((DetectedMod m) -> VersionConstraintMatcher.parseVersionForSort(m.getVersion()),
                    Comparator.comparingInt((int[] a) -> a.length > 0 ? a[0] : 0)
                        .thenComparingInt(a -> a.length > 1 ? a[1] : 0)
                        .thenComparingInt(a -> a.length > 2 ? a[2] : 0)))
                .orElse(candidates.get(0));
        }
            DetectedMod finalChosen = chosen;
            candidates.stream()
                .filter(c -> c != finalChosen)
                .forEach(c -> VyneLogger.warn("[ConflictEngine] Skipping " + id + " v" + c.getVersion()
                    + " (" + c.getJarPath().getFileName() + ") — using v" + finalChosen.getVersion()
                    + " (constraint: " + (constraint != null ? constraint : "none, picked latest") + ")"));
            resolved.put(id, chosen);
        }

        return new ArrayList<>(resolved.values());
    }

    private void resolveVersionConflicts() {
        Map<String, List<DetectedMod>> byId = new HashMap<>();
        for (DetectedMod mod : mods) {
            byId.computeIfAbsent(mod.getModId(), k -> new ArrayList<>()).add(mod);
        }
        for (Map.Entry<String, List<DetectedMod>> entry : byId.entrySet()) {
            if (entry.getValue().size() > 1) {
                VyneLogger.info("[ConflictEngine] Multiple versions of '" + entry.getKey() + "' detected — will resolve.");
            }
        }
    }

    private boolean versionMatches(String version, String constraint) {
        return VersionConstraintMatcher.matches(version, constraint);
    }

    private void checkCrossLoaderOverlaps() {
        Set<String> fabricIds = new HashSet<>();
        Set<String> forgeIds = new HashSet<>();

        for (DetectedMod mod : mods) {
            if (mod.getLoaderType() == DetectedMod.LoaderType.FABRIC) fabricIds.add(mod.getModId());
            if (mod.getLoaderType() == DetectedMod.LoaderType.FORGE || mod.getLoaderType() == DetectedMod.LoaderType.NEOFORGE) forgeIds.add(mod.getModId());
        }

        if (fabricIds.contains("sodium") && (forgeIds.contains("rubidium") || forgeIds.contains("embeddium"))) {
            VyneLogger.warn("[Conflict] Both Sodium (Fabric) and a Forge equivalent (Rubidium/Embeddium) detected!");
            VyneLogger.warn("           This will cause double-rendering and crashes. Keep only one.");
        }
    }
}
