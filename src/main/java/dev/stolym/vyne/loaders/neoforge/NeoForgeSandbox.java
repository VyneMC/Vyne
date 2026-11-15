/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.loaders.neoforge;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.util.VyneLogger;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NeoForgeSandbox {
    private final ClassLoader parentCl;

    private static final Map<String, dev.stolym.vyne.vcl.RemappingClassLoader> PER_MOD =
        new ConcurrentHashMap<>();
    private static volatile URLClassLoader globalLoader;

    private static final java.util.Set<String> PROTECTED_ROOTS = java.util.Set.of(
        "net.", "com.", "dev.", "java.", "javax.", "sun.", "jdk.", "org.", "io.", "it."
    );

    public NeoForgeSandbox() {
        this.parentCl = Thread.currentThread().getContextClassLoader();
    }

    public NeoForgeSandbox(ClassLoader parent) {
        this.parentCl = parent != null ? parent : Thread.currentThread().getContextClassLoader();
    }

    public dev.stolym.vyne.vcl.RemappingClassLoader getIsolatedLoader(
            DetectedMod mod, ClassLoader parent) {
        if (!dev.stolym.vyne.vcl.VCLManager.LEGACY_VCL_ENABLED) {
            VyneLogger.info("[Sandbox] vyne.vcl.legacy=false — refusing to build a legacy "
                + this.getClass().getSimpleName() + " loader; unified Knot handles classloading.");
            return null;
        }
        return PER_MOD.computeIfAbsent(mod.getModId(), id -> {
            try {
                URL vyneJar = NeoForgeSandbox.class.getProtectionDomain()
                    .getCodeSource().getLocation();
                scanRootPackages(mod.getJarPath().toFile());
                URL[] urls = {vyneJar, mod.getJarPath().toUri().toURL()};
                VyneLogger.debug("[NeoForgeSandbox] Isolated loader for: " + id);
                return new dev.stolym.vyne.vcl.RemappingClassLoader(urls, parent);
            } catch (Exception e) {
                VyneLogger.error("[NeoForgeSandbox] Failed isolated loader for " + id, e);
                return null;
            }
        });
    }

    public URLClassLoader getGlobalLoader(List<DetectedMod> mods, ClassLoader parent) {
        if (globalLoader != null) return globalLoader;
        if (!dev.stolym.vyne.vcl.VCLManager.LEGACY_VCL_ENABLED) {
            VyneLogger.info("[Sandbox] vyne.vcl.legacy=false — refusing to build a legacy "
                + this.getClass().getSimpleName() + " loader; unified Knot handles classloading.");
            return null;
        }
        VyneLogger.setPhase("NEOFORGE-LOAD");
        VyneLogger.info("[NeoForgeSandbox] Creating global sandbox...");
        try {
            java.util.List<URL> urls = new java.util.ArrayList<>();
            urls.add(NeoForgeSandbox.class.getProtectionDomain().getCodeSource().getLocation());

            java.util.List<DetectedMod> neoMods = new java.util.ArrayList<>();
            for (DetectedMod mod : mods) {
                if (mod.getLoaderType() == DetectedMod.LoaderType.NEOFORGE) {
                    neoMods.add(mod);
                    scanRootPackages(mod.getJarPath().toFile());
                }
            }
            var split = dev.stolym.vyne.engine.dynamic.DynamicEngineHost
                .preparedClasspathSplit(neoMods, parent);
            dev.stolym.vyne.loaders.KnotModClasspathBridge.addRewriteJars(
                split.knotRewriteJarPaths());
            java.util.List<URL> prepared = dev.stolym.vyne.engine.dynamic.DynamicEngineHost
                .remappingClasspathUrls(split);
            urls.addAll(prepared);

            globalLoader = new dev.stolym.vyne.vcl.RemappingClassLoader(
                urls.toArray(new URL[0]), parent);
            injectLegacyNeoForgeShims(globalLoader);
            VyneLogger.success("[NeoForgeSandbox] Global sandbox: "
                + neoMods.size() + " mod(s) prepared via dynamic engine, "
                + (urls.size() - 1) + " classpath entries total.");
            return globalLoader;
        } catch (Exception e) {
            VyneLogger.error("[NeoForgeSandbox] Failed global sandbox: " + e.getMessage(), e);
            return null;
        }
    }

    private static void injectLegacyNeoForgeShims(ClassLoader modCl) {
        if (modCl == null) {
            return;
        }
        ClassLoader[] candidates = { modCl };
        for (ClassLoader cand : candidates) {
            if (cand == null) {
                continue;
            }
            try {
                Class<?> neoForgeClass = Class.forName(
                    "net.neoforged.neoforge.common.NeoForge", true, cand);
                java.lang.reflect.Field[] fields = neoForgeClass.getDeclaredFields();
                for (java.lang.reflect.Field f : fields) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    Object val = f.get(null);
                    if (val == null) continue;
                    dev.stolym.vyne.vcl.VyneFieldRegistry.register(
                        neoForgeClass.getName() + "#" + f.getName(), val);
                }
                String[] busFieldNames = {"EVENT_BUS", "MOD_EVENT_BUS"};
                for (String busFieldName : busFieldNames) {
                    if (dev.stolym.vyne.vcl.VyneFieldRegistry.resolve(
                            neoForgeClass.getName(), busFieldName) != null) continue;
                    try {
                        Class<?> busCls = Class.forName(
                            "net.neoforged.bus.api.BusBuilder", true, cand);
                        Object builder = busCls.getMethod("builder").invoke(null);
                        Object bus = builder.getClass().getMethod("build").invoke(builder);
                        dev.stolym.vyne.vcl.VyneFieldRegistry.register(
                            neoForgeClass.getName() + "#" + busFieldName, bus);
                    } catch (Throwable ignored) {}
                }
                return;
            } catch (Throwable ignored) {
            }
        }
        VyneLogger.warn("[NeoForgeSandbox] Legacy shim injection failed on mod ClassLoader.");
    }

    public URLClassLoader load(DetectedMod mod) {
        return getGlobalLoader(java.util.List.of(mod), parentCl);
    }

    public static void evictLoader(String modId) {
        dev.stolym.vyne.vcl.RemappingClassLoader loader = PER_MOD.remove(modId);
        if (loader != null) {
            try { loader.close(); } catch (Exception ignored) {}
        }
    }

    public static void resetGlobalLoader() {
        if (globalLoader != null) {
            try { globalLoader.close(); } catch (Exception ignored) {}
            globalLoader = null;
        }
        dev.stolym.vyne.vcl.RemappingClassLoader.resetLoaderStaticState();
    }

    public Object createEventBus(ClassLoader modCl) {
        Throwable primaryFailure = null;
        for (ClassLoader cl : new ClassLoader[]{modCl, NeoForgeSandbox.class.getClassLoader()}) {
            if (cl == null) continue;
            try {
                Class<?> cls = cl.loadClass("net.neoforged.bus.api.VyneEventBus");
                return cls.getMethod("global").invoke(null);
            } catch (Throwable t) {
                if (primaryFailure == null) primaryFailure = t;
            }
        }
        if (primaryFailure != null) {
            VyneLogger.warn("[NeoForgeSandbox] VyneEventBus load failed: " + primaryFailure);
        }
        try {
            Object bus = dev.stolym.vyne.compat.EventBusBridge.busApiBus();
            if (bus != null) return bus;
        } catch (Throwable ignored) {}
        for (String candidate : new String[]{
            "net.neoforged.bus.api.BusBuilder",
            "net.neoforged.bus.EventBus"
        }) {
            for (ClassLoader cl : new ClassLoader[]{modCl, NeoForgeSandbox.class.getClassLoader()}) {
                if (cl == null) continue;
                try {
                    Class<?> cls = cl.loadClass(candidate);
                    if (candidate.contains("BusBuilder")) {
                        Object builder = cls.getMethod("builder").invoke(null);
                        return builder.getClass().getMethod("build").invoke(builder);
                    }
                    return cls.getDeclaredConstructor().newInstance();
                } catch (Throwable ignored) {}
            }
        }
        VyneLogger.warn("[NeoForgeSandbox] Could not create event bus via known types.");
        return null;
    }

    public boolean wireModLoadingContext(ClassLoader modCl, Object bus, String modId) {
        for (String ctxClass : new String[]{
            "net.neoforged.neoforge.fml.javafmlmod.FMLJavaModLoadingContext",
            "net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext",
            "net.neoforged.fml.ModLoadingContext"
        }) {
            try {
                Class<?> ctx = modCl.loadClass(ctxClass);
                try {
                    java.lang.reflect.Method setId =
                        ctx.getDeclaredMethod("setActiveModId", String.class);
                    setId.setAccessible(true);
                    setId.invoke(null, modId);
                } catch (Throwable ignored) {}
                for (java.lang.reflect.Method m : ctx.getDeclaredMethods()) {
                    if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                    if (!m.getName().equals("setActiveBus") && !m.getName().equals("set"))
                        continue;
                    m.setAccessible(true);
                    if (m.getParameterCount() == 1) {
                        m.invoke(null, bus);
                        VyneLogger.debug("[NeoForgeSandbox] Wired bus via " + ctxClass
                            + " for " + modId);
                        return true;
                    }
                    if (m.getParameterCount() == 2
                            && m.getParameterTypes()[0] == String.class) {
                        m.invoke(null, modId, bus);
                        return true;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public Object instantiateMod(Class<?> modClass, Object bus, String modId,
                                  ClassLoader modCl) throws Throwable {
        for (String busType : new String[]{
            "net.neoforged.bus.api.IEventBus",
            "net.neoforged.neoforge.eventbus.api.IEventBus"
        }) {
            try {
                Class<?> busInterface = modCl.loadClass(busType);
                java.lang.reflect.Constructor<?> ctor =
                    modClass.getDeclaredConstructor(busInterface);
                ctor.setAccessible(true);
                return ctor.newInstance(bus);
            } catch (NoSuchMethodException ignored) {}
        }

        java.lang.reflect.Constructor<?> best = null;
        for (java.lang.reflect.Constructor<?> c : modClass.getDeclaredConstructors()) {
            if (best == null || c.getParameterCount() < best.getParameterCount()) best = c;
        }
        if (best == null) throw new NoSuchMethodException("No constructor in " + modClass);

        best.setAccessible(true);
        Object[] args = buildArgs(best, bus, modId, modCl);
        return best.newInstance(args);
    }

    private Object[] buildArgs(java.lang.reflect.Constructor<?> ctor, Object bus,
                                String modId, ClassLoader modCl) {
        Object[] args = new Object[ctor.getParameterCount()];
        for (int i = 0; i < args.length; i++) {
            Class<?> pt = ctor.getParameterTypes()[i];
            String n = pt.getName();
            if (n.contains("IEventBus") || n.contains("EventBus")) {
                args[i] = bus;
            } else if (n.contains("ModContainer")) {
                try {
                    args[i] = pt.getDeclaredConstructor(String.class, Object.class)
                        .newInstance(modId, bus);
                } catch (Throwable primary) {
                    try {
                        args[i] = pt.getDeclaredConstructor(String.class).newInstance(modId);
                    } catch (Throwable fallback) { args[i] = null; }
                }
            } else if (n.contains("FMLJavaModLoadingContext")
                    || n.contains("ModLoadingContext")) {
                try {
                    args[i] = pt.getMethod("get").invoke(null);
                } catch (Throwable t) { args[i] = null; }
            } else {
                args[i] = null;
            }
        }
        return args;
    }

    private static void scanRootPackages(java.io.File jar) {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jar)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zf.entries();
            java.util.Set<String> seen = new java.util.HashSet<>();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.endsWith(".class") || !name.contains("/")) continue;
                String root = name.substring(0, name.indexOf('/')) + ".";
                if (!seen.add(root)) continue;
                boolean prot = PROTECTED_ROOTS.stream().anyMatch(root::startsWith);
                if (!prot) {
                    dev.stolym.vyne.vcl.RemappingClassLoader.registerOverridePrefix(root);
                }
            }
        } catch (Exception ignored) {}
    }
}
