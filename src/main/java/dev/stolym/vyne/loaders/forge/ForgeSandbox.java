/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.loaders.forge;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.util.VyneLogger;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ForgeSandbox {
    private final ClassLoader parentCl;

    private static volatile URLClassLoader globalLoader;
    private static final Map<String, dev.stolym.vyne.vcl.RemappingClassLoader> PER_MOD =
        new ConcurrentHashMap<>();

    private static final java.util.Set<String> PROTECTED_ROOTS = java.util.Set.of(
        "net.", "com.", "dev.", "java.", "javax.", "sun.", "jdk.", "org.", "io.", "it."
    );

    public ForgeSandbox() {
        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
        this.parentCl = ctx != null ? ctx : ForgeSandbox.class.getClassLoader();
    }

    public ForgeSandbox(ClassLoader parent) {
        this.parentCl = parent != null ? parent : ForgeSandbox.class.getClassLoader();
    }

    public URLClassLoader getGlobalLoader(List<DetectedMod> mods, ClassLoader parent) {
        if (globalLoader != null) return globalLoader;
        if (!dev.stolym.vyne.vcl.VCLManager.LEGACY_VCL_ENABLED) {
            VyneLogger.info("[Sandbox] vyne.vcl.legacy=false — refusing to build a legacy "
                + this.getClass().getSimpleName() + " loader; unified Knot handles classloading.");
            return null;
        }
        VyneLogger.setPhase("FORGE-LOAD");
        VyneLogger.info("[ForgeSandbox] Creating global sandbox...");
        try {
            java.util.List<URL> urls = new java.util.ArrayList<>();
            urls.add(ForgeSandbox.class.getProtectionDomain().getCodeSource().getLocation());

            java.util.List<DetectedMod> forgeMods = new java.util.ArrayList<>();
            for (DetectedMod mod : mods) {
                if (mod.getLoaderType() == DetectedMod.LoaderType.FORGE) {
                    forgeMods.add(mod);
                    scanRootPackages(mod.getJarPath().toFile());
                }
            }
            var split = dev.stolym.vyne.engine.dynamic.DynamicEngineHost
                .preparedClasspathSplit(forgeMods, parent);
            dev.stolym.vyne.loaders.KnotModClasspathBridge.addRewriteJars(
                split.knotRewriteJarPaths());
            java.util.List<URL> prepared = dev.stolym.vyne.engine.dynamic.DynamicEngineHost
                .remappingClasspathUrls(split);
            urls.addAll(prepared);

            globalLoader = new dev.stolym.vyne.vcl.RemappingClassLoader(
                urls.toArray(new URL[0]), parent);
            captureForgeStatics(globalLoader);
            VyneLogger.success("[ForgeSandbox] Global sandbox: "
                + forgeMods.size() + " mod(s) prepared via dynamic engine, "
                + (urls.size() - 1) + " classpath entries total.");
            return globalLoader;
        } catch (Exception e) {
            VyneLogger.error("[ForgeSandbox] Failed global sandbox: " + e.getMessage(), e);
            return null;
        }
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
                URL vyneJar = ForgeSandbox.class.getProtectionDomain()
                    .getCodeSource().getLocation();
                scanRootPackages(mod.getJarPath().toFile());
                URL[] urls = {vyneJar, mod.getJarPath().toUri().toURL()};
                return new dev.stolym.vyne.vcl.RemappingClassLoader(urls, parent);
            } catch (Exception e) {
                VyneLogger.error("[ForgeSandbox] Failed isolated loader for " + id, e);
                return null;
            }
        });
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
        for (ClassLoader cl : new ClassLoader[]{modCl, ForgeSandbox.class.getClassLoader()}) {
            if (cl == null) continue;
            try {
                Class<?> cls = cl.loadClass("net.neoforged.bus.api.VyneEventBus");
                return cls.getMethod("global").invoke(null);
            } catch (Throwable ignored) {}
        }
        try {
            Object bus = dev.stolym.vyne.compat.EventBusBridge.forgeBus();
            if (bus != null) return bus;
        } catch (Throwable ignored) {}
        for (String candidate : new String[]{
            "net.minecraftforge.eventbus.api.BusBuilder",
            "net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext",
            "net.minecraftforge.eventbus.EventBus"
        }) {
            try {
                Class<?> cls = modCl.loadClass(candidate);
                if (candidate.contains("BusBuilder")) {
                    Object builder = cls.getMethod("builder").invoke(null);
                    return builder.getClass().getMethod("build").invoke(builder);
                }
            } catch (Throwable ignored) {}
        }
        VyneLogger.warn("[ForgeSandbox] Could not create Forge event bus.");
        return null;
    }

    public boolean wireModLoadingContext(ClassLoader modCl, Object bus, String modId) {
        try {
            Class<?> fmlCtx = modCl.loadClass(
                "net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext");
            java.lang.reflect.Method setMod = fmlCtx.getMethod("setActiveModId", String.class);
            setMod.invoke(null, modId);
        } catch (Throwable ignored) {}
        for (String ctxName : new String[]{
            "net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext",
            "net.minecraftforge.fml.ModLoadingContext"
        }) {
            try {
                Class<?> ctx = modCl.loadClass(ctxName);
                for (java.lang.reflect.Method m : ctx.getDeclaredMethods()) {
                    if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                    if (!m.getName().equals("setActiveBus") && !m.getName().equals("set"))
                        continue;
                    m.setAccessible(true);
                    if (m.getParameterCount() == 1) { m.invoke(null, bus); return true; }
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
            "net.minecraftforge.eventbus.api.IEventBus",
            "net.neoforged.bus.api.IEventBus"
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
                    args[i] = pt.getDeclaredConstructor(String.class).newInstance(modId);
                } catch (Throwable t) { args[i] = null; }
            } else if (n.contains("ModLoadingContext")
                    || n.contains("FMLJavaModLoadingContext")) {
                try {
                    args[i] = pt.getMethod("get").invoke(null);
                } catch (Throwable t) { args[i] = null; }
            } else {
                args[i] = null;
            }
        }
        return args;
    }

    private static void captureForgeStatics(ClassLoader modCl) {
        try {
            Class<?> mf = modCl.loadClass("net.minecraftforge.common.MinecraftForge");
            for (java.lang.reflect.Field f : mf.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object val = f.get(null);
                if (val == null) continue;
                dev.stolym.vyne.vcl.VyneFieldRegistry.register(mf.getName() + "#" + f.getName(), val);
            }
        } catch (Throwable ignored) {
        }
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
