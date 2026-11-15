/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.util;

import dev.stolym.vyne.core.DetectedMod;

public final class VyneConflictReporter {
    public enum Stage {
        DETECTION,
        REMAPPING,
        SANDBOX_ISOLATION,
        CLASSLOADING,
        MIXIN_APPLICATION,
        LIFECYCLE_HOOK,
        CAPABILITY_LOOKUP,
        EVENT_DISPATCH
    }

    public static void report(DetectedMod mod, Stage stage, String detail, Throwable t) {
        VyneLogger.error("");
        VyneLogger.error("┌─ VYNE CRITICAL FAILURE ──────────────────────────────");
        VyneLogger.error("│ Mod      : " + (mod != null ? mod.getModId() : "Internal/Unknown"));
        VyneLogger.error("│ Pipeline : " + (mod != null ? mod.getLoaderType() : "Core"));
        VyneLogger.error("│ Stage    : " + stage);
        VyneLogger.error("│ Detail   : " + detail);
        VyneLogger.error("├──────────────────────────────────────────────────────");

        if (t != null) {
            VyneLogger.error("│ Exception: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            StackTraceElement[] ste = t.getStackTrace();
            for (int i = 0; i < Math.min(ste.length, 8); i++) {
                VyneLogger.error("│   at " + ste[i].toString());
            }
            if (ste.length > 8) {
                VyneLogger.error("│   ... " + (ste.length - 8) + " more");
            }
        }
        VyneLogger.error("└──────────────────────────────────────────────────────");
        VyneLogger.error("");
    }
}
