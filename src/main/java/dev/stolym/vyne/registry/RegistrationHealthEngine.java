/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.registry;

import dev.stolym.vyne.util.VyneLogger;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RegistrationHealthEngine {
    private static final String POLICY_WARN = "warn";
    private static final String POLICY_QUARANTINE = "quarantine";
    private static final String POLICY_STRICT = "strict";

    private RegistrationHealthEngine() {}

    public static void runPostRegistrationPolicy() {
        String raw = System.getProperty("vyne.registry.policy", POLICY_QUARANTINE).trim().toLowerCase(Locale.ROOT);
        String policy = switch (raw) {
            case POLICY_WARN, POLICY_QUARANTINE, POLICY_STRICT -> raw;
            default -> {
                VyneLogger.warn("[VyneHealth] Unknown vyne.registry.policy=" + raw + " — using " + POLICY_QUARANTINE);
                yield POLICY_QUARANTINE;
            }
        };

        double ratio = parseDouble("vyne.registry.quarantineRatio", 0.12);
        int minExpected = parseInt("vyne.registry.quarantineMinExpected", 24);
        int absoluteMissing = parseInt("vyne.registry.quarantineAbsoluteMissing", 12);
        int supplierMin = parseInt("vyne.registry.quarantineSupplierFailures", 6);
        int skippedMin = parseInt("vyne.registry.quarantineSkippedNull", 28);
        int skippedBatchMin = parseInt("vyne.registry.quarantineSkippedNullBatch", 14);
        int aggMissingMin = parseInt("vyne.registry.quarantineAggregatedMissing", 16);
        int aggMinExpected = parseInt("vyne.registry.quarantineAggregatedMinExpected", 32);

        Set<String> catastrophic = computeCatastrophicMods(ratio, minExpected, absoluteMissing, supplierMin, skippedMin,
            skippedBatchMin, aggMissingMin, aggMinExpected);
        if (catastrophic.isEmpty()) {
            VyneLogger.success("[VyneHealth] No mod crossed catastrophic registration thresholds — datapack cascade risk is low.");
            return;
        }

        VyneLogger.warn("[VyneHealth] Catastrophic registration stress detected for: "
            + String.join(", ", catastrophic)
            + " (policy=" + policy + "). See [RegDiag] above for details.");

        switch (policy) {
            case POLICY_WARN -> VyneLogger.warn("[VyneHealth] policy=warn — datapacks still load; expect registry/worldgen errors. "
                + "Set -Dvyne.registry.policy=quarantine to strip broken mods’ packs, or =strict to abort early.");
            case POLICY_QUARANTINE -> {
                dev.stolym.vyne.world.RegistryHealthPolicy.sessionQuarantineModDataPacks(catastrophic);
                VyneLogger.warn("[VyneHealth] Quarantined datapacks for mod id(s): " + String.join(", ", catastrophic)
                    + " — their built-in data will not load this session (dynamic, threshold-based).");
            }
            case POLICY_STRICT -> {
                String msg = buildFatalMessage(catastrophic);
                VyneLogger.failure("[VyneHealth] policy=strict — aborting: " + msg);
                throw new IllegalStateException(msg);
            }
            default -> {
            }
        }
    }

    private static Set<String> computeCatastrophicMods(double ratio, int minExpected, int absoluteMissing,
                                                      int supplierMin, int skippedMin,
                                                      int skippedBatchMin, int aggMissingMin, int aggMinExpected) {
        LinkedHashSet<String> out = new LinkedHashSet<>();

        for (RegistrationDiagnostics.ModCoverageExport c
                : RegistrationDiagnostics.exportCoverageLines()) {
            if (c.modId() == null || c.modId().isBlank() || RegistrationDiagnostics.isReservedPlatformNamespace(c.modId())) {
                continue;
            }
            if (c.missing() <= 0) {
                continue;
            }
            String path = c.registryPath();
            if (path == null) {
                continue;
            }
            boolean core = path.equals("minecraft:block") || path.equals("minecraft:item");
            if (!core) {
                continue;
            }
            if (c.missing() >= absoluteMissing) {
                out.add(c.modId());
                continue;
            }
            if (c.expected() >= minExpected && c.expected() > 0) {
                double r = c.missing() / (double) c.expected();
                if (r >= ratio) {
                    out.add(c.modId());
                }
            }
        }

        for (RegistrationDiagnostics.ModCoreAggregate a : RegistrationDiagnostics.exportAggregatedBlockItemCoverage()) {
            if (a.missingTotal() <= 0) {
                continue;
            }
            if (a.missingTotal() >= aggMissingMin) {
                out.add(a.modId());
                continue;
            }
            if (a.expectedTotal() >= aggMinExpected && a.expectedTotal() > 0) {
                double r = a.missingTotal() / (double) a.expectedTotal();
                if (r >= ratio) {
                    out.add(a.modId());
                }
            }
        }

        for (Map.Entry<String, Integer> e : RegistrationDiagnostics.supplierFailureCountsByModNamespace().entrySet()) {
            if (e.getValue() != null && e.getValue() >= supplierMin) {
                out.add(e.getKey());
            }
        }

        for (Map.Entry<String, Integer> e : RegistrationDiagnostics.skippedNullTotalsByMod().entrySet()) {
            if (e.getValue() != null && e.getValue() >= skippedMin) {
                out.add(e.getKey());
            }
        }

        for (Map.Entry<String, Integer> e : RegistrationDiagnostics.skippedNullMaxBatchByMod().entrySet()) {
            if (e.getValue() != null && e.getValue() >= skippedBatchMin) {
                out.add(e.getKey());
            }
        }

        return out;
    }

    private static String buildFatalMessage(Set<String> mods) {
        return "Vyne registration health (strict): catastrophic failure thresholds exceeded for mod(s): "
            + String.join(", ", mods)
            + ". Fix/remap those mods, remove them from the instance, or set -Dvyne.registry.policy=quarantine "
            + "to strip their datapacks instead of aborting.";
    }

    private static double parseDouble(String key, double def) {
        try {
            String s = System.getProperty(key);
            if (s == null || s.isBlank()) {
                return def;
            }
            return Double.parseDouble(s.trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static int parseInt(String key, int def) {
        try {
            String s = System.getProperty(key);
            if (s == null || s.isBlank()) {
                return def;
            }
            return Integer.parseInt(s.trim(), 10);
        } catch (Throwable t) {
            return def;
        }
    }
}
