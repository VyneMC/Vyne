/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.core.VyneLauncher;
import dev.stolym.vyne.loaders.RealNeoForgeApiMount;
import dev.stolym.vyne.util.VyneLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class ConvertedModMount {
    private ConvertedModMount() {}

    private static volatile Path preparedFmlCache;

    private static volatile NeoForgeInstall install;

    public static void preparePatchedGame(boolean dedicatedServer) {
        if ("false".equalsIgnoreCase(System.getProperty("vyne.patchedGame"))) return;
        String version = VyneLauncher.getNeoForgeVersion();
        List<DetectedMod> mods = VyneLauncher.getDetectedMods();
        boolean neoForgeMods = mods != null && mods.stream()
                .anyMatch(mod -> mod.getLoaderType() == DetectedMod.LoaderType.NEOFORGE);
        boolean fabricMods = mods != null && mods.stream()
                .anyMatch(mod -> mod.getLoaderType() == DetectedMod.LoaderType.FABRIC && !isVyne(mod));
        boolean hooks = Boolean.getBoolean("vyne.hooks");
        if (hooks) version = HookedGame.SURFACE_VERSION;
        if (version == null || version.isBlank() || !neoForgeMods) return;
        if (fabricMods) {
            VyneLogger.info("[PatchedGame] Fabric and NeoForge mods together: using the compatibility path");
            return;
        }
        try {
            Path base = Path.of(System.getProperty("vyne.gameDir", ".")).toAbsolutePath().normalize();
            Path cacheRoot = base.resolve(".vyne").resolve("cache");
            NeoForgeInstall neoForge;
            if (hooks) {
                Path vanilla = VanillaGame.mojang(VyneLauncher.TARGET_MC, dedicatedServer, cacheRoot);
                Path game = HookPatches.apply(vanilla, dedicatedServer, cacheRoot);
                NeoForgeLibraries libraries = NeoForgeLibraries.ensure(dedicatedServer, base);
                neoForge = new NeoForgeInstall(version, game, vanilla, libraries.fml(), libraries.bus(), libraries.libraries());
                VyneLauncher.useNeoForgeApiJar(HookedGame.vyneNeoForge(cacheRoot));
                VyneLogger.info("[Hooks] Running vanilla Minecraft with Vyne's hooks and Vyne's NeoForge API");
            } else {
                neoForge = NeoForgeInstall.ensure(version, dedicatedServer, base);
            }
            Path game = neoForge.patchedGame();
            Path vanilla = neoForge.vanillaGame();
            List<Path> classpath = new ArrayList<>();
            classpath.add(vanilla);
            Path neoApi = VyneLauncher.getNeoForgeApiJar();
            if (neoApi != null) classpath.add(neoApi);
            classpath.add(neoForge.fml());
            classpath.add(neoForge.bus());
            List<Path> inputs = new ArrayList<>();
            List<Path> indexSources = new ArrayList<>();
            if (neoApi != null && Files.exists(neoApi)) {
                inputs.add(RealNeoForgeApiMount.prepareFullApiJar(neoApi));
                indexSources.add(neoApi);
            }
            List<DetectedMod> detectedMods = VyneLauncher.getDetectedMods();
            if (detectedMods != null) {
                for (DetectedMod mod : detectedMods) {
                    if (isEmulated(mod) && mod.getJarPath() != null && Files.isRegularFile(mod.getJarPath())) {
                        inputs.add(mod.getJarPath());
                        indexSources.add(mod.getJarPath());
                        classpath.add(mod.getJarPath());
                    }
                }
            }
            String mc = VyneLauncher.TARGET_MC;
            Path cache = Path.of(System.getProperty("vyne.gameDir", ".")).toAbsolutePath().normalize()
                    .resolve(".vyne").resolve("cache").resolve("patched");
            dev.stolym.vyne.ui.VyneLoadingScreen.update("Preparing NeoForge's game (first launch only)", 50);
            PatchedGameBuilder.Result result = PatchedGameBuilder.build(game, inputs, classpath,
                    dev.stolym.vyne.mappings.MojmapDownloader.getIntermediaryPath(mc),
                    dev.stolym.vyne.mappings.MojmapDownloader.getMojmapPath(mc), cache, dedicatedServer);
            dev.stolym.vyne.core.VyneMode.enablePatchedGame();
            if (hooks) dev.stolym.vyne.core.VyneMode.enableHooks();
            GameOverlay.install(result.game());
            for (int i = 0; i < inputs.size(); i++) {
                ModConverter.registerConverted(inputs.get(i), indexSources.get(i), result.converted().get(inputs.get(i)));
            }
            dev.stolym.vyne.core.VyneMode.remapClasspath(classpath);
            preparedFmlCache = cache;
            install = neoForge;
            VyneLogger.info("[PatchedGame] Running Minecraft with NeoForge's patches (" + GameOverlay.size() + " entries)");
        } catch (Throwable t) {
            VyneLogger.error("[PatchedGame] Could not prepare NeoForge's patched game, running vanilla: " + t, t);
        }
    }

    public static void mount(Consumer<Path> addCodeSource, Collection<Path> gameJars) {
        mountFml(addCodeSource);
        List<DetectedMod> detected = VyneLauncher.getDetectedMods();
        List<ModConverter.Request> requests = new ArrayList<>();
        Set<Path> hierarchyJars = new LinkedHashSet<>();
        if (gameJars != null) hierarchyJars.addAll(gameJars);

        if (detected != null) {
            for (DetectedMod mod : detected) {
                Path jar = mod.getJarPath();
                if (jar == null || !Files.isRegularFile(jar)) continue;
                hierarchyJars.add(jar);
                if (isEmulated(mod)) {
                    requests.add(new ModConverter.Request(jar, mod.getModId()));
                }
            }
        }

        Path strippedApi = null;
        Path neoApi = VyneLauncher.getNeoForgeApiJar();
        if (neoApi != null && Files.exists(neoApi) && RealNeoForgeApiMount.isEnabled()) {
            try {
                strippedApi = dev.stolym.vyne.core.VyneMode.patchedGame()
                        ? RealNeoForgeApiMount.prepareFullApiJar(neoApi)
                        : RealNeoForgeApiMount.prepareStrippedApiJar(neoApi);
                hierarchyJars.add(neoApi);
                requests.add(new ModConverter.Request(strippedApi, neoApi, "NeoForge API"));
            } catch (IOException e) {
                VyneLogger.warn("[Vyne] Could not prepare the NeoForge API jar: " + e.getMessage());
            }
        }

        dev.stolym.vyne.ui.VyneLoadingScreen.update("Preparing " + requests.size() + " mod jar(s)...", 52);
        try {
            ModConverter.convertAll(requests, hierarchyJars, environmentKey(neoApi));
        } catch (Throwable t) {
            VyneLogger.warn("[Converter] Conversion skipped (" + t + "); mods will be translated while the game runs.");
        }

        dev.stolym.vyne.ui.VyneLoadingScreen.update("Loading Minecraft...", 58);
        if (detected != null) {
            for (DetectedMod mod : detected) {
                if (!isEmulated(mod) || mod.getJarPath() == null) continue;
                VyneLogger.info("[Vyne] Adding " + mod.getModId() + " to unified Knot classpath.");
                addCodeSource.accept(ModConverter.runtimeJar(mod.getJarPath()));
            }
        }
        if (strippedApi != null) {
            VyneLogger.info("[Vyne] Adding NeoForge API to unified Knot classpath: " + strippedApi.getFileName());
            Path apiRuntime = ModConverter.runtimeJar(strippedApi);
            dev.stolym.vyne.core.VyneMode.neoForgeApiJar(apiRuntime);
            addCodeSource.accept(apiRuntime);
        }
        NeoForgeInstall neoForge = install;
        if (dev.stolym.vyne.core.VyneMode.patchedGame() && neoForge != null) {
            for (Path library : neoForge.libraries()) addCodeSource.accept(library);
            addCodeSource.accept(neoForge.bus());
            VyneLogger.info("[PatchedGame] NeoForge's event bus mounted: " + neoForge.bus().getFileName());
        }
        try {
            addCodeSource.accept(VyneGameJar.prepare());
        } catch (IOException e) {
            VyneLogger.error("[Vyne] Could not prepare Vyne's game-side classes: " + e.getMessage(), e);
        }
        dev.stolym.vyne.loaders.KnotModClasspathBridge.markVyneJarRegistered();
    }

    private static void mountFml(Consumer<Path> addCodeSource) {
        NeoForgeInstall neoForge = install;
        if (!dev.stolym.vyne.core.VyneMode.patchedGame() || neoForge == null || preparedFmlCache == null) return;
        try {
            Path loader = FmlMount.prepare(neoForge.fml(), preparedFmlCache);
            addCodeSource.accept(loader);
            VyneLogger.info("[PatchedGame] NeoForge's FML mounted: " + loader.getFileName());
        } catch (Throwable t) {
            VyneLogger.error("[PatchedGame] Could not mount NeoForge's FML: " + t, t);
        }
    }

    private static boolean isVyne(DetectedMod mod) {
        if ("fabricloader".equals(mod.getModId()) || "vyne".equals(mod.getModId())) return true;
        try {
            Path self = Path.of(VyneLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return mod.getJarPath() != null && Files.isSameFile(self, mod.getJarPath());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isEmulated(DetectedMod mod) {
        return mod.getLoaderType() == DetectedMod.LoaderType.NEOFORGE
                || mod.getLoaderType() == DetectedMod.LoaderType.FORGE;
    }

    private static String environmentKey(Path neoApi) {
        StringBuilder key = new StringBuilder(VyneLauncher.VERSION).append('|').append(VyneLauncher.TARGET_MC);
        if (dev.stolym.vyne.core.VyneMode.patchedGame()) key.append("|patched-").append(Routing.fingerprint());
        if (neoApi != null) key.append('|').append(neoApi.getFileName());
        try {
            Path self = Path.of(VyneLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(self)) {
                key.append('|').append(Files.size(self)).append(':').append(Files.getLastModifiedTime(self).toMillis());
            }
        } catch (Exception ignored) {
        }
        return key.toString();
    }
}
