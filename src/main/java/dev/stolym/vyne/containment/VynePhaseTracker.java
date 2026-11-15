/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.containment;

import dev.stolym.vyne.util.VyneLogger;

import java.util.concurrent.atomic.AtomicReference;

public final class VynePhaseTracker {
    private VynePhaseTracker() {}

    public enum Phase {
        BOOT,

        DISCOVERY,

        DEPENDENCY,

        REGISTRY,

        DATAPACK,

        WORLD_LOAD,

        RUNTIME
    }

    private static final AtomicReference<Phase> CURRENT = new AtomicReference<>(Phase.BOOT);

    public static Phase current() {
        return CURRENT.get();
    }

    public static void advance(Phase next) {
        Phase prev = CURRENT.get();
        if (prev.ordinal() >= next.ordinal()) {
            VyneLogger.warn("[VynePhase] Attempted backward or duplicate phase transition: "
                    + prev + " → " + next + " (ignored)");
            return;
        }
        CURRENT.set(next);
        VyneLogger.info("[VynePhase] Phase advanced: " + prev + " → " + next);
    }

    public static boolean isPostFreeze() {
        Phase p = CURRENT.get();
        return p == Phase.DATAPACK || p == Phase.WORLD_LOAD || p == Phase.RUNTIME;
    }

    public static boolean isRuntime() {
        return CURRENT.get() == Phase.RUNTIME;
    }
}
