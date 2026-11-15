/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.registry;

import dev.stolym.vyne.util.ReflectionHelper;
import dev.stolym.vyne.util.VyneLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class RegistrationDiagnostics {
    private static final Object LOCK = new Object();
    private static final int MAX_FAILURE_LINES = 48;
    private static final int MAX_SKIPPED_IDS_PER_BATCH = 24;
    private static final int MAX_MISSING_IDS_PER_BATCH = 32;

    private static final List<SupplierFailure> SUPPLIER_FAILURES = new ArrayList<>();
    private static final List<BatchLine> BATCH_LINES = new ArrayList<>();
    private static final List<CoverageLine> COVERAGE_LINES = new ArrayList<>();

    private static final Set<String> RESERVED_NAMESPACES = Set.of(
        "minecraft", "c", "neoforge", "forge", "realms", "vyne");

    private static boolean isDatapackCriticalDeferredRegistry(String registryPath) {
        return registryPath != null
            && ("minecraft:block".equals(registryPath) || "minecraft:item".equals(registryPath));
    }

    private RegistrationDiagnostics() {}

    public static boolean isReservedPlatformNamespace(String s) {
        return s != null && RESERVED_NAMESPACES.contains(s);
    }

    public static void resetPhase() {
        synchronized (LOCK) {
            SUPPLIER_FAILURES.clear();
            BATCH_LINES.clear();
            COVERAGE_LINES.clear();
        }
    }

    public static void recordDeferredSupplierFailure(Object registryKey, Object id, Throwable t) {
        Objects.requireNonNull(id);
        Throwable root = rootCause(t);
        String reg = ReflectionHelper.extractRegistryKeyLocation(registryKey);
        synchronized (LOCK) {
            SUPPLIER_FAILURES.add(new SupplierFailure(
                reg != null ? reg : "?",
                id.toString(),
                t.getClass().getName(),
                root.getClass().getName(),
                shortMessage(root)));
        }
    }

    public static void recordDeferredRegisterBatch(String modId, String registryPath,
                                                   int applied, int skipped,
                                                   List<?> skippedIds) {
        if (skipped <= 0) {
            return;
        }
        List<String> ids = skippedIds == null ? List.of()
            : skippedIds.stream().limit(MAX_SKIPPED_IDS_PER_BATCH).map(RegistrationDiagnostics::idToLogString).toList();
        synchronized (LOCK) {
            BATCH_LINES.add(new BatchLine(modId, registryPath, applied, skipped, ids));
        }
    }

    public static void recordExpectedVsRegistered(String modId, String registryPath,
                                                  List<?> expectedIds,
                                                  List<?> registeredIds) {
        List<String> expected = expectedIds == null ? List.of()
            : expectedIds.stream().map(RegistrationDiagnostics::idToLogString).toList();
        List<String> registered = registeredIds == null ? List.of()
            : registeredIds.stream().map(RegistrationDiagnostics::idToLogString).toList();

        java.util.LinkedHashSet<String> regSet = new java.util.LinkedHashSet<>(registered);
        List<String> missing = expected.stream()
            .filter(id -> !regSet.contains(id))
            .limit(MAX_MISSING_IDS_PER_BATCH)
            .toList();

        synchronized (LOCK) {
            COVERAGE_LINES.add(new CoverageLine(
                modId,
                registryPath,
                expected.size(),
                registered.size(),
                Math.max(0, expected.size() - registered.size()),
                missing
            ));
        }
    }

    public static Set<String> collectUnhealthyModIds(Set<String> knownJarModIds) {
        Set<String> known = knownJarModIds == null ? Set.of() : knownJarModIds;
        List<SupplierFailure> failuresCopy;
        List<BatchLine> batchesCopy;
        List<CoverageLine> coverageCopy;
        synchronized (LOCK) {
            failuresCopy = new ArrayList<>(SUPPLIER_FAILURES);
            batchesCopy = new ArrayList<>(BATCH_LINES);
            coverageCopy = new ArrayList<>(COVERAGE_LINES);
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (BatchLine b : batchesCopy) {
            if (b.skipped > 0 && b.modId != null && !b.modId.isBlank()
                && !RESERVED_NAMESPACES.contains(b.modId)
                && isDatapackCriticalDeferredRegistry(b.registryPath)) {
                out.add(b.modId);
            }
        }
        for (CoverageLine c : coverageCopy) {
            if (c.missing > 0 && c.modId != null && !c.modId.isBlank()
                && !RESERVED_NAMESPACES.contains(c.modId)
                && isDatapackCriticalDeferredRegistry(c.registryPath)) {
                out.add(c.modId);
            }
        }
        for (SupplierFailure f : failuresCopy) {
            String ns = namespaceOfRlString(f.id);
            if (ns == null || RESERVED_NAMESPACES.contains(ns)) {
                continue;
            }
            if (!known.isEmpty() && known.contains(ns)) {
                out.add(ns);
            }
        }
        return out;
    }

    public static List<ModCoverageExport> exportCoverageLines() {
        synchronized (LOCK) {
            List<ModCoverageExport> out = new ArrayList<>(COVERAGE_LINES.size());
            for (CoverageLine c : COVERAGE_LINES) {
                out.add(new ModCoverageExport(
                    c.modId, c.registryPath, c.expected, c.registered, c.missing));
            }
            return List.copyOf(out);
        }
    }

    public static Map<String, Integer> supplierFailureCountsByModNamespace() {
        synchronized (LOCK) {
            Map<String, Integer> m = new HashMap<>();
            for (SupplierFailure f : SUPPLIER_FAILURES) {
                String ns = namespaceOfRlString(f.id);
                if (ns == null || RESERVED_NAMESPACES.contains(ns)) {
                    continue;
                }
                m.merge(ns, 1, Integer::sum);
            }
            return Map.copyOf(m);
        }
    }

    public static Map<String, Integer> skippedNullTotalsByMod() {
        synchronized (LOCK) {
            Map<String, Integer> m = new HashMap<>();
            for (BatchLine b : BATCH_LINES) {
                if (b.skipped <= 0 || b.modId == null || b.modId.isBlank()
                        || RESERVED_NAMESPACES.contains(b.modId)
                        || !isDatapackCriticalDeferredRegistry(b.registryPath)) {
                    continue;
                }
                m.merge(b.modId, b.skipped, Integer::sum);
            }
            return Map.copyOf(m);
        }
    }

    public record ModCoverageExport(String modId, String registryPath,
                                    int expected, int registered, int missing) {}

    public record ModCoreAggregate(String modId, int expectedTotal, int registeredTotal, int missingTotal) {}

    public static List<ModCoreAggregate> exportAggregatedBlockItemCoverage() {
        Map<String, int[]> acc = new LinkedHashMap<>();
        synchronized (LOCK) {
            for (CoverageLine c : COVERAGE_LINES) {
                if (c.modId == null || c.modId.isBlank() || RESERVED_NAMESPACES.contains(c.modId)) {
                    continue;
                }
                String p = c.registryPath;
                if (!"minecraft:block".equals(p) && !"minecraft:item".equals(p)) {
                    continue;
                }
                int[] v = acc.computeIfAbsent(c.modId, k -> new int[3]);
                v[0] += c.expected;
                v[1] += c.registered;
                v[2] += c.missing;
            }
        }
        List<ModCoreAggregate> out = new ArrayList<>(acc.size());
        for (Map.Entry<String, int[]> e : acc.entrySet()) {
            int[] v = e.getValue();
            out.add(new ModCoreAggregate(e.getKey(), v[0], v[1], v[2]));
        }
        return List.copyOf(out);
    }

    public static Map<String, Integer> skippedNullMaxBatchByMod() {
        synchronized (LOCK) {
            Map<String, Integer> m = new HashMap<>();
            for (BatchLine b : BATCH_LINES) {
                if (b.skipped <= 0 || b.modId == null || b.modId.isBlank()
                        || RESERVED_NAMESPACES.contains(b.modId)
                        || !isDatapackCriticalDeferredRegistry(b.registryPath)) {
                    continue;
                }
                m.merge(b.modId, b.skipped, Math::max);
            }
            return Map.copyOf(m);
        }
    }

    private static String idToLogString(Object id) {
        if (id == null) {
            return "null";
        }
        return id.toString();
    }

    private static String namespaceOfRlString(String id) {
        if (id == null) {
            return null;
        }
        int colon = id.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        return id.substring(0, colon);
    }

    public static void logPostRegistrationReport() {
        if (dev.stolym.vyne.util.VyneLogLevel.isVerbose()) {
            VyneLogger.info("[RegDiag] BEGIN post-registration report");
            try {
                logPostRegistrationReportBody();
                VyneLogger.info("[RegDiag] END post-registration report (ok)");
            } catch (Throwable t) {
                VyneLogger.error("[RegDiag] END post-registration report (failed mid-report)", t);
                throw t;
            }
            return;
        }

        int failN;
        long batchesWithSkips;
        long incomplete;
        int coverageSize;
        synchronized (LOCK) {
            failN = SUPPLIER_FAILURES.size();
            batchesWithSkips = BATCH_LINES.stream().filter(b -> b.skipped > 0).count();
            incomplete = COVERAGE_LINES.stream().filter(c -> c.missing > 0).count();
            coverageSize = COVERAGE_LINES.size();
        }

        boolean clean = failN == 0 && batchesWithSkips == 0 && incomplete == 0 && coverageSize > 0;
        if (clean) {
            VyneLogger.success("[RegDiag] Registration clean — all tracked ids registered (-Dvyne.verbose=true for full report).");
            return;
        }

        try {
            logPostRegistrationReportBody();
        } catch (Throwable t) {
            VyneLogger.error("[RegDiag] post-registration report failed mid-body", t);
            throw t;
        }
    }

    private static void logPostRegistrationReportBody() {
        List<SupplierFailure> failuresCopy;
        List<BatchLine> batchesCopy;
        List<CoverageLine> coverageCopy;
        synchronized (LOCK) {
            failuresCopy = new ArrayList<>(SUPPLIER_FAILURES);
            batchesCopy = new ArrayList<>(BATCH_LINES);
            coverageCopy = new ArrayList<>(COVERAGE_LINES);
        }

        VyneLogger.divider();
        VyneLogger.init("[RegDiag] ═══ Registration summary (Vyne) ═══");
        VyneLogger.init("[RegDiag] Explains: “success” logs are per-entry; datapacks need EVERY id in registries.");

        int failN = failuresCopy.size();
        if (failN == 0) {
            VyneLogger.success("[RegDiag] Deferred supplier failures: 0");
        } else {
            VyneLogger.failure("[RegDiag] Deferred supplier failures: " + failN
                + " (these ids were NOT registered into the live game registry)");
            Map<String, Long> byReg = failuresCopy.stream()
                .collect(Collectors.groupingBy(f -> f.registryPath, LinkedHashMap::new, Collectors.counting()));
            VyneLogger.detail("[RegDiag] Failures by registry: "
                + byReg.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", ")));

            int n = 0;
            for (SupplierFailure f : failuresCopy) {
                if (n++ >= MAX_FAILURE_LINES) {
                    VyneLogger.warn("[RegDiag] … +" + (failN - MAX_FAILURE_LINES) + " more (grep log for [RegDiag] DEFERRED_FAIL line)");
                    break;
                }
                VyneLogger.warn("[RegDiag] DEFERRED_FAIL registry=" + f.registryPath + " id=" + f.id
                    + " threw=" + f.thrownClass + " root=" + f.rootClass + " — " + f.rootMessage);
            }
            logDeferredFailureHints(failuresCopy);
        }

        long batchesWithSkips = batchesCopy.stream().filter(b -> b.skipped > 0).count();
        if (batchesWithSkips == 0) {
            VyneLogger.success("[RegDiag] DeferredRegister batches with skipped-null entries: 0");
        } else {
            VyneLogger.failure("[RegDiag] Batches with skipped-null suppliers: " + batchesWithSkips);
            for (BatchLine b : batchesCopy) {
                if (b.skipped <= 0) {
                    continue;
                }
                String sample = b.skippedIds.isEmpty() ? "(ids not listed)"
                    : String.join(", ", b.skippedIds);
                if (b.skipped > b.skippedIds.size()) {
                    sample += " …";
                }
                VyneLogger.warn("[RegDiag] SKIP_NULL mod=" + b.modId + " registry=" + b.registryPath
                    + " applied=" + b.applied + " skipped=" + b.skipped + " sample=" + sample);
            }
        }

        long incomplete = coverageCopy.stream().filter(c -> c.missing > 0).count();
        if (coverageCopy.isEmpty()) {
            VyneLogger.warn("[RegDiag] Expected-vs-registered audit: no batches recorded");
        } else if (incomplete == 0) {
            VyneLogger.success("[RegDiag] Expected-vs-registered audit: all tracked IDs were registered.");
        } else {
            VyneLogger.failure("[RegDiag] Expected-vs-registered audit: " + incomplete
                + " batch(es) missing at least one id.");
            for (CoverageLine c : coverageCopy) {
                if (c.missing <= 0) continue;
                String sample = c.missingIds.isEmpty() ? "(none listed)"
                    : String.join(", ", c.missingIds);
                if (c.missing > c.missingIds.size()) {
                    sample += " …";
                }
                VyneLogger.warn("[RegDiag] MISSING_IDS mod=" + c.modId + " registry=" + c.registryPath
                    + " expected=" + c.expected + " registered=" + c.registered
                    + " missing=" + c.missing + " sample=" + sample);
            }
        }

        if (failN > 0 || batchesWithSkips > 0) {
            VyneLogger.error("[RegDiag] Datapack risk: any worldgen/recipe JSON that references a FAILED or "
                + "SKIP_NULL id can throw Failed to load registries / unknown registry key on world load.");
        } else {
            VyneLogger.success("[RegDiag] No deferred failures recorded in this phase — block/item ids needed "
                + "by datapacks should match BuiltInRegistries if mod code paths succeeded.");
        }
        VyneLogger.divider();
    }

    private static void logDeferredFailureHints(List<SupplierFailure> failures) {
        boolean codecLikely = false;
        boolean cnfeLikely = false;
        for (SupplierFailure f : failures) {
            String msg = f.rootMessage != null ? f.rootMessage : "";
            String blob = (msg + " " + f.rootClass).toLowerCase(Locale.ROOT);
            if (blob.contains("codec") || blob.contains("recordcodecbuilder") || blob.contains("codecstart")) {
                codecLikely = true;
            }
            if (f.rootClass != null && f.rootClass.contains("ClassNotFoundException")) {
                cnfeLikely = true;
            }
        }
        if (codecLikely) {
            VyneLogger.detail("[RegDiag] Hint: serialization / codec NoSuchMethodError — ensure the mod JAR is remapped under "
                + "VCL and codec bridge rules apply; if it persists, the declaring class may not be transforming.");
        }
        if (cnfeLikely) {
            VyneLogger.detail("[RegDiag] Hint: ClassNotFoundException on a game type — check class loader (Knot vs app) and that "
                + "intermediary game classes are visible to the mod sandbox, not a missing register() call.");
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c;
    }

    private static String shortMessage(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.isEmpty()) {
            return "(no message)";
        }
        return m.length() > 220 ? m.substring(0, 217) + "…" : m;
    }

    private record SupplierFailure(String registryPath, String id,
                                   String thrownClass, String rootClass, String rootMessage) {}

    private record BatchLine(String modId, String registryPath, int applied, int skipped,
                             List<String> skippedIds) {}

    private record CoverageLine(String modId, String registryPath, int expected, int registered,
                                int missing, List<String> missingIds) {}
}
