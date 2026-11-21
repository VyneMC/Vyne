/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mappings;

import dev.stolym.vyne.util.VyneLogger;

import java.nio.file.Path;

public final class MappingRegistry {
    private static volatile MappingComposer.ComposedMappings instance;

    public static void init(String mcVersion) {
        if (instance != null) return;
        synchronized (MappingRegistry.class) {
            if (instance != null) return;
            try {
                VyneLogger.info("[MappingRegistry] Initializing Mojmap→Intermediary mappings for " + mcVersion + "...");
                Path mojmapPath = MojmapDownloader.getMojmapPath(mcVersion);
                Path intermediaryPath = MojmapDownloader.getIntermediaryPath(mcVersion);
                instance = MappingComposer.compose(mojmapPath, intermediaryPath);
                VyneLogger.info("[MappingRegistry] Mapping system ready.");
                notifyMappingsChanged();
                startClientJarProbeAsync(mcVersion);
            } catch (Exception e) {
                VyneLogger.error("[MappingRegistry] Failed to initialize mappings: " + e.getMessage(), e);
                try {
                    java.nio.file.Files.deleteIfExists(MojmapDownloader.getMojmapCachePath(mcVersion));
                    java.nio.file.Files.deleteIfExists(MojmapDownloader.getIntermediaryCachePath(mcVersion));
                    VyneLogger.info("[MappingRegistry] Deleted corrupt cache, retrying...");
                    Path mojmapPath = MojmapDownloader.getMojmapPath(mcVersion);
                    Path intermediaryPath = MojmapDownloader.getIntermediaryPath(mcVersion);
                    instance = MappingComposer.compose(mojmapPath, intermediaryPath);
                    VyneLogger.info("[MappingRegistry] Mapping system ready (after cache refresh).");
                    notifyMappingsChanged();
                } catch (Exception e2) {
                    VyneLogger.error("[MappingRegistry] Retry failed: " + e2.getMessage(), e2);
                    instance = new MappingComposer.ComposedMappings(
                    new java.util.HashMap<>(),
                    new java.util.HashMap<>(),
                    new java.util.HashMap<>(),
                    new java.util.HashMap<>(),
                    new java.util.HashMap<>()
                    );
                }
            }
        }
    }

    public static MappingComposer.ComposedMappings get() {
        if (instance == null)
            throw new IllegalStateException("MappingRegistry not initialized — call init() first.");
        return instance;
    }

    public static void initForTests(MappingComposer.ComposedMappings mappings) {
        instance = mappings;
        notifyMappingsChanged();
    }

    public static boolean isReady() {
        return instance != null;
    }

    private static final java.util.List<Runnable> INVALIDATION_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void onMappingsChanged(Runnable listener) {
        INVALIDATION_LISTENERS.add(listener);
    }

    public static void notifyMappingsChanged() {
        MappingComposer.invalidateCache();
        for (Runnable listener : INVALIDATION_LISTENERS) listener.run();
    }

    private static void startClientJarProbeAsync(String mcVersion) {
        java.nio.file.Path cached = MojmapDownloader.getClientJarPath(mcVersion);
        if (java.nio.file.Files.exists(cached)) {
            MappingComposer.setClientJarProbe(cached);
            notifyMappingsChanged();
            VyneLogger.info("[MappingRegistry] Client JAR probe ready (cached).");
            return;
        }
        Thread t = new Thread(() -> {
            try {
                java.nio.file.Path jar = MojmapDownloader.getClientJar(mcVersion);
                MappingComposer.setClientJarProbe(jar);
                notifyMappingsChanged();
                VyneLogger.info("[MappingRegistry] Client JAR probe ready (downloaded).");
            } catch (Throwable e) {
                VyneLogger.warn("[MappingRegistry] Client JAR probe unavailable: " + e.getMessage()
                    + " — client-only stub shapes will fall back to heuristics.");
            }
        }, "vyne-client-jar-probe");
        t.setDaemon(true);
        t.start();
    }

    private static final java.util.Set<String> disabledMods = new java.util.HashSet<>();

    public static void registerDisabled(String modId) {
        disabledMods.add(modId);
        VyneLogger.warn("[Vyne] Mod '" + modId + "' added to suppression list — mixins and entrypoints will be inactive.");
    }

    public static boolean isDisabled(String modId) {
        return disabledMods.contains(modId);
    }
}
