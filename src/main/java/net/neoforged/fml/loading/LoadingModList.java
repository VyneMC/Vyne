/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.fml.loading;

import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;

public final class LoadingModList {
    private static final LoadingModList INSTANCE = new LoadingModList();
    private static final ModFileInfo PRESENT = new ModFileInfo();

    private LoadingModList() {
    }

    public static LoadingModList get() {
        return INSTANCE;
    }

    public ModFileInfo getModFileById(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        try {
            if (FabricLoader.getInstance().isModLoaded(id)) {
                return PRESENT;
            }
        } catch (Throwable ignored) {
        }
        if (net.neoforged.fml.ModList.get().isLoaded(id)) {
            return PRESENT;
        }
        return null;
    }
}
