/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.containment;

import dev.stolym.vyne.ui.VyneFailureReport;
import dev.stolym.vyne.util.VyneLogger;

public final class VyneFailureTriage {
    private VyneFailureTriage() {}

    public enum FailureTier {
        TIER_1_ISOLATE,
        TIER_2_WARN,
        TIER_3_CLEAN_SHUTDOWN,
        TIER_4_EMERGENCY_HALT
    }

    public static void classify(Throwable t, String context, String modId) {
        FailureTier tier = determineTier(t, context);
        VyneLogger.warn("[VyneTriage] Classified '" + (modId != null ? modId : "unknown")
                + "' as " + tier + " in context=" + context
                + " (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")");
        dispatch(tier, modId, context, t);
    }

    public static FailureTier determineTier(Throwable t, String context) {
        VynePhaseTracker.Phase phase = VynePhaseTracker.current();

        if (t instanceof OutOfMemoryError) return FailureTier.TIER_4_EMERGENCY_HALT;
        if (t instanceof StackOverflowError) return FailureTier.TIER_4_EMERGENCY_HALT;
        if (isCoreCrash(t)) return FailureTier.TIER_4_EMERGENCY_HALT;

        if (VynePhaseTracker.isPostFreeze()) {
            if (isRegistryTaintingFailure(t)) return FailureTier.TIER_3_CLEAN_SHUTDOWN;
            if (phase == VynePhaseTracker.Phase.WORLD_LOAD) return FailureTier.TIER_3_CLEAN_SHUTDOWN;
        }

        if (isKnownSafeException(t)) return FailureTier.TIER_1_ISOLATE;
        if (!VynePhaseTracker.isPostFreeze()) return FailureTier.TIER_1_ISOLATE;

        if (phase == VynePhaseTracker.Phase.RUNTIME && !isRegistryTaintingFailure(t)) {
            return FailureTier.TIER_2_WARN;
        }

        return FailureTier.TIER_3_CLEAN_SHUTDOWN;
    }

    private static boolean isKnownSafeException(Throwable t) {
        return t instanceof MissingDependencyException
                || t instanceof VersionMismatchException
                || t instanceof DuplicateRegistryKeyException
                || t instanceof ClassNotFoundException
                || t instanceof VyneModFrozenException
                || (t instanceof VerifyError)
                || (t instanceof LinkageError && !(t instanceof ExceptionInInitializerError));
    }

    private static boolean isRegistryTaintingFailure(Throwable t) {
        if (t instanceof IllegalStateException ise) {
            String msg = ise.getMessage();
            return msg != null && (msg.contains("Unknown registry key")
                    || msg.contains("Registry")
                    || msg.contains("already registered"));
        }
        return false;
    }

    private static boolean isCoreCrash(Throwable t) {
        String cn = t.getClass().getName();
        return cn.startsWith("net.minecraft.server.dedicated")
                || cn.startsWith("net.fabricmc.loader")
                || (t instanceof Error && !(t instanceof AssertionError)
                        && !(t instanceof VerifyError)
                        && !(t instanceof LinkageError));
    }

    private static void dispatch(FailureTier tier, String modId, String context, Throwable cause) {
        switch (tier) {
            case TIER_1_ISOLATE -> {
                String reason = summarise(cause, context);
                if (modId != null) {
                    VyneModStateRegistry.freeze(
                            modId, reason,
                            contextToSubsystem(context), cause);
                    VyneFailureReport.renderAndLog(modId, VyneModState.FROZEN, reason,
                            tier, cause);
                } else {
                    VyneLogger.warn("[VyneTriage] Tier 1 failure with unknown mod — cannot isolate. "
                            + "reason=" + reason);
                }
            }
            case TIER_2_WARN -> {
                VyneLogger.warn("[VyneTriage] Tier 2 (non-critical): " + summarise(cause, context));
                VyneFailureReport.renderWarn(modId, summarise(cause, context), cause);
            }
            case TIER_3_CLEAN_SHUTDOWN -> {
                String reason = summarise(cause, context);
                if (modId != null) {
                    VyneModStateRegistry.hardStop(modId, reason,
                            contextToSubsystem(context), cause);
                }
                VyneFailureReport.renderAndLog(
                        modId != null ? modId : "core",
                        VyneModState.HARD_STOPPED, reason, tier, cause);
                VyneLogger.error("[VyneTriage] Tier 3 — initiating clean shutdown.");
                initiateCleanShutdown(reason, cause);
            }
            case TIER_4_EMERGENCY_HALT -> {
                String reason = summarise(cause, context);
                VyneFailureReport.renderAndLog(
                        modId != null ? modId : "core",
                        VyneModState.HARD_STOPPED, reason, tier, cause);
                VyneLogger.error("[VyneTriage] Tier 4 — EMERGENCY HALT.");
                Runtime.getRuntime().halt(1);
            }
        }
    }

    private static void initiateCleanShutdown(String reason, Throwable cause) {
        try {
            dev.stolym.vyne.world.VyneWorldSnapshot.offerRollback(reason);
        } catch (Throwable ignored) {}
        throw new VyneCleanShutdownError(reason, cause);
    }

    private static String summarise(Throwable t, String context) {
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg != null ? ": " + msg : "") + " [" + context + "]";
    }

    private static VyneModStateRegistry.FailureSubsystem contextToSubsystem(String context) {
        if (context == null) return VyneModStateRegistry.FailureSubsystem.UNKNOWN;
        String lc = context.toLowerCase(java.util.Locale.ROOT);
        if (lc.contains("tick"))     return VyneModStateRegistry.FailureSubsystem.TICK;
        if (lc.contains("event"))    return VyneModStateRegistry.FailureSubsystem.EVENT;
        if (lc.contains("registry")) return VyneModStateRegistry.FailureSubsystem.REGISTRY;
        if (lc.contains("datapack")) return VyneModStateRegistry.FailureSubsystem.DATAPACK;
        if (lc.contains("io") || lc.contains("file")) return VyneModStateRegistry.FailureSubsystem.IO;
        if (lc.contains("depend"))   return VyneModStateRegistry.FailureSubsystem.DEPENDENCY;
        if (lc.contains("class"))    return VyneModStateRegistry.FailureSubsystem.CLASSLOADING;
        return VyneModStateRegistry.FailureSubsystem.UNKNOWN;
    }

    public static class MissingDependencyException extends RuntimeException {
        public final String missingModId;
        public final String requiredVersion;
        public MissingDependencyException(String missingModId, String requiredVersion) {
            super("Missing dependency: " + missingModId + " (requires " + requiredVersion + ")");
            this.missingModId = missingModId;
            this.requiredVersion = requiredVersion;
        }
    }

    public static class VersionMismatchException extends RuntimeException {
        public final String depModId;
        public final String foundVersion;
        public final String requiredRange;
        public VersionMismatchException(String depModId, String foundVersion, String requiredRange) {
            super("Version mismatch: " + depModId + " is " + foundVersion
                    + " but requires " + requiredRange);
            this.depModId = depModId;
            this.foundVersion = foundVersion;
            this.requiredRange = requiredRange;
        }
    }

    public static class DuplicateRegistryKeyException extends RuntimeException {
        public final String registryPath;
        public final String key;
        public DuplicateRegistryKeyException(String registryPath, String key) {
            super("Duplicate registry key: " + key + " in " + registryPath);
            this.registryPath = registryPath;
            this.key = key;
        }
    }

    public static class VyneCleanShutdownError extends Error {
        public VyneCleanShutdownError(String reason, Throwable cause) {
            super("[Vyne] Clean shutdown triggered: " + reason, cause);
        }
    }
}
