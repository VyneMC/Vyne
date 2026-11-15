/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl;

import dev.stolym.vyne.client.ClientRegistrationDiag;
import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.util.VyneLogger;
import dev.stolym.vyne.util.VyneConflictReporter;
import dev.stolym.vyne.mappings.MappingRegistry;
import dev.stolym.vyne.ui.VyneLoadingScreen;
import dev.stolym.vyne.world.RegistryHealthPolicy;
import dev.stolym.vyne.loaders.neoforge.NeoForgeSandbox;
import dev.stolym.vyne.loaders.forge.ForgeSandbox;

import net.neoforged.fml.ModList;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStream;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
public final class VCLManager {
    public static final boolean LEGACY_VCL_ENABLED = Boolean.getBoolean("vyne.vcl.legacy");

    private static final Set<String> failedMods   = dev.stolym.vyne.core.ModActivity.FAILED;
    private static final Set<String> inactiveModIds = dev.stolym.vyne.core.ModActivity.INACTIVE;
    private static volatile boolean initialized = false;
    private static final Object INIT_LOCK = new Object();

    public static boolean isInactive(String modId) {
        return inactiveModIds.contains(modId) || failedMods.contains(modId);
    }

    public static void markInactive(String modId) {
        inactiveModIds.add(modId);
    }

    public static void suppressModForRegistryHealth(String modId) {
        if (modId == null || modId.isBlank()) {
            return;
        }
        VyneLogger.debug("[VCL] suppressModForRegistryHealth is a no-op (datapacks are never stripped "
                + "for registry diagnostics — stripping breaks registration/worldgen): " + modId);
    }

    public static boolean shouldDropFailedModDataPack(String packId) {
        if (packId == null || packId.isEmpty()) {
            return false;
        }
        synchronized (INIT_LOCK) {
            for (String id : failedMods) {
                if (RegistryHealthPolicy.packMatchesMod(packId, id)) {
                    return true;
                }
            }
            for (String id : inactiveModIds) {
                if (RegistryHealthPolicy.packMatchesMod(packId, id)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void hardDisableMod(String modId) {
        if (modId == null || modId.isBlank()) {
            return;
        }
        failedMods.add(modId);
        inactiveModIds.add(modId);
        dev.stolym.vyne.core.FailureTracker.markFailed(modId);
        MappingRegistry.registerDisabled(modId);
    }

    public static String findModClassPublic(Path jarPath, DetectedMod.LoaderType type)
            throws Exception {
        return findModClass(jarPath, type,
            net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType()
                == net.fabricmc.api.EnvType.CLIENT);
    }

    record ModBusEntry(Object bus, ClassLoader loader, Class<?> mainClass, Object modInstance) {}

    public static void bootstrapMod(DetectedMod mod) {
        VyneLogger.info("[VCL] Pre-bootstrapping " + mod.getLoaderType()
            + " mod: " + mod.getModId());
    }

    private static final java.util.concurrent.Semaphore MOD_INIT_SEMAPHORE =
        new java.util.concurrent.Semaphore(
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2));

    private static volatile ClassLoader CANONICAL_KNOT_LOADER;
    private static volatile ClassLoader BRIDGE_LOADER;

    public static void setBridgeClassLoader(ClassLoader cl) {
        BRIDGE_LOADER = cl;
    }

    public static ClassLoader getBridgeClassLoader() {
        ClassLoader bridge = BRIDGE_LOADER;
        return bridge != null ? bridge : dev.stolym.vyne.core.GameLoader.get();
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Class<?>> WARMED_GAME_CLASSES =
        new java.util.concurrent.ConcurrentHashMap<>();

    public static ClassLoader getCanonicalKnotLoader() {
        ClassLoader canonical = CANONICAL_KNOT_LOADER;
        return canonical != null ? canonical : dev.stolym.vyne.core.GameLoader.get();
    }

    private static void warmClientOnlyGameStubs(ClassLoader globalNeo, ClassLoader globalForge) {
        String[] names = {
            "net.minecraft.class_1087",
            "net.minecraft.class_746",
            "net.minecraft.class_437",
        };
        for (ClassLoader loader : new ClassLoader[]{globalNeo, globalForge}) {
            if (loader == null) {
                continue;
            }
            for (String name : names) {
                try {
                    Class.forName(name, false, loader);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static Class<?> getWarmedGameClass(String dotName) {
        return dotName == null ? null : WARMED_GAME_CLASSES.get(dotName);
    }

    private static void rememberWarmedGameClassAliases(Class<?> clazz, String... dottedBinaryNames) {
        if (clazz == null || dottedBinaryNames == null) {
            return;
        }
        for (String key : dottedBinaryNames) {
            if (key == null || key.isBlank()) {
                continue;
            }
            WARMED_GAME_CLASSES.putIfAbsent(key, clazz);
        }
    }

    private static void primeCreativeTabStackForRemappingModLoader(ClassLoader modCl) {
        if (modCl == null || !(modCl instanceof dev.stolym.vyne.vcl.RemappingClassLoader)) {
            return;
        }
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        names.add("net.minecraft.class_1761");
        names.add("net.minecraft.class_1761$class_7913");
        names.add("net.minecraft.class_1761$class_7914");
        names.add("net.minecraft.class_1761$class_7703");
        names.add("net.minecraft.class_1761$class_8128");
        names.add("net.minecraft.class_1761$class_7704");
        names.add("net.minecraft.class_1761$class_7915");
        names.add("net.minecraft.class_1761$class_7705");
        names.add("net.minecraft.class_1761$class_7916");
        if (MappingRegistry.isReady()) {
            try {
                dev.stolym.vyne.mappings.MappingComposer.ComposedMappings m = MappingRegistry.get();
                for (String slash : new String[] {
                    "net/minecraft/world/item/CreativeModeTab",
                    "net/minecraft/world/item/CreativeModeTab$Builder",
                    "net/minecraft/world/item/CreativeModeTab$Row",
                    "net/minecraft/world/item/CreativeModeTab$DisplayItemsGenerator",
                    "net/minecraft/world/item/CreativeModeTab$ItemDisplayBuilder",
                    "net/minecraft/world/item/CreativeModeTab$ItemDisplayParameters",
                    "net/minecraft/world/item/CreativeModeTab$Output",
                    "net/minecraft/world/item/CreativeModeTab$TabVisibility",
                    "net/minecraft/world/item/CreativeModeTab$Type",
                }) {
                    String inter = m.mapClass(slash);
                    if (inter != null && !inter.isBlank()) {
                        names.add(inter.replace('/', '.'));
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        ClassLoader knot = getCanonicalKnotLoader();
        if (knot != null) {
            for (String n : names) {
                try {
                    Class.forName(n, true, knot);
                } catch (Throwable ignored) {
                }
            }
        }
        for (String n : names) {
            try {
                Class.forName(n, true, modCl);
            } catch (Throwable t) {
                VyneLogger.warn("[VCL] CreativeModeTab prime on mod loader (" + n + "): "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
        try {
            Class.forName("net.minecraft.class_1087", false, modCl);
            Class.forName("net.minecraft.class_1735", true, modCl);
        } catch (Throwable t) {
            VyneLogger.debug("[VCL] Server-safe MC type prime: " + t.getMessage());
        }
    }

    private static final java.util.Set<ClassLoader> WARMED_REGISTRY_FACADES =
        java.util.Collections.synchronizedSet(
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    public static void initializeLifecycle(ClassLoader cl, List<DetectedMod> mods) {
        if (initialized) return;
        synchronized (INIT_LOCK) {
            if (initialized) return;
            initialized = true;

            RegistryHealthPolicy.loadPersistedFailuresIntoSession();

            suppressLog4j();
            dev.stolym.vyne.world.MissingDataPackFilter.install();

            dev.stolym.vyne.loaders.KnotModClasspathBridge.installEmbeddedNeoForgeRegistriesBootstrap(
                VCLManager.class);

            ClassLoader knotCl = preferLoaderThatCanResolveMinecraft(
                cl != null ? cl : Thread.currentThread().getContextClassLoader());
            CANONICAL_KNOT_LOADER = knotCl;
            warmCriticalSharedGameTypes(knotCl);

            verifyClassloaderChain(knotCl);
            bootstrapMinecraft(knotCl);
            warmRegistryFacadesFromMappingsOnce(knotCl);

            net.fabricmc.api.EnvType currentEnv =
                net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType();
            boolean isClient = currentEnv == net.fabricmc.api.EnvType.CLIENT;

            preloadNeoForgeRegistries(knotCl);
            preloadCreativeModeTabFabricBuilder(knotCl);

            NeoForgeSandbox neoSandbox = new NeoForgeSandbox(knotCl);
            ForgeSandbox forgeSandbox  = new ForgeSandbox(knotCl);
            dev.stolym.vyne.vcl.VCLFieldPopulator.registerModPackage("net.neoforged.neoforge.event.level", "neoforge");
            dev.stolym.vyne.vcl.VCLFieldPopulator.registerModPackage("net.neoforged.neoforge", "neoforge");
            dev.stolym.vyne.vcl.VCLFieldPopulator.registerModPackage("net.minecraftforge", "forge");

            ClassLoader modLoader = knotCl;

            dev.stolym.vyne.compat.ServiceLoaderUtil.registerSearchClassLoader(modLoader);

            warmRegistryFacadesFromMappingsOnce(modLoader);

            warmNeoForgeSerializationAndFluidStack(knotCl, modLoader);

            warmClientOnlyGameStubs(modLoader, modLoader);

            Map<String, ModBusEntry> modBuses = Collections.synchronizedMap(new LinkedHashMap<>());

            List<DetectedMod> orderedMods = topologicallySortByDeps(mods);

            for (DetectedMod mod : orderedMods) {
                if (mod.getLoaderType() == DetectedMod.LoaderType.FABRIC) continue;
                if (mod.getLoaderType() == DetectedMod.LoaderType.VYNE)   continue;
                if (isInactive(mod.getModId())) {
                    VyneLogger.info("[VCL] Skipping inactive: " + mod.getModId());
                    continue;
                }
                if (!checkEnvironment(mod, isClient)) continue;

                try {
                    MOD_INIT_SEMAPHORE.acquire();
                    VyneLogger.info("[VCL] Initializing " + mod.getModId() + "...");

                    System.out.println("[VYNE-INIT] Initializing " + mod.getModId()
                            + " (" + mod.getLoaderType() + ") ...");

                    ClassLoader prevInit = Thread.currentThread().getContextClassLoader();
                    try {
                        Thread.currentThread().setContextClassLoader(modLoader);

                        dev.stolym.vyne.vcl.compat.VyneCompatManager.apply(mod.getModId(), modLoader);

                        String mainClassName = findModClass(
                            mod.getJarPath(), mod.getLoaderType(), isClient);
                        if (mainClassName == null) {
                            VyneLogger.warn("[VCL] No @Mod class in " + mod.getModId());
                            continue;
                        }

                        Class<?> modClass = modLoader.loadClass(mainClassName);

                        String rootPkg = mainClassName.contains(".")
                            ? mainClassName.split("\\.")[0]
                            : mainClassName;
                        dev.stolym.vyne.vcl.VCLFieldPopulator.registerModPackage(rootPkg, mod.getModId());

                        boolean isNeo = mod.getLoaderType() == DetectedMod.LoaderType.NEOFORGE;
                        Object bus = isNeo
                            ? neoSandbox.createEventBus(modLoader)
                            : forgeSandbox.createEventBus(modLoader);

                        if (bus == null) {
                            VyneLogger.warn("[VCL] Could not create event bus for " + mod.getModId());
                            continue;
                        }

                        Object modInstance;
                        ModList.beginModInstanceLoad(mod.getModId(), bus);
                        try {
                            if (isNeo) {
                                neoSandbox.wireModLoadingContext(modLoader, bus, mod.getModId());
                                modInstance = neoSandbox.instantiateMod(modClass, bus, mod.getModId(), modLoader);
                            } else {
                                forgeSandbox.wireModLoadingContext(modLoader, bus, mod.getModId());
                                modInstance = forgeSandbox.instantiateMod(modClass, bus, mod.getModId(), modLoader);
                            }
                        } finally {
                            ModList.endModInstanceLoad();
                        }

                        registerToModList(modLoader, mod.getModId());
                        modBuses.put(mod.getModId(), new ModBusEntry(bus, modLoader, modClass, modInstance));

                        dev.stolym.vyne.engine.detector.EventBusSubscriberScanner
                            .scanAndRegister(mod.getModId(), mod.getJarPath(), modLoader, bus, isClient);
                        VyneLogger.success("[VCL] " + mod.getModId() + " instantiated.");
                    } finally {
                        Thread.currentThread().setContextClassLoader(prevInit);
                    }
                } catch (Throwable t) {
                    handleModFailure(mod, t, modLoader);
                } finally {
                    MOD_INIT_SEMAPHORE.release();
                }
            }

            dispatchRegistration(modBuses, mods);
            dev.stolym.vyne.vcl.VCLFieldPopulator.retryPending(knotCl);
            dispatchPayloadRegistration(modBuses);
            dev.stolym.vyne.network.VynePayloadRegistration.logSummaryIfAny();
            dispatchFmlLifecycle(modBuses, isClient);
            dev.stolym.vyne.vcl.VCLFieldPopulator.retryPending(knotCl);
            runPostFmlBlockRegistryPipeline(modBuses);
            try {
                RegistryHealthPolicy.applyAfterRegistrationDiagnostics();
            } catch (IllegalStateException e) {
                throw e;
            } catch (Throwable t) {
                VyneLogger.warn("[RegistryHealth] applyAfterRegistrationDiagnostics: " + t.getMessage());
            }

            try {
                java.nio.file.Path summaryPath = java.nio.file.Paths.get(".vyne/initialization_summary.txt");
                java.nio.file.Files.createDirectories(summaryPath.getParent());
                StringBuilder sb = new StringBuilder();
                sb.append("=== Vyne Mod Initialization Summary ===\n\n");
                sb.append("Successfully Loaded:\n");
                for (String mid : modBuses.keySet()) sb.append("  + ").append(mid).append("\n");
                sb.append("\nFailed/Errored:\n");
                for (String mid : failedMods) sb.append("  - ").append(mid).append("\n");

                java.nio.file.Files.writeString(summaryPath, sb.toString());
                VyneLogger.info("[VCL] Initialization summary written to " + summaryPath);
            } catch (Throwable t) {
                VyneLogger.warn("[VCL] Could not write summary: " + t);
            }

            if (isClient) {
                runClientRegistrationDiagnostics(modBuses);
            }

            VyneLogger.info("[VCL] Applying VyneFluidBridge...");
            for (ModBusEntry entry : modBuses.values()) {
                 try {
                      Class<?> shimReg = entry.loader().loadClass(
                           "dev.stolym.vyne.fluid.VyneFluidTypeRegistry");
                      java.lang.reflect.Method allMethod = shimReg.getMethod("all");
                      @SuppressWarnings("unchecked")
                      Map<String, Object> shimTypes =
                            (Map<String, Object>) allMethod.invoke(null);
                      shimTypes.forEach(dev.stolym.vyne.fluid.VyneFluidTypeRegistry::register);
                 } catch (Throwable ignored) {}
            }
            dev.stolym.vyne.fluid.VyneFluidBridge.apply();

            dev.stolym.vyne.engine.detector.ModDiscoveryCache.save();

            if (isClient) {
                VyneLoadingScreen.update("Ready!", 100);
            }
        }
    }

    static List<DetectedMod> topologicallySortByDeps(List<DetectedMod> mods) {
        if (mods == null || mods.size() <= 1) return mods == null ? Collections.emptyList() : mods;

        Map<String, DetectedMod> byId = new LinkedHashMap<>();
        for (DetectedMod mod : mods) {
            byId.putIfAbsent(mod.getModId(), mod);
        }

        List<DetectedMod> result = new ArrayList<>(mods.size());
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (DetectedMod mod : mods) {
            visit(mod, byId, visited, visiting, result);
        }

        return result;
    }

    private static void visit(DetectedMod mod, Map<String, DetectedMod> byId,
                              Set<String> visited, Set<String> visiting, List<DetectedMod> out) {
        String id = mod.getModId();
        if (visited.contains(id)) return;
        if (!visiting.add(id)) {
            return;
        }
        for (String depId : mod.getDependencies().keySet()) {
            DetectedMod dep = byId.get(depId);
            if (dep != null && dep != mod) {
                visit(dep, byId, visited, visiting, out);
            }
        }
        visiting.remove(id);
        visited.add(id);
        out.add(mod);
    }

    private static boolean checkEnvironment(DetectedMod mod, boolean isClient) {
        if (mod.getSide() == DetectedMod.Environment.UNIVERSAL) return true;
        if (mod.getSide() == DetectedMod.Environment.CLIENT && !isClient) {
            VyneLogger.warn("[VCL] Skipping CLIENT-ONLY mod on server: " + mod.getModId());
            markInactive(mod.getModId());
            return false;
        }
        if (mod.getSide() == DetectedMod.Environment.SERVER && isClient) {
            VyneLogger.warn("[VCL] Skipping SERVER-ONLY mod on client: " + mod.getModId());
            markInactive(mod.getModId());
            return false;
        }
        return true;
    }

    private static void drainAllDeferredRegistersForMod(ModBusEntry mbe, List<DetectedMod> mods,
                                                        String modId) {
        if (mbe == null) {
            return;
        }
        touchModClasses(modId, mods, mbe.mainClass(), mbe.loader());
        dev.stolym.vyne.registry.VyneRegistryInjector.drainDeferredRegisters(
            mbe.modInstance(), mbe.bus(), mbe.loader());
        Path jarPath = mods.stream()
            .filter(m -> m != null && modId.equals(m.getModId()))
            .map(DetectedMod::getJarPath)
            .findFirst()
            .orElse(null);
        dev.stolym.vyne.registry.VyneRegistryInjector.drainDeferredRegistersFromModPackage(
            jarPath, mbe.mainClass(), mbe.bus(), mbe.loader());
    }

    private static void dispatchRegistration(Map<String, ModBusEntry> modBuses,
                                              List<DetectedMod> mods) {
        dev.stolym.vyne.registry.RegistrationDiagnostics.resetPhase();
        VyneLogger.info("[VCL] Dispatching registration events...");

        java.util.Set<Object> dispatchedBuses = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        try {
            for (Map.Entry<String, ModBusEntry> entry : modBuses.entrySet()) {
                String modId = entry.getKey();
                if (isInactive(modId)) continue;
                ModBusEntry mbe = entry.getValue();
                if (mbe == null || mbe.bus() == null) continue;
                try {
                    drainAllDeferredRegistersForMod(mbe, mods, modId);
                } catch (Throwable t) {
                    VyneLogger.warn("[VCL] DeferredRegister drain failed for " + modId
                            + ": " + t.getClass().getSimpleName());
                }
            }

            for (Map.Entry<String, ModBusEntry> entry : modBuses.entrySet()) {
                String modId = entry.getKey();
                if (isInactive(modId)) continue;
                ModBusEntry mbe = entry.getValue();
                if (mbe == null || mbe.bus() == null) continue;
                if (!dispatchedBuses.add(mbe.bus())) {
                    continue;
                }
                try {
                    Object bus = mbe.bus();
                    ClassLoader cl = mbe.loader();
                    setActiveBusReflective(cl, bus);

                    java.lang.reflect.Method dispatch = null;
                    for (java.lang.reflect.Method m : bus.getClass().getMethods()) {
                        if (m.getName().equals("dispatchRegistration")
                                && m.getParameterCount() == 1
                                && m.getParameterTypes()[0] == ClassLoader.class) {
                            dispatch = m;
                            break;
                        }
                    }
                    if (dispatch != null) {
                        dispatch.setAccessible(true);
                        warmRegistryFacadesFromMappingsOnce(cl);
                        ClassLoader prev = Thread.currentThread().getContextClassLoader();
                        try {
                            Thread.currentThread().setContextClassLoader(cl);
                            dispatch.invoke(bus, cl);
                        } finally {
                            Thread.currentThread().setContextClassLoader(prev);
                        }
                    }
                } catch (Throwable t) {
                    VyneLogger.error("[VCL] CRITICAL: Registration dispatch failure for " + modId 
                        + ". Disabling mod to prevent server crash.", t);
                    failedMods.add(modId);
                    markInactive(modId);
                    dev.stolym.vyne.core.FailureTracker.markFailed(modId);
                    purgeModState(modId, mbe.loader());
                }
            }

            ClassLoader knot = getCanonicalKnotLoader();
            if (knot != null) {
                try {
                    dev.stolym.vyne.registry.VyneRegistryInjector.syncAllTrackedGenericEntriesToLiveRegistry(
                        knot);
                } catch (Throwable t) {
                    VyneLogger.warn("[VCL] VyneGenericRegistry→live sync failed: "
                        + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
            }
            invokeGapFillViaKnot(knot, "DeferredRegister block gap fill");
            if (knot != null) {
                try {
                    dev.stolym.vyne.registry.VyneRegistryInjector.syncAllTrackedGenericEntriesToLiveRegistry(
                        knot);
                } catch (Throwable t) {
                    VyneLogger.warn("[VCL] VyneGenericRegistry→live sync (post gap-fill) failed: "
                        + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
            }
        } finally {
            try {
                dev.stolym.vyne.registry.RegistrationDiagnostics.logPostRegistrationReport();
            } catch (Throwable t) {
                VyneLogger.error("[RegDiag] logPostRegistrationReport failed (partial or no summary)", t);
            }
            VyneLogger.info("[VCL] [RegDiag] hook finished — if [RegDiag] BEGIN/END missing above, "
                + "this build is not the one with RegistrationDiagnostics.");
        }
    }

    private static void dispatchPayloadRegistration(Map<String, ModBusEntry> modBuses) {
        for (Map.Entry<String, ModBusEntry> entry : modBuses.entrySet()) {
            String modId = entry.getKey();
            if (isInactive(modId) || failedMods.contains(modId)) continue;
            ModBusEntry mbe = entry.getValue();
            if (mbe == null || mbe.bus() == null) continue;
            try {
                Object bus = mbe.bus();
                ClassLoader cl = mbe.loader();
                ClassLoader prev = Thread.currentThread().getContextClassLoader();
                try {
                    Thread.currentThread().setContextClassLoader(cl);
                    Class<?> evtCls = cl.loadClass(
                        "net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent");
                    Object evt = evtCls.getDeclaredConstructor().newInstance();
                    java.lang.reflect.Method postMethod = null;
                    for (java.lang.reflect.Method m : bus.getClass().getMethods()) {
                        if (m.getName().equals("post") && m.getParameterCount() == 1
                                && m.getParameterTypes()[0] == Object.class) {
                            postMethod = m;
                            break;
                        }
                    }
                    if (postMethod != null) {
                        postMethod.setAccessible(true);
                        postMethod.invoke(bus, evt);
                        VyneLogger.debug("[VCL] Posted RegisterPayloadHandlersEvent to " + modId);
                    }
                } finally {
                    Thread.currentThread().setContextClassLoader(prev);
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                VyneLogger.warn("[VCL] RegisterPayloadHandlersEvent for " + modId + ": " + unwrap(t));
            }
        }
    }

    private static void postFirstResolvedLifecycleEvent(String modId, ClassLoader cl, Object bus,
            java.lang.reflect.Method postMethod, String[] classNames) {
        for (String eventClass : classNames) {
            try {
                Class<?> evtCls = cl.loadClass(eventClass);
                Object evt = evtCls.getDeclaredConstructor().newInstance();
                postMethod.invoke(bus, evt);
                VyneLogger.debug("[VCL] Posted "
                    + eventClass.substring(eventClass.lastIndexOf('.') + 1)
                    + " to " + modId);
                return;
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                Throwable cause = unwrap(t);
                VyneLogger.warn("[VCL] Lifecycle event failed for " + modId + " ("
                    + eventClass + "): " + cause);
            }
        }
    }

    private static void runClientRegistrationDiagnostics(Map<String, ModBusEntry> modBuses) {
        for (Map.Entry<String, ModBusEntry> e : modBuses.entrySet()) {
            String modId = e.getKey();
            if (isInactive(modId)) {
                continue;
            }
            ModBusEntry mbe = e.getValue();
            if (mbe == null || mbe.loader() == null) {
                continue;
            }
            ClientRegistrationDiag.logAfterVclClient(mbe.loader(), modId);
        }
    }

    private static void runPostFmlBlockRegistryPipeline(Map<String, ModBusEntry> modBuses) {
        if (Boolean.getBoolean("vyne.registry.skipPostFmlBlockPipeline")) {
            return;
        }
        ClassLoader knot = getCanonicalKnotLoader();
        if (knot == null) {
            knot = Thread.currentThread().getContextClassLoader();
        }
        try {
            postThirdBlockRegisterEventToModBuses(modBuses, knot);
        } catch (Throwable t) {
            VyneLogger.warn("[VCL] Third block RegisterEvent pass failed: "
                + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        invokeGapFillViaKnot(knot, "post-FML block gap fill");
        if (knot != null) {
            try {
                dev.stolym.vyne.registry.VyneRegistryInjector.invalidateCache();
            } catch (Throwable ignored) {
            }
            try {
                dev.stolym.vyne.registry.VyneRegistryInjector.syncAllTrackedGenericEntriesToLiveRegistry(
                    knot);
            } catch (Throwable t) {
                VyneLogger.warn("[VCL] post-FML VyneGenericRegistry→live sync failed: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    private static Object newRegisterEventForLoader(ClassLoader modCl, Object regKey, Object liveReg) {
        if (modCl == null) {
            return new net.neoforged.neoforge.registries.RegisterEvent(regKey, liveReg);
        }
        try {
            Class<?> evtCls = modCl.loadClass("net.neoforged.neoforge.registries.RegisterEvent");
            for (java.lang.reflect.Constructor<?> ctor : evtCls.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                if (ctor.getParameterCount() == 2) {
                    return ctor.newInstance(regKey, liveReg);
                }
            }
            for (java.lang.reflect.Constructor<?> ctor : evtCls.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                if (ctor.getParameterCount() == 1) {
                    return ctor.newInstance(regKey);
                }
            }
        } catch (Throwable ignored) {
        }
        return new net.neoforged.neoforge.registries.RegisterEvent(regKey, liveReg);
    }

    private static void postThirdBlockRegisterEventToModBuses(Map<String, ModBusEntry> modBuses,
                                                             ClassLoader knot) {
        if (modBuses == null || modBuses.isEmpty() || knot == null) {
            return;
        }
        Object liveReg = dev.stolym.vyne.registry.VyneRegistryInjector.resolveRegistryForPath(
            knot, "minecraft:block");
        if (liveReg == null) {
            return;
        }
        Object regKey = dev.stolym.vyne.util.ReflectionHelper.call(liveReg, "key", "method_30517");
        if (regKey == null) {
            return;
        }
        for (Map.Entry<String, ModBusEntry> entry : modBuses.entrySet()) {
            String modId = entry.getKey();
            if (isInactive(modId) || failedMods.contains(modId)) {
                continue;
            }
            ModBusEntry mbe = entry.getValue();
            if (mbe == null || mbe.bus() == null) {
                continue;
            }
            Object bus = mbe.bus();
            ClassLoader prev = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(mbe.loader());
                Object evt = newRegisterEventForLoader(mbe.loader(), regKey, liveReg);
                java.lang.reflect.Method postMethod = null;
                for (java.lang.reflect.Method m : bus.getClass().getMethods()) {
                    if ("post".equals(m.getName()) && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == Object.class) {
                        postMethod = m;
                        break;
                    }
                }
                if (postMethod != null) {
                    postMethod.setAccessible(true);
                    postMethod.invoke(bus, evt);
                }
            } catch (Throwable t) {
                VyneLogger.debug("[VCL] Late block RegisterEvent for " + modId + ": " + t.getMessage());
            } finally {
                Thread.currentThread().setContextClassLoader(prev);
            }
        }
        VyneLogger.info("[VCL] Posted late block RegisterEvent to " + modBuses.size() + " mod bus(es).");
    }

    private static void dispatchFmlLifecycle(Map<String, ModBusEntry> modBuses,
                                              boolean isClient) {
        VyneLogger.info("[VCL] Dispatching FML lifecycle events...");
        java.util.Set<Object> dispatchedBuses = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());

        net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent clientExtEvent =
                isClient ? new net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent() : null;
        for (Map.Entry<String, ModBusEntry> entry : modBuses.entrySet()) {
            String modId = entry.getKey();
            if (isInactive(modId) || failedMods.contains(modId)) continue;
            ModBusEntry mbe = entry.getValue();
            if (mbe == null || mbe.bus() == null) continue;
            if (!dispatchedBuses.add(mbe.bus())) continue;

            try {
                Object bus     = mbe.bus();
                ClassLoader cl = mbe.loader();

                java.lang.reflect.Method postMethod = null;
                for (java.lang.reflect.Method m : bus.getClass().getMethods()) {
                    if (m.getName().equals("post") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == Object.class) {
                        postMethod = m;
                        break;
                    }
                }
                if (postMethod == null) continue;
                postMethod.setAccessible(true);

                String[] commonOrder = {
                    "net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent",
                    "net.neoforged.neoforge.fml.event.lifecycle.FMLCommonSetupEvent",
                    "net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent"
                };
                String[] sideOrder = isClient
                    ? new String[]{
                        "net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent",
                        "net.neoforged.neoforge.fml.event.lifecycle.FMLClientSetupEvent",
                        "net.neoforged.fml.event.lifecycle.FMLClientSetupEvent"
                    }
                    : new String[]{
                        "net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent",
                        "net.neoforged.neoforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent",
                        "net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent"
                    };
                String[] completeOrder = {
                    "net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent",
                    "net.neoforged.neoforge.fml.event.lifecycle.FMLLoadCompleteEvent",
                    "net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent"
                };

                postFirstResolvedLifecycleEvent(modId, cl, bus, postMethod, commonOrder);
                postFirstResolvedLifecycleEvent(modId, cl, bus, postMethod, sideOrder);

                if (isClient && clientExtEvent != null) {
                    try {
                        postMethod.invoke(bus, clientExtEvent);
                    } catch (Throwable t) {
                        VyneLogger.warn("[VCL] RegisterClientExtensions dispatch failed for "
                                + modId + ": " + unwrap(t));
                    }

                    postFreshEvent(modId, cl, bus, postMethod,
                            "net.minecraftforge.client.event.EntityRenderersEvent$RegisterRenderers");
                    postFreshEvent(modId, cl, bus, postMethod,
                            "net.minecraftforge.client.event.EntityRenderersEvent$AddLayers");
                    postFreshEvent(modId, cl, bus, postMethod,
                            "net.minecraftforge.client.event.EntityRenderersEvent$RegisterLayerDefinitions");

                    postFreshEvent(modId, cl, bus, postMethod,
                            "net.minecraftforge.client.event.ModelEvent$RegisterAdditional");
                    postFreshEvent(modId, cl, bus, postMethod,
                            "net.minecraftforge.client.event.ModelEvent$RegisterGeometryLoaders");

                    postFreshEvent(modId, cl, bus, postMethod,
                            "net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent");
                    postFreshEvent(modId, cl, bus, postMethod,
                            "net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent");
                }
                postFirstResolvedLifecycleEvent(modId, cl, bus, postMethod, completeOrder);
            } catch (Throwable t) {
                VyneLogger.warn("[VCL] Lifecycle dispatch failed for " + modId + ": " + unwrap(t));
            }
        }
        if (isClient && clientExtEvent != null) {
            try {
                dev.stolym.vyne.client.ClientExtensionsBridge.apply(clientExtEvent);
            } catch (Throwable t) {
                VyneLogger.warn("[VCL] ClientExtensionsBridge.apply failed: " + unwrap(t));
            }
        }
    }

    private static void postFreshEvent(String modId, ClassLoader cl, Object bus,
                                        java.lang.reflect.Method postMethod,
                                        String eventClassName) {
        String shortName = eventClassName.substring(eventClassName.lastIndexOf('.') + 1);
        try {
            Class<?> evtCls = cl.loadClass(eventClassName);
            Object evt = evtCls.getDeclaredConstructor().newInstance();
            postMethod.invoke(bus, evt);

            if (shortName.equals("RegisterClientTooltipComponentFactoriesEvent")
                    || shortName.equals("RegisterKeyMappingsEvent")) {
                System.out.println("[VCL-Post] " + modId + ": posted " + shortName
                    + " (" + eventClassName + ") on bus=" + bus.getClass().getName());
            }
        } catch (ClassNotFoundException cnfe) {
            if (shortName.equals("RegisterClientTooltipComponentFactoriesEvent")) {
                System.out.println("[VCL-Post] " + modId + ": " + shortName
                    + " not resolvable (" + eventClassName + ") — skipped.");
            }
        } catch (Throwable t) {
            if (shortName.equals("RegisterClientTooltipComponentFactoriesEvent")) {
                System.out.println("[VCL-Post] " + modId + ": " + shortName
                    + " dispatch threw: " + unwrap(t));
            }
            VyneLogger.warn("[VCL] " + shortName
                    + " dispatch failed for " + modId + ": " + unwrap(t));
        }
    }

    private static void touchModClasses(String modId, List<DetectedMod> mods,
                                         Class<?> modClass, ClassLoader cl) {
        try {
            DetectedMod mod = mods.stream()
                .filter(m -> m.getModId().equals(modId)).findFirst().orElse(null);
            if (mod == null || mod.getJarPath() == null) return;

            String pkgRoot = modClass.getPackageName();
            pkgRoot = pkgRoot.contains(".")
                ? pkgRoot.substring(0, pkgRoot.indexOf('.')) : pkgRoot;
            final String root = pkgRoot;

            List<String> classes = dev.stolym.vyne.engine.detector.ModDiscoveryCache
                .getCachedClasses(modId, mod.getJarPath());
            if (classes == null) {
                classes = new ArrayList<>();
                try (ZipFile zf = new ZipFile(mod.getJarPath().toFile())) {
                    Enumeration<? extends ZipEntry> entries = zf.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement().getName();
                        if (!name.endsWith(".class") || name.contains("$")) continue;
                        String cn = name.substring(0, name.length() - 6).replace('/', '.');
                        if (cn.startsWith(root)
                                || cn.contains(modId.replace("_", ""))) {
                            classes.add(cn);
                        }
                    }
                }
                dev.stolym.vyne.engine.detector.ModDiscoveryCache
                    .updateCache(modId, mod.getJarPath(), classes);
            }
            for (String cn : classes) {
                try { Class.forName(cn, false, cl); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static void setActiveBusReflective(ClassLoader cl, Object bus) {
        for (String ctxName : new String[]{
            "net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext",
            "net.neoforged.fml.ModLoadingContext"
        }) {
            try {
                Class<?> ctx = cl.loadClass(ctxName);
                for (java.lang.reflect.Method m : ctx.getDeclaredMethods()) {
                    if (m.getName().equals("setActiveBus") && m.getParameterCount() == 1) {
                        m.setAccessible(true);
                        m.invoke(null, bus);
                        return;
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void registerToModList(ClassLoader modCl, String modId) {
        try {
            Class<?> ml = modCl.loadClass("net.neoforged.fml.ModList");
            java.lang.reflect.Method reg = ml.getMethod("registerMod", String.class);
            reg.invoke(null, modId);
        } catch (Throwable ignored) {}
    }

    private static void handleModFailure(DetectedMod mod, Throwable t,
            ClassLoader modLoader) {
        Throwable cause = unwrap(t);
        String missingDep = detectMissingModLibrary(cause);
        if (missingDep != null) {
            VyneLogger.warn("[VCL] " + mod.getModId() + " skipped — missing library "
                + missingDep + ". Install the required library mod and relaunch.");
            markInactive(mod.getModId());
            failedMods.add(mod.getModId());
            dev.stolym.vyne.core.FailureTracker.markFailed(mod.getModId());
            purgeModState(mod.getModId(), modLoader);
            return;
        }

        VyneLogger.error("VYNE LIFECYCLE CRASH: " + mod.getModId(), cause);

        boolean isRemapMismatch = (cause instanceof NoSuchMethodError
            || cause instanceof NoSuchFieldError
            || cause instanceof ClassNotFoundException)
            && cause.getMessage() != null
            && (cause.getMessage().contains("class_")
                || cause.getMessage().contains("method_")
                || cause.getMessage().contains("field_"))
            && !cause.getMessage().contains("dev.stolym.vyne.vcl.shims");

        if (isRemapMismatch) {
            VyneLogger.warn("[VCL] " + mod.getModId()
                + " skipped — intermediary remap mismatch: " + cause.getMessage());
        } else {
            VyneConflictReporter.Stage stage = t instanceof java.lang.reflect.InvocationTargetException
                ? VyneConflictReporter.Stage.LIFECYCLE_HOOK
                : VyneConflictReporter.Stage.CLASSLOADING;
            VyneConflictReporter.report(mod, stage, cause.getMessage(), cause);
        }

        markInactive(mod.getModId());
        failedMods.add(mod.getModId());
        dev.stolym.vyne.core.FailureTracker.markFailed(mod.getModId());
        purgeModState(mod.getModId(), modLoader);
    }

    private static String detectMissingModLibrary(Throwable t) {
        if (t == null) return null;
        if (!(t instanceof NoClassDefFoundError) && !(t instanceof ClassNotFoundException)) {
            return null;
        }
        String msg = t.getMessage();
        if (msg == null || msg.isEmpty()) return null;
        String dotted = normalizeDescriptorLikeClassName(msg);

        if (dotted.startsWith("net.minecraft.")
            || dotted.startsWith("net.fabricmc.")
            || dotted.startsWith("com.mojang.")
            || dotted.startsWith("net.neoforged.")
            || dotted.startsWith("net.minecraftforge.")
            || dotted.startsWith("dev.stolym.vyne.")
            || dotted.startsWith("java.")) {
            return null;
        }

        return dotted.split(" ")[0];
    }

    private static String normalizeDescriptorLikeClassName(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().replace('/', '.');
        if (s.startsWith("L") && s.length() > 1
            && (s.contains("net.minecraft") || s.contains("net/minecraft"))) {
            if (s.endsWith(";")) {
                return s.substring(1, s.length() - 1);
            }
            return s.substring(1);
        }
        return s;
    }

    private static void invokeGapFillViaKnot(ClassLoader knot, String stage) {
        try {
            ClassLoader cl = knot != null ? knot : VCLManager.class.getClassLoader();
            Class<?> drClass = Class.forName(
                "net.neoforged.neoforge.registries.DeferredRegister", true, cl);
            drClass.getMethod("fillAllRegistryGapsIntoVyneGeneric").invoke(null);
        } catch (Throwable t) {
            VyneLogger.warn("[VCL] " + stage + " failed: "
                + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static void purgeModState(String modId, ClassLoader modLoader) {
        NeoForgeSandbox.evictLoader(modId);
        ForgeSandbox.evictLoader(modId);
        if (modLoader instanceof java.io.Closeable) {
            try { ((java.io.Closeable) modLoader).close(); } catch (Throwable ignored) {}
        }
        VyneLogger.warn("[VCL] Loader for '" + modId + "' closed and evicted.");
    }

    private static void suppressLog4j() {
        System.setProperty("log4j2.StatusLogger.level", "OFF");
        try {
            org.apache.logging.log4j.status.StatusLogger.getLogger()
                .setLevel(org.apache.logging.log4j.Level.OFF);
        } catch (Throwable ignored) {}
    }

    private static void warmRegistryFacadesFromMappingsOnce(ClassLoader cl) {
        if (cl == null || !WARMED_REGISTRY_FACADES.add(cl)) {
            return;
        }
        warmRegistryFacadesFromMappings(cl);
    }

    private static final String[] EAGER_FACADES_INTER = {
        "net.minecraft.class_1761",
        "net.minecraft.class_1761$class_7913",
        "net.minecraft.class_1761$class_7704",
        "net.minecraft.class_3917", 
        "net.minecraft.class_3917$class_3918", 
        "net.minecraft.class_9139", 
        "net.minecraft.class_9143", 
        "net.minecraft.class_9141", 
        "net.minecraft.class_2960", 
        "net.minecraft.class_5321", 
        "net.minecraft.class_9129", 
        "net.minecraft.class_2540", 
        "net.minecraft.class_1703", 
        "net.minecraft.class_1661", 
    };

    private static void warmRegistryFacadesFromMappings(ClassLoader cl) {
        if (cl == null) {
            return;
        }
        if (MappingRegistry.isReady()) {
            dev.stolym.vyne.mappings.MappingComposer.ComposedMappings composed = MappingRegistry.get();
            Map<String, String> namedToInter = composed.classMap();
            for (Map.Entry<String, String> e : namedToInter.entrySet()) {
                String moj = e.getKey();
                if (moj == null || moj.isEmpty()) {
                    continue;
                }
                if (!moj.contains("CreativeModeTab")
                    && !moj.contains("MenuType")
                    && !moj.contains("PacketCodec")
                    && !moj.endsWith("/ResourceLocation")) {
                    continue;
                }
                String inter = e.getValue();
                if (inter == null || inter.isEmpty()) {
                    continue;
                }
                if (inter.indexOf('$') >= 0) {
                    continue;
                }
                tryLinkFacade(inter.replace('/', '.'), cl);
            }
        }
        for (String name : EAGER_FACADES_INTER) {
            tryLinkFacade(name, cl);
        }
        VyneLogger.info("[VCL] Warmed " + WARMED_GAME_CLASSES.size()
            + " game facade class(es) on " + cl.getClass().getSimpleName()
            + " (class_1761 cached: "
            + WARMED_GAME_CLASSES.containsKey("net.minecraft.class_1761") + ")");
    }

    private static final java.util.Set<String> REPORTED_FACADE_MISSES =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private static void tryLinkFacade(String dotName, ClassLoader cl) {
        try {
            Class<?> c = Class.forName(dotName, false, cl);
            WARMED_GAME_CLASSES.putIfAbsent(dotName, c);
        } catch (Throwable missing) {
            if (REPORTED_FACADE_MISSES.add(dotName)) {
                VyneLogger.warn("[VCL] prelink could not link " + dotName + " on "
                    + (cl == null ? "null" : cl.getClass().getName())
                    + " → " + missing.getClass().getSimpleName()
                    + ": " + String.valueOf(missing.getMessage()));
            }
        }
    }

    private static void warmNeoForgeSerializationAndFluidStack(ClassLoader knotCl,
            ClassLoader neoLoader) {
        if (knotCl != null) {
            for (String n : new String[] {
                "com.mojang.serialization.Codec",
                "com.mojang.datafixers.kinds.App",
            }) {
                try {
                    Class.forName(n, true, knotCl);
                } catch (Throwable t) {
                    VyneLogger.warn("[VCL] warmNeoForgeSerialization(mojang): could not init " + n + ": " + t);
                }
            }
        }
        if (neoLoader != null) {
            try {
                Class.forName("net.neoforged.neoforge.fluids.FluidStack", true, neoLoader);
            } catch (Throwable t) {
                VyneLogger.warn("[VCL] warmNeoForgeSerialization(FluidStack) neo loader: " + t);
                if (knotCl != null) {
                    try {
                        Class.forName("net.neoforged.neoforge.fluids.FluidStack", true, knotCl);
                    } catch (Throwable t2) {
                        VyneLogger.warn("[VCL] warmNeoForgeSerialization(FluidStack) knot: " + t2);
                    }
                }
            }
        } else if (knotCl != null) {
            try {
                Class.forName("net.neoforged.neoforge.fluids.FluidStack", true, knotCl);
            } catch (Throwable t) {
                VyneLogger.warn("[VCL] warmNeoForgeSerialization(FluidStack) knot-only: " + t);
            }
        }
    }

    private static void warmCriticalSharedGameTypes(ClassLoader knotCl) {
        if (knotCl == null) {
            return;
        }
        String[] names = {
            "com.mojang.serialization.Codec",
            "com.mojang.datafixers.kinds.App",
            "net.minecraft.class_6862",
        };
        for (String n : names) {
            try {
                Class.forName(n, false, knotCl);
            } catch (Throwable t) {
                VyneLogger.warn("[VCL] warmCriticalSharedGameTypes: could not load " + n + ": " + t);
            }
        }
    }

    private static ClassLoader preferLoaderThatCanResolveMinecraft(ClassLoader primaryHint) {
        for (ClassLoader probe : FabricLoaderProbes.reflectionProbesForFabricLauncherBase()) {
            if (probe == null) {
                continue;
            }
            try {
                Class<?> flb = Class.forName(
                    "net.fabricmc.loader.impl.launch.FabricLauncherBase", false, probe);
                Object launcher = flb.getMethod("getLauncher").invoke(null);
                if (launcher == null) {
                    continue;
                }
                Object tcl = launcher.getClass().getMethod("getTargetClassLoader").invoke(launcher);
                if (tcl instanceof ClassLoader fabricTarget) {
                    try {
                        Class.forName("net.minecraft.class_2960", false, fabricTarget);
                        return fabricTarget;
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (primaryHint != null) {
            try {
                Class.forName("net.minecraft.class_2960", false, primaryHint);
                return primaryHint;
            } catch (Throwable ignored) {
            }
        }
        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
        if (ctx != null) {
            try {
                Class.forName("net.minecraft.class_2960", false, ctx);
                return ctx;
            } catch (Throwable ignored) {
            }
        }
        ClassLoader vcl = VCLManager.class.getClassLoader();
        if (vcl != null) {
            try {
                Class.forName("net.minecraft.class_2960", false, vcl);
                return vcl;
            } catch (Throwable ignored) {
            }
        }
        return primaryHint != null ? primaryHint : vcl;
    }

    private static void verifyClassloaderChain(ClassLoader knotCl) {
        try {
            Class<?> knotPair = knotCl.loadClass("org.apache.commons.lang3.tuple.Pair");
            Class<?> appPair  = ClassLoader.getSystemClassLoader()
                .loadClass("org.apache.commons.lang3.tuple.Pair");
            if (knotPair == appPair) {
                VyneLogger.info("[VCL] Classloader chain verified: Pair identity matches.");
            } else {
                if (dev.stolym.vyne.util.VyneLogLevel.isVerbose()) {
                    VyneLogger.info("[VCL] Pair identity mismatch (non-fatal, verbose only). "
                        + "Knot: " + knotPair.getClassLoader()
                        + " | App: " + appPair.getClassLoader());
                }
            }
        } catch (Throwable t) {
            VyneLogger.warn("[VCL] Classloader chain check failed: " + t.getMessage());
        }
    }

    private static void bootstrapMinecraft(ClassLoader knotCl) {
        primeSharedConstants(knotCl);

        String[] classes = new String[] {
            MappingRegistry.isReady()
                ? MappingRegistry.get().mapClass("net/minecraft/server/Bootstrap").replace('/', '.')
                : "net.minecraft.class_2966",
            "net.minecraft.class_2966",
            "net.minecraft.server.Bootstrap",
        };
        for (String cls : classes) {
            if (cls == null || cls.isBlank()) {
                continue;
            }
            try {
                Class<?> bootstrap = Class.forName(cls, false, knotCl);
                for (java.lang.reflect.Method boot : bootstrap.getDeclaredMethods()) {
                    int mod = boot.getModifiers();
                    if (!java.lang.reflect.Modifier.isStatic(mod)
                        || boot.getParameterCount() != 0
                        || boot.getReturnType() != void.class) {
                        continue;
                    }
                    String n = boot.getName();
                    if (!"bootStrap".equals(n) && !"bootstrap".equals(n) && !"method_12851".equals(n)) {
                        continue;
                    }
                    boot.setAccessible(true);
                    try {
                        boot.invoke(null);
                        VyneLogger.info("[VCL] Minecraft Bootstrap verified via " + cls + "." + n + "().");
                        return;
                    } catch (java.lang.reflect.InvocationTargetException ite) {
                        Throwable root = ite.getCause() != null ? ite.getCause() : ite;
                        Throwable deep = root;
                        while (deep != null) {
                            if (deep instanceof ExceptionInInitializerError eiie && eiie.getCause() != null) {
                                deep = eiie.getCause();
                                continue;
                            }
                            if (deep.getCause() != null && deep.getCause() != deep) {
                                VyneLogger.error("[VCL] bootstrap cause chain ("
                                    + deep.getClass().getName() + "): "
                                    + String.valueOf(deep.getMessage()));
                                for (StackTraceElement ste : deep.getStackTrace()) {
                                    VyneLogger.error("[VCL]     at " + ste);
                                }
                                deep = deep.getCause();
                            } else {
                                VyneLogger.error("[VCL] bootstrap root cause ("
                                    + deep.getClass().getName() + "): "
                                    + String.valueOf(deep.getMessage()));
                                for (StackTraceElement ste : deep.getStackTrace()) {
                                    VyneLogger.error("[VCL]     at " + ste);
                                }
                                break;
                            }
                        }
                        VyneLogger.warn("[VCL] Bootstrap invocation failed on " + cls + "." + n
                            + "() — continuing to next candidate.");
                    }
                }
            } catch (Throwable t) {
                VyneLogger.warn("[VCL] Bootstrap candidate '" + cls + "' skipped: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
        VyneLogger.warn("[VCL] Bootstrap check failed: could not run net.minecraft.server.Bootstrap on Knot.");
    }

    private static void primeSharedConstants(ClassLoader knotCl) {
        String[] names = new String[] {
            MappingRegistry.isReady()
                ? MappingRegistry.get().mapClass("net/minecraft/SharedConstants").replace('/', '.')
                : "net.minecraft.class_155",
            "net.minecraft.class_155",
            "net.minecraft.SharedConstants",
        };
        String[] methodNames = new String[] { "tryDetectVersion", "method_36208" };
        for (String cls : names) {
            if (cls == null || cls.isBlank()) continue;
            try {
                Class<?> sc = Class.forName(cls, true, knotCl);
                for (String m : methodNames) {
                    try {
                        java.lang.reflect.Method detect = sc.getDeclaredMethod(m);
                        detect.setAccessible(true);
                        detect.invoke(null);
                        VyneLogger.info("[VCL] SharedConstants primed via " + cls + "." + m + "().");
                        return;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            } catch (Throwable t) {
                VyneLogger.warn("[VCL] SharedConstants prime candidate '" + cls + "' skipped: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
        VyneLogger.warn("[VCL] Could not prime SharedConstants — entity/datafixer init may fail.");
    }

    private static void preloadNeoForgeRegistries(ClassLoader knotCl) {
        if (knotCl == null) return;
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(knotCl);
            Class.forName("net.neoforged.neoforge.registries.NeoForgeRegistries$Keys", true, knotCl);
            VyneLogger.info("[VCL] NeoForgeRegistries$Keys pre-initialized on Knot-aligned loader.");
        } catch (Throwable t) {
            VyneLogger.warn("[VCL] NeoForge registry Keys pre-init: " + t.getMessage());
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    private static void preloadCreativeModeTabFabricBuilder(ClassLoader knotCl) {
        if (knotCl == null) {
            return;
        }
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(knotCl);
            String tab = "net.minecraft.class_1761";
            String row = "net.minecraft.class_1761$class_7915";
            if (MappingRegistry.isReady()) {
                String t = MappingRegistry.get().mapClass("net/minecraft/world/item/CreativeModeTab");
                if (t != null && !t.isBlank()) {
                    tab = t.replace('/', '.');
                }
                String r = MappingRegistry.get().mapClass("net/minecraft/world/item/CreativeModeTab$Row");
                if (r != null && !r.isBlank()) {
                    row = r.replace('/', '.');
                }
            }
            Class<?> tabC = Class.forName(tab, true, knotCl);
            Class<?> rowC = Class.forName(row, true, knotCl);
            String builder = "net.minecraft.class_1761$class_7913";
            if (MappingRegistry.isReady()) {
                try {
                    String mapped = MappingRegistry.get().mapClass(
                        "net/minecraft/world/item/CreativeModeTab$Builder");
                    if (mapped != null && !mapped.isBlank()) {
                        builder = mapped.replace('/', '.');
                    }
                } catch (Throwable ignored) {
                }
            }
            Class<?> builderC = Class.forName(builder, true, knotCl);
            rememberWarmedGameClassAliases(builderC,
                builder,
                "net.minecraft.class_1761$class_7913",
                "net.minecraft.world.item.CreativeModeTab$Builder");
            if (MappingRegistry.isReady()) {
                try {
                    dev.stolym.vyne.mappings.MappingComposer.ComposedMappings m = MappingRegistry.get();
                    String interB = m.mapClass("net/minecraft/world/item/CreativeModeTab$Builder");
                    if (interB != null && !interB.isBlank()) {
                        rememberWarmedGameClassAliases(builderC, interB.replace('/', '.'));
                    }
                    String mojFromInter = m.unmapClass("net/minecraft/class_1761$class_7913");
                    if (mojFromInter != null && !mojFromInter.isBlank()) {
                        rememberWarmedGameClassAliases(builderC, mojFromInter.replace('/', '.'));
                    }
                } catch (Throwable ignored) {
                }
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object top = java.lang.Enum.valueOf((Class<? extends java.lang.Enum>) rowC, "TOP");
            java.lang.reflect.Method factory = tabC.getMethod("method_47307", rowC, int.class);
            factory.invoke(null, top, 0);
            rememberWarmedGameClassAliases(tabC,
                tab,
                "net.minecraft.class_1761",
                "net.minecraft.world.item.CreativeModeTab");
            rememberWarmedGameClassAliases(rowC,
                row,
                "net.minecraft.class_1761$class_7915",
                "net.minecraft.world.item.CreativeModeTab$Row");
            VyneLogger.info("[VCL] CreativeModeTab Fabric builder stack pre-initialized on Knot.");
        } catch (Throwable t) {
            VyneLogger.warn("[VCL] CreativeModeTab pre-init: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    private static Throwable unwrap(Throwable t) {
        Throwable cause = t;
        while (cause instanceof java.lang.reflect.InvocationTargetException ite
                && ite.getCause() != null) {
            cause = ite.getCause();
        }
        if (cause instanceof ExceptionInInitializerError eiie && eiie.getCause() != null) {
            cause = eiie.getCause();
        }
        return cause;
    }

    private static String findModClass(Path jarPath, DetectedMod.LoaderType type,
                                        boolean isClient) throws Exception {
        String annotationDesc = type == DetectedMod.LoaderType.FORGE
            ? "Lnet/minecraftforge/fml/common/Mod;"
            : "Lnet/neoforged/fml/common/Mod;";

        String commonFallback = null;
        String sideMatch = null;
        String oppositeSideMatch = null;

        try (ZipFile zf = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                try (InputStream is = zf.getInputStream(entry)) {
                    ClassReader cr = new ClassReader(is);
                    ClassNode cn = new ClassNode();
                    cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG
                        | ClassReader.SKIP_FRAMES);
                    if (cn.visibleAnnotations == null) continue;
                    for (AnnotationNode ann : cn.visibleAnnotations) {
                        if (!ann.desc.equals(annotationDesc)) continue;
                        ModDist dist = readModDist(ann);
                        String qualified = cn.name.replace('/', '.');
                        switch (dist) {
                            case COMMON -> {
                                if (sideMatch == null) sideMatch = qualified;
                                if (commonFallback == null) commonFallback = qualified;
                            }
                            case CLIENT_ONLY -> {
                                if (isClient && sideMatch == null) sideMatch = qualified;
                                else if (!isClient && oppositeSideMatch == null)
                                    oppositeSideMatch = qualified;
                            }
                            case SERVER_ONLY -> {
                                if (!isClient && sideMatch == null) sideMatch = qualified;
                                else if (isClient && oppositeSideMatch == null)
                                    oppositeSideMatch = qualified;
                            }
                        }
                    }
                }
            }
        }

        if (sideMatch != null) return sideMatch;
        if (commonFallback != null) return commonFallback;

        return oppositeSideMatch;
    }

    private enum ModDist { COMMON, CLIENT_ONLY, SERVER_ONLY }

    private static ModDist readModDist(AnnotationNode ann) {
        if (ann.values == null) return ModDist.COMMON;
        for (int i = 0; i + 1 < ann.values.size(); i += 2) {
            Object key = ann.values.get(i);
            if (!"dist".equals(key)) continue;
            Object val = ann.values.get(i + 1);
            if (!(val instanceof List<?> list)) continue;
            boolean hasClient = false;
            boolean hasServer = false;
            for (Object element : list) {
                if (!(element instanceof String[] pair) || pair.length < 2) continue;

                String name = pair[1];
                if ("CLIENT".equals(name)) hasClient = true;
                else if ("DEDICATED_SERVER".equals(name)) hasServer = true;
            }
            if (hasClient && hasServer) return ModDist.COMMON;
            if (hasClient) return ModDist.CLIENT_ONLY;
            if (hasServer) return ModDist.SERVER_ONLY;
            return ModDist.COMMON;
        }
        return ModDist.COMMON;
    }
}
