/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.core;

import dev.stolym.vyne.config.VyneContainmentConfig;
import dev.stolym.vyne.containment.VyneModStateRegistry;
import dev.stolym.vyne.containment.VynePhaseTracker;
import dev.stolym.vyne.engine.conflict.ConflictEngine;
import dev.stolym.vyne.engine.detector.ModDetector;
import dev.stolym.vyne.server.ServerPropertiesResilience;
import dev.stolym.vyne.ui.VyneLoadingScreen;
import dev.stolym.vyne.util.VyneLogger;
import dev.stolym.vyne.world.VyneWorldSnapshot;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;

public final class VyneLauncher {
    public static final String VERSION = "0.1.0-alpha";
    public static final String TARGET_MC = "1.21.1";
    public static final String DEFAULT_NEOFORGE = "21.1.265";

    private static final java.util.Set<String> hardDisabledModIds =
        new java.util.HashSet<>();
    private static List<DetectedMod> detectedMods;
    private static List<DetectedMod> pendingVCLMods = new ArrayList<>();
    private static Path neoForgeApiJar;

    public static Path getNeoForgeApiJar() {
        return neoForgeApiJar;
    }

    public static void useNeoForgeApiJar(Path jar) {
        neoForgeApiJar = jar;
    }

    public static boolean isHardDisabled(String modId) {
        return hardDisabledModIds.contains(modId);
    }

    public static java.util.Set<String> getHardDisabledModIds() {
        return java.util.Collections.unmodifiableSet(
            new java.util.HashSet<>(hardDisabledModIds)
        );
    }

    private static boolean isDesktopSession() {
        if (java.awt.GraphicsEnvironment.isHeadless()) return false;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win") || os.contains("mac")) return true;
        String display = System.getenv("DISPLAY");
        String wayland = System.getenv("WAYLAND_DISPLAY");
        return (
            (display != null && !display.isEmpty()) ||
            (wayland != null && !wayland.isEmpty())
        );
    }

    private static void openEarlyWindow(String[] args) {
        int width = 854;
        int height = 480;
        for (int i = 0; i < args.length - 1; i++) {
            try {
                if ("--width".equals(args[i])) width = Integer.parseInt(args[i + 1]);
                else if ("--height".equals(args[i])) height = Integer.parseInt(args[i + 1]);
            } catch (NumberFormatException ignored) {
            }
        }
        String gameDir = null;
        for (int i = 0; i < args.length - 1; i++) {
            if ("--gameDir".equals(args[i])) gameDir = args[i + 1];
        }
        try {
            dev.stolym.vyne.ui.VyneLoadingTheme.load(
                    gameDir != null ? Paths.get(gameDir) : Paths.get(".").toAbsolutePath().normalize());
            dev.stolym.vyne.ui.VyneEarlyWindow.open("Minecraft " + TARGET_MC, width, height, VERSION);
        } catch (Throwable t) {
            VyneLogger.debug("[EarlyWindow] Not available: " + t);
        }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("log4j2.status.entries", "0");
        System.setProperty("log4j2.StatusLogger.level", "OFF");
        try {
            org.apache.logging.log4j.status.StatusLogger.getLogger().setLevel(
                org.apache.logging.log4j.Level.OFF
            );
        } catch (Throwable ignored) {}

        boolean installerCli = false;
        boolean installerGui = false;
        boolean serverFlag = false;
        for (String arg : args) {
            if (
                "--install".equals(arg) || "--install-cli".equals(arg)
            ) installerCli = true;
            else if (
                "--installer".equals(arg) ||
                "--install-gui".equals(arg) ||
                "--gui".equals(arg)
            ) installerGui = true;
            else if ("--server".equals(arg)) serverFlag = true;
        }

        if (
            !installerCli &&
            !installerGui &&
            !serverFlag &&
            args.length == 0 &&
            isDesktopSession()
        ) {
            installerGui = true;
        }

        if (installerGui) {
            dev.stolym.vyne.install.gui.VyneInstallerGui.launch(args);
            return;
        }
        if (installerCli) {
            dev.stolym.vyne.install.VyneInstaller.run(args);
            return;
        }

        if (!serverFlag) {
            VyneLoadingScreen.update("Starting Vyne...", 2);
            openEarlyWindow(args);
        }

        VyneContainmentConfig.load();
        VynePhaseTracker.advance(VynePhaseTracker.Phase.BOOT);

        VyneLogger.banner(VERSION);
        VyneLogger.info(
            "Initializing Vyne Universal Modloader for Minecraft " + TARGET_MC
        );

        String gameDir = null;
        for (int i = 0; i < args.length - 1; i++) {
            if ("--gameDir".equals(args[i])) {
                gameDir = args[i + 1];
                break;
            }
        }
        if (gameDir != null) System.setProperty("vyne.gameDir", gameDir);

        Path baseDir =
            gameDir != null
                ? Paths.get(gameDir)
                : Paths.get(".").toAbsolutePath().normalize();

        if (System.getProperty("vyne.gameDir") == null) {
            System.setProperty("vyne.gameDir", baseDir.toString());
        }

        Path modsDir = baseDir.resolve("mods");
        if (!Files.exists(modsDir)) {
            try {
                Files.createDirectories(modsDir);
            } catch (IOException ignored) {}
        }

        VyneLogger.info("[Vyne] Scanning mods for dependency resolution...");
        VyneLoadingScreen.update("Scanning mods...", 5);
        ModDetector detector = new ModDetector(modsDir);
        detectedMods = detector.scan();

        String preferredVersion = VyneContainmentConfig.getNeoForgeVersion();
        dev.stolym.vyne.engine.dynamic.NeoForgeVersionResolver.ResolutionResult res =
            dev.stolym.vyne.engine.dynamic.NeoForgeVersionResolver.resolve(detectedMods, preferredVersion);

        String neoVersion = res.selectedVersion();
        neoForgeVersion = neoVersion;
        if (!res.incompatibleModIds().isEmpty()) {
            for (String modId : res.incompatibleModIds()) {
                hardDisabledModIds.add(modId);
                VyneLogger.warn("[Vyne] Disabling mod " + modId + " due to NeoForge version incompatibility.");
            }
        }

        VyneLoadingScreen.update("Resolving the NeoForge API...", 12);
        Path refDir = baseDir.resolve(".vyne").resolve("references");
        Path neoJar = dev.stolym.vyne.engine.dynamic.NeoForgeReferenceDownloader.resolve(neoVersion, refDir);
        neoForgeApiJar = neoJar;

        VyneLogger.info("[Vyne] Initializing Unified Knot Environment...");
        ClassLoader launcherLoader = VyneLauncher.class.getClassLoader();

        dev.stolym.vyne.util.MixinExtrasCompat.prepare(launcherLoader);

        VyneLoadingScreen.update("Checking " + detectedMods.size() + " mods...", 18);
        VyneLoadingScreen.activity("Found " + detectedMods.size() + " mods in " + modsDir.getFileName());

        EnvType envType = EnvType.CLIENT;
        for (String arg : args) {
            if ("--server".equals(arg)) envType = EnvType.SERVER;
        }

        VyneLogger.phase("PRE-LAUNCH DETECTION");
        VynePhaseTracker.advance(VynePhaseTracker.Phase.DISCOVERY);
        FailureTracker.clear();

        VyneLogger.info(
            "Detected " + detectedMods.size() + " mods across all ecosystems."
        );

        ConflictEngine conflictEngine = new ConflictEngine(detectedMods);
        conflictEngine.analyse();
        detectedMods = conflictEngine.getResolvedMods();
        VyneLogger.info(
            "After conflict resolution: " +
                detectedMods.size() +
                " mods active."
        );
        dev.stolym.vyne.engine.conflict.MixinConflictScanner.scan(detectedMods);

        VynePhaseTracker.advance(VynePhaseTracker.Phase.DEPENDENCY);

        java.util.List<VyneLoadingScreen.ModTile> modTiles = new java.util.ArrayList<>();
        for (DetectedMod mod : detectedMods) {
            if (mod.getModId() == null || mod.getLoaderType() == DetectedMod.LoaderType.VYNE) continue;
            modTiles.add(new VyneLoadingScreen.ModTile(mod.getModId(), mod.getLoaderType().name()));
        }
        VyneLoadingScreen.registerMods(modTiles);

        java.util.Map<String, String> idToVersion = new java.util.LinkedHashMap<>();
        for (DetectedMod mod : detectedMods) {
            if (mod.getModId() != null) idToVersion.put(mod.getModId(), mod.getVersion());
        }
        ModIndex.seed(idToVersion);

        for (DetectedMod mod : detectedMods) {
            VyneModStateRegistry.register(mod.getModId());
        }

        dev.stolym.vyne.engine.dynamic.deps.DependencyAnalyzer.Report depReport =
            dev.stolym.vyne.engine.dynamic.deps.DependencyAnalyzer.analyze(
                detectedMods
            );
        dev.stolym.vyne.engine.dynamic.deps.DependencyAnalyzer.logReport(
            depReport
        );
        if (!depReport.modsToDisable.isEmpty()) {
            hardDisabledModIds.addAll(depReport.modsToDisable);
            for (String id : depReport.modsToDisable) {
                VyneModStateRegistry.freeze(
                    id,
                    "Missing or incompatible dependency",
                    VyneModStateRegistry.FailureSubsystem.DEPENDENCY,
                    null
                );
                dev.stolym.vyne.core.FailureTracker.markFailed(id);
            }
        }

        VyneLogger.detail("Running Pre-Flight Linkage Verification...");
        preFlightVerification();

        for (DetectedMod mod : detectedMods) {
            String status = hardDisabledModIds.contains(mod.getModId())
                ? "[DISABLED]"
                : "[OK]";
            VyneLogger.detail(
                "  - " +
                    mod.getModId() +
                    " (" +
                    mod.getLoaderType() +
                    ") " +
                    status
            );
        }

        VynePhaseTracker.advance(VynePhaseTracker.Phase.REGISTRY);
        VyneLogger.phase("HANDOVER TO KNOT");
        VyneLogger.info("Injecting Vyne logic into Fabric launch pipeline...");

        System.setProperty("fabric.mappingNamespace", "intermediary");
        System.setProperty("vyne.loader.version", VERSION);

        try {
            Path intermediaryTiny =
                dev.stolym.vyne.mappings.MojmapDownloader.getIntermediaryCachePath(
                    TARGET_MC
                );
            if (Files.exists(intermediaryTiny)) {
                System.setProperty(
                    "fabric.mappingPath",
                    intermediaryTiny.toAbsolutePath().toString()
                );
            } else {
                dev.stolym.vyne.mappings.MojmapDownloader.getIntermediaryPath(
                    TARGET_MC
                );
                System.setProperty(
                    "fabric.mappingPath",
                    intermediaryTiny.toAbsolutePath().toString()
                );
            }
        } catch (Exception e) {
            VyneLogger.warn(
                "Failed to set fabric.mappingPath: " + e.getMessage()
            );
        }

        if (!hardDisabledModIds.isEmpty()) {
            String disableList = String.join(",", hardDisabledModIds);
            System.setProperty("fabric.debug.disableModIds", disableList);
        }

        VyneLoadingScreen.update("Loading mappings...", 28);
        dev.stolym.vyne.mappings.MappingRegistry.init(TARGET_MC);
        VyneLoadingScreen.activity("Mappings ready for Minecraft " + TARGET_MC);

        if (envType == EnvType.SERVER) {
            registerMinecraftServerJar();
        }

        VyneLoadingScreen.update("Indexing mod classes...", 40);
        VyneLogger.phase("METADATA INDEXING");
        VyneLogger.info("[Vyne] Building class hierarchy index for all mods...");
        for (DetectedMod mod : detectedMods) {
            dev.stolym.vyne.vcl.metadata.HierarchyScanner.scan(mod.getJarPath(), mod.getLoaderType());
        }
        try {
            Path vynePath = Paths.get(VyneLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            dev.stolym.vyne.vcl.metadata.HierarchyScanner.scan(vynePath, DetectedMod.LoaderType.VYNE);
        } catch (Exception ignored) {}

        String serverJarPath = System.getProperty("fabric.gameJarPath.server");
        if (serverJarPath != null) {
            dev.stolym.vyne.vcl.metadata.HierarchyScanner.scan(Paths.get(serverJarPath));
        }

        if (neoForgeApiJar != null) {
            dev.stolym.vyne.vcl.metadata.HierarchyScanner.scan(neoForgeApiJar);
        }

        VyneLogger.success("[Vyne] Indexed " + dev.stolym.vyne.vcl.metadata.VyneMetadataIndex.size() + " classes for hierarchy resolution.");
        VyneLoadingScreen.activity("Indexed " + dev.stolym.vyne.vcl.metadata.VyneMetadataIndex.size() + " classes");

        pendingVCLMods = detectedMods
            .stream()
            .filter(
                m ->
                    m.getLoaderType() != DetectedMod.LoaderType.FABRIC &&
                    m.getLoaderType() != DetectedMod.LoaderType.VYNE
            )
            .filter(m -> !hardDisabledModIds.contains(m.getModId()))
            .collect(Collectors.toList());

        if (envType == EnvType.SERVER) {
            String worldName = resolveWorldName(baseDir);
            Path worldDir = baseDir.resolve(worldName);
            VynePhaseTracker.advance(VynePhaseTracker.Phase.WORLD_LOAD);
            VyneWorldSnapshot.takeStartupSnapshot(worldDir);
        }

        VyneLoadingScreen.update("Starting Minecraft...", 48);
        VyneLogger.info("[Vyne] Launching Knot...");

        if (envType == EnvType.SERVER) {
            Path serverProps = baseDir.resolve("server.properties");
            if (!Files.exists(serverProps)) {
                VyneLogger.warn(
                    "server.properties not found! Creating default."
                );
                try {
                    Files.writeString(
                        serverProps,
                        "level-type=minecraft:normal\n" +
                            "game-mode=survival\n" +
                            "max-players=20\n" +
                            "max-tick-time=" +
                            ServerPropertiesResilience.VYNE_MIN_MAX_TICK_MS +
                            "\n"
                    );
                } catch (IOException e) {
                    VyneLogger.error(
                        "Failed to create server.properties: " + e.getMessage()
                    );
                }
            }
            ServerPropertiesResilience.ensureMaxTickTime(baseDir);
        }

        List<String> knotArgs = new ArrayList<>();
        for (String arg : args) {
            if (!"--server".equals(arg)) {
                knotArgs.add(arg);
            }
        }

        VyneLogger.info("[Vyne] Launching Knot...");
        Class<?> knotClass = Class.forName(
            "net.fabricmc.loader.impl.launch.knot.Knot",
            true,
            launcherLoader
        );
        java.lang.reflect.Method launchMethod = knotClass.getMethod(
            "launch",
            String[].class,
            net.fabricmc.api.EnvType.class
        );
        launchMethod.invoke(null, knotArgs.toArray(new String[0]), envType);
    }

    private static void registerMinecraftServerJar() throws Exception {
        String jarName = "minecraft_server." + TARGET_MC + ".jar";
        Path currentDirJar = Paths.get(jarName);
        Path parentDirJar = Paths.get("..").resolve(jarName);
        Path modsDirJar = Paths.get("mods").resolve(jarName);

        Path serverJar = null;

        if (Files.exists(currentDirJar)) {
            serverJar = currentDirJar;
        } else if (Files.exists(parentDirJar)) {
            serverJar = parentDirJar;
        } else if (Files.exists(modsDirJar)) {
            serverJar = modsDirJar;
        }

        if (serverJar == null) {
            VyneLogger.error(
                jarName +
                    " not found in current directory, parent directory, or mods/ folder!\n" +
                    "  Run the Vyne installer first to set up the server:\n" +
                    "    java -jar vyne-loader-" +
                    VERSION +
                    ".jar --server\n"
            );
            System.exit(1);
        }

        System.setProperty(
            "fabric.gameJarPath.server",
            serverJar.toAbsolutePath().toString()
        );
        VyneLogger.info(
            "[Vyne] Registered server jar for Fabric deobfuscation pipeline: " +
                serverJar.toAbsolutePath()
        );
    }

    public static List<DetectedMod> getPendingVCLMods() {
        return pendingVCLMods;
    }

    private static String resolveWorldName(Path baseDir) {
        Path serverProps = baseDir.resolve("server.properties");
        if (Files.exists(serverProps)) {
            try {
                for (String line : Files.readAllLines(serverProps)) {
                    if (line.startsWith("level-name=")) {
                        String val = line.substring("level-name=".length()).trim();
                        if (!val.isEmpty()) return val;
                    }
                }
            } catch (IOException ignored) {}
        }
        return "world";
    }

    private static void preFlightVerification() {
        hardDisabledModIds.addAll(FailureTracker.loadFailures());

        for (DetectedMod mod : detectedMods) {
            if (
                mod.getLoaderType() == DetectedMod.LoaderType.FABRIC ||
                mod.getLoaderType() == DetectedMod.LoaderType.VYNE
            ) continue;
        }
    }

    private static volatile String neoForgeVersion;

    public static String getNeoForgeVersion() {
        return neoForgeVersion;
    }

    public static List<DetectedMod> getDetectedMods() {
        return detectedMods;
    }

    public static int getTotalModCount() {
        return detectedMods != null ? detectedMods.size() : 0;
    }
}
