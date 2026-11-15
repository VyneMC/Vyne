/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.containment;

import dev.stolym.vyne.util.VyneLogger;

public final class VyneExecutionGate {
    private VyneExecutionGate() {}

    public static boolean isAllowed(String modId) {
        if (modId == null) return true;
        return VyneModStateRegistry.isRunnable(modId);
    }

    public static void checkAndGate(String modId, String context) {
        if (modId == null) return;
        VyneModState state = VyneModStateRegistry.getState(modId);
        if (!state.allowsExecution()) {
            throw new VyneModFrozenException(modId, state, context);
        }
    }

    public static void gate(Runnable work, String modId, String context) {
        if (!isAllowed(modId)) {
            VyneLogger.debug("[VyneGate] Skipping blocked mod '" + modId + "' in context: " + context);
            return;
        }
        VyneModIdentity.tagCurrentThread(modId);
        try {
            work.run();
        } catch (VyneModFrozenException e) {
            VyneLogger.debug("[VyneGate] Nested freeze block for '" + modId + "' in " + context);
        } catch (Throwable t) {
            VyneFailureTriage.classify(t, context, modId);
        } finally {
            VyneModIdentity.clearCurrentThread();
        }
    }

    public static boolean allowsRegistryWrite(String modId, String context) {
        if (modId == null) return true;
        if (!isAllowed(modId)) {
            VyneLogger.warn("[VyneGate] Blocked registry write from frozen mod '"
                    + modId + "' — context: " + context);
            return false;
        }
        return true;
    }
}
