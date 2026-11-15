/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl;

import dev.stolym.vyne.containment.VyneModIdentity;
import dev.stolym.vyne.util.VyneLogger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LoaderAudit {
    private static final boolean ENABLED = Boolean.getBoolean("vyne.debug.loaderAudit");
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    private LoaderAudit() {}

    private static final Set<String> PARENT_FALLBACKS = ConcurrentHashMap.newKeySet();

    public static void recordParentFallback(String dottedName) {
        if (PARENT_FALLBACKS.add(dottedName)) {
            VyneLogger.warn("[LoaderAudit] " + dottedName
                    + " is game-side code but was loaded by the launcher's class loader (Vyne jar not on the game class path yet)");
        }
    }

    public static Set<String> parentFallbacks() {
        return Set.copyOf(PARENT_FALLBACKS);
    }

    public static void record(String dottedName) {
        if (!ENABLED || !SEEN.add(dottedName)) return;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        String loader = cl == null ? "<bootstrap>" : cl.getClass().getName();
        String modId = VyneModIdentity.resolveModId(cl);
        VyneLogger.info("[LoaderAudit] " + dottedName
                + " loader=" + loader
                + (modId != null ? " mod=" + modId : ""));
    }
}
