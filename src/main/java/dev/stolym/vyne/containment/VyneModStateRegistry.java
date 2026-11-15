/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.containment;

import dev.stolym.vyne.util.VyneLogger;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

public final class VyneModStateRegistry {
    private VyneModStateRegistry() {}

    public record StateEntry(
            String modId,
            VyneModState state,
            String reason,
            FailureSubsystem subsystem,
            String blastRadiusDescription,
            Instant timestamp,
            Throwable cause
    ) {
        public String summary() {
            return "[" + state + "] mod='" + modId + "' subsystem=" + subsystem
                    + " reason=" + reason + " at=" + timestamp;
        }
    }

    public enum FailureSubsystem {
        REGISTRY,
        TICK,
        EVENT,
        IO,
        DATAPACK,
        DEPENDENCY,
        CLASSLOADING,
        UNKNOWN
    }

    private static final ConcurrentHashMap<String, StateEntry> STATES = new ConcurrentHashMap<>();

    public static void register(String modId) {
        if (modId == null || modId.isBlank()) return;
        STATES.putIfAbsent(modId, new StateEntry(
                modId, VyneModState.RUNNING, "Loaded normally",
                FailureSubsystem.UNKNOWN, "none", Instant.now(), null));
    }

    public static void freeze(String modId, String reason, FailureSubsystem subsystem,
                               Throwable cause) {
        if (modId == null) return;
        STATES.compute(modId, (id, existing) -> {
            if (existing != null && existing.state().isFailed()) return existing;
            StateEntry entry = new StateEntry(
                    modId, VyneModState.FROZEN, reason, subsystem,
                    "Contained — server continues", Instant.now(), cause);
            VyneLogger.warn("[VyneContainment] FROZEN: " + entry.summary());
            return entry;
        });
    }

    public static void hardStop(String modId, String reason, FailureSubsystem subsystem,
                                 Throwable cause) {
        if (modId == null) return;
        STATES.compute(modId, (id, existing) -> {
            if (existing != null && existing.state() == VyneModState.HARD_STOPPED) return existing;
            StateEntry entry = new StateEntry(
                    modId, VyneModState.HARD_STOPPED, reason, subsystem,
                    "Uncontained — initiating shutdown", Instant.now(), cause);
            VyneLogger.error("[VyneContainment] HARD_STOPPED: " + entry.summary());
            return entry;
        });
    }

    public static boolean isRunnable(String modId) {
        if (modId == null) return true;
        StateEntry entry = STATES.get(modId);
        return entry == null || entry.state().allowsExecution();
    }

    public static VyneModState getState(String modId) {
        StateEntry entry = modId != null ? STATES.get(modId) : null;
        return entry != null ? entry.state() : VyneModState.RUNNING;
    }

    public static StateEntry getEntry(String modId) {
        return modId != null ? STATES.get(modId) : null;
    }

    public static Collection<StateEntry> allEntries() {
        return Collections.unmodifiableCollection(STATES.values());
    }

    public static long failedModCount() {
        return STATES.values().stream().filter(e -> e.state().isFailed()).count();
    }

    public static String getReport() {
        StringBuilder sb = new StringBuilder();
        STATES.values().stream()
                .filter(e -> e.state().isFailed())
                .forEach(e -> sb.append("  → ")
                        .append(e.modId())
                        .append(" [").append(e.state()).append("]: ")
                        .append(e.reason())
                        .append("\n"));
        return sb.isEmpty() ? "  All mods running normally.\n" : sb.toString();
    }
}
