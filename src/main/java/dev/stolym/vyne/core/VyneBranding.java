/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.core;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.Set;

public final class VyneBranding {
    private static final Set<String> BUILT_IN = Set.of("minecraft", "java", "fabricloader", "mixinextras");

    private VyneBranding() {}

    public static String line() {
        int fabricMods = 0;
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            if (mod.getContainingMod().isPresent()) continue;
            if (BUILT_IN.contains(mod.getMetadata().getId()) || "builtin".equals(mod.getMetadata().getType())) continue;
            if (mod.getMetadata() instanceof dev.stolym.vyne.engine.detector.VyneVirtualMetadata) continue;
            fabricMods++;
        }
        return "Vyne " + VyneLauncher.VERSION + " (" + fabricMods + (fabricMods == 1 ? " Fabric mod" : " Fabric mods") + ")";
    }
}
