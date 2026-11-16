/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.event;

import java.util.List;

public final class NeoForgeEvents {
    private static volatile Boolean enabled;

    private NeoForgeEvents() {}

    public static boolean enabled() {
        Boolean cached = enabled;
        if (cached != null) return cached;
        List<?> mods = dev.stolym.vyne.core.VyneLauncher.getPendingVCLMods();
        boolean result = mods != null && !mods.isEmpty();
        enabled = result;
        return result;
    }
}
