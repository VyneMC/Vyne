/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.minecraftforge.fml;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModList {
    private static final ModList INSTANCE = new ModList();
    private static final Set<String> loadedMods = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<String, String> modVersions = new ConcurrentHashMap<>();

    static {
        seedLoadedMods(dev.stolym.vyne.core.ModIndex.versions());
        loadedMods.add("forge");
        loadedMods.add("minecraft");
    }

    public static void registerMod(String modId) { loadedMods.add(modId); }

    public static void seedLoadedMods(Map<String, String> idToVersion) {
        if (idToVersion == null) return;
        for (Map.Entry<String, String> e : idToVersion.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) continue;
            loadedMods.add(e.getKey());
            if (e.getValue() != null && !e.getValue().isBlank()) {
                modVersions.put(e.getKey().toLowerCase(java.util.Locale.ROOT), e.getValue());
            }
        }
    }

    public static String getModVersion(String modId) {
        if (modId == null) return null;
        return modVersions.get(modId.toLowerCase(java.util.Locale.ROOT));
    }

    public static ModList get() { return INSTANCE; }

    public boolean isLoaded(String modId) {
        if (loadedMods.contains(modId)) return true;
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }

    public Optional<? extends Object> getModContainerById(String modId) {
        return Optional.empty();
    }
}
