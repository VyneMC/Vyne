/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.fml;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ModList {
    private static final ModList INSTANCE = new ModList();
    private static final Set<String> loadedMods = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<String, String> modVersions = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger LOAD_COUNTER = new java.util.concurrent.atomic.AtomicInteger();
    private static final ThreadLocal<String> INSTANCE_LOAD_MOD_ID = new ThreadLocal<>();
    private static final ThreadLocal<Object> INSTANCE_LOAD_BUS = new ThreadLocal<>();
    private static final Map<String, Object> modBuses = new ConcurrentHashMap<>();
    private static final Map<String, ModContainer> containers = new ConcurrentHashMap<>();

    static {
        seedLoadedMods(dev.stolym.vyne.core.ModIndex.versions());
        loadedMods.add("neoforge");
        loadedMods.add("minecraft");
        loadedMods.add("forge");
    }

    public static void registerMod(String modId) {
        loadedMods.add(modId);
    }

    public static void seedLoadedMods(Map<String, String> idToVersion) {
        if (idToVersion == null) return;
        for (Map.Entry<String, String> e : idToVersion.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) continue;
            loadedMods.add(e.getKey());
            if (e.getValue() != null && !e.getValue().isBlank()) {
                modVersions.put(e.getKey().toLowerCase(java.util.Locale.ROOT), e.getValue());
            }
        }
        net.minecraftforge.fml.ModList.seedLoadedMods(idToVersion);
    }

    public static String getModVersion(String modId) {
        if (modId == null) return null;
        return modVersions.get(modId.toLowerCase(java.util.Locale.ROOT));
    }

    public static void beginModInstanceLoad(String modId, Object modEventBus) {
        INSTANCE_LOAD_MOD_ID.set(modId);
        INSTANCE_LOAD_BUS.set(modEventBus);
        dev.stolym.vyne.core.ModBusRegistry.register(modId, modEventBus);
        dev.stolym.vyne.ui.VyneLoadingScreen.setLoadingMod(modId);
        if (modId != null) {
            dev.stolym.vyne.ui.VyneLoadingScreen.modState(modId, dev.stolym.vyne.ui.VyneLoadingScreen.MOD_WORKING);
            java.util.List<dev.stolym.vyne.core.DetectedMod> pending = dev.stolym.vyne.core.VyneLauncher.getPendingVCLMods();
            int total = pending != null ? pending.size() : 0;
            int current = Math.min(Math.max(total, 1), LOAD_COUNTER.incrementAndGet());
            dev.stolym.vyne.ui.VyneLoadingScreen.update("Loading mods", 70);
            dev.stolym.vyne.ui.VyneLoadingScreen.task(modId, current, total);
        }
        if (modId != null && modEventBus != null && modBuses.putIfAbsent(modId, modEventBus) == null) {
            containers.remove(modId);
        }
    }

    public static void endModInstanceLoad() {
        String finished = INSTANCE_LOAD_MOD_ID.get();
        if (finished != null) {
            dev.stolym.vyne.ui.VyneLoadingScreen.modState(finished, dev.stolym.vyne.ui.VyneLoadingScreen.MOD_DONE);
            dev.stolym.vyne.ui.VyneLoadingScreen.activity("Loaded " + finished);
        }
        INSTANCE_LOAD_MOD_ID.remove();
        INSTANCE_LOAD_BUS.remove();
        dev.stolym.vyne.ui.VyneLoadingScreen.setLoadingMod(null);
    }

    public static java.util.Collection<Object> allModBuses() {
        return java.util.Collections.unmodifiableCollection(modBuses.values());
    }

    public static String currentlyLoadingModId() {
        String local = INSTANCE_LOAD_MOD_ID.get();
        return local != null ? local : dev.stolym.vyne.ui.VyneLoadingScreen.loadingMod();
    }

    public static ModList get() { return INSTANCE; }

    public List<Object> getAllScanData() {
        return Collections.emptyList();
    }

    public boolean isLoaded(String modId) {
        if (loadedMods.contains(modId)) return true;
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }

    public Optional<ModContainer> getModContainerById(String modId) {
        if (modId == null || modId.isEmpty()) {
            return Optional.empty();
        }
        if (!modId.equals(INSTANCE_LOAD_MOD_ID.get()) && !loadedMods.contains(modId) && !isLoaded(modId)) {
            return Optional.empty();
        }
        return Optional.of(containers.computeIfAbsent(modId, id ->
                new net.neoforged.fml.javafmlmod.FMLModContainer(id, modBuses.get(id), getModVersion(id))));
    }
}
