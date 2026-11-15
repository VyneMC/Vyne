/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl;

import dev.stolym.vyne.mappings.MappingComposer;
import net.fabricmc.loader.impl.transformer.VyneRemapper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

@Deprecated
public final class RemappingClassLoader extends URLClassLoader {
    static {
        dev.stolym.vyne.mappings.MappingRegistry.onMappingsChanged(RemappingClassLoader::invalidateCache);
    }

    private static final String[] PARENT_DELEGATE_PREFIXES = {
        "java.", "javax.", "sun.", "jdk.",
        "org.xml.sax.", "org.w3c.dom.", "com.sun.",
        "org.spongepowered.asm.",
        "net.fabricmc.",
        "dev.stolym.vyne.util.",
        "dev.stolym.vyne.core.",
        "dev.stolym.vyne.registry.",
        "dev.stolym.vyne.compat.",
        "dev.stolym.vyne.client.",
        "com.mojang.",
        "org.apache.commons.lang3.",
        "org.lwjgl.",
        "net.minecraft.",
        "net.neoforged.bus.",
        "net.neoforged.neoforge.common.",
        "net.neoforged.neoforge.registries.",
        "net.neoforged.neoforge.network.",
        "net.neoforged.neoforge.event.",
        "net.neoforged.neoforge.capabilities.",
        "net.neoforged.neoforge.energy.",
        "net.neoforged.neoforge.items.",
        "net.neoforged.neoforge.client.",
        "net.neoforged.neoforge.eventbus.",
        "net.neoforged.neoforge.fml.",
        "net.neoforged.neoforge.internal.",
        "net.neoforged.neoforge.server.",
        "net.neoforged.neoforge.data.",
        "net.neoforged.neoforge.entity.",
        "net.neoforged.fml.",
        "net.neoforged.neoforgespi.",
        "net.neoforged.api.",
        "net.minecraftforge.",
    };

    private static final String[] SHIM_FORCE_PREFIXES = {
        "net.neoforged.neoforge.items.",
        "net.neoforged.neoforge.entity.",
        "net.neoforged.neoforge.registries.",
        "net.neoforged.neoforge.fluids.",
        "net.neoforged.neoforge.common.util.",
        "net.neoforged.neoforge.common.Tags",
        "net.neoforged.neoforge.common.ModConfigSpec",
        "net.neoforged.neoforge.common.NeoForgeMod",
        "net.minecraftforge.items.",
        "net.minecraftforge.registries.",
    };

    private static final String[] LOCAL_FIRST_PREFIXES = {
        "net.neoforged.fml.loading.",
        "net.neoforged.fml.event.",
    };

    private static final String[] APP_LOADER_PREFIXES = {
        "com.google.gson.",
        "com.google.common.",
        "com.google.guava.",
        "org.apache.commons.",
        "org.apache.logging.",
        "org.apache.maven.",
        "org.slf4j.",
        "io.netty.",
        "it.unimi.dsi.fastutil.",
        "com.ibm.icu.",
        "net.jodah.typetools.",
        "com.electronwill.nightconfig.",
    };

    private static final java.util.Set<String> SHIM_OVERRIDE_PREFIXES =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private static final java.util.Set<String> KNOT_DELEGATE_PREFIXES =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final java.util.Set<String> SYNTHESIZED_CLASSES =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void registerSynthesizedClass(String dottedName) {
        if (dottedName != null && !dottedName.isEmpty()
            && !dottedName.startsWith("net.minecraft.")) {
            SYNTHESIZED_CLASSES.add(dottedName);
        }
    }

    public static boolean isSynthesized(String dottedName) {
        return SYNTHESIZED_CLASSES.contains(dottedName);
    }

    private final java.util.concurrent.ConcurrentHashMap<String, Object> defineLocks =
        new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.concurrent.ConcurrentHashMap<String, String> canonicalCodecStartDesc =
        new java.util.concurrent.ConcurrentHashMap<>();

    private Object getDefineLock(String name) {
        return defineLocks.computeIfAbsent(name, k -> new Object());
    }

    private static final java.util.Set<String> REPORTED_MC_CNFE =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private static void logMinecraftCnfeDiagnostics(String requested, String intermediary,
        ClassLoader fabricTarget, ClassLoader canonicalKnot, ClassLoader ctx) {
        if (!REPORTED_MC_CNFE.add(requested)) return;
        try {
            dev.stolym.vyne.util.VyneLogger.warn("[RemappingCL] "
                + requested + " (→ " + intermediary + ") unresolved — "
                + "fabricTarget=" + identify(fabricTarget)
                + ", canonicalKnot=" + identify(canonicalKnot)
                + ", tccl=" + identify(ctx));
        } catch (Throwable ignored) {
        }
    }

    private static String identify(ClassLoader cl) {
        if (cl == null) return "null";
        return cl.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(cl));
    }

    private static void ensureUnnamedModuleReads(ClassLoader child, ClassLoader parent) {
        if (child == null || parent == null) {
            return;
        }
        try {
            Module childMod = child.getUnnamedModule();
            Module parentMod = parent.getUnnamedModule();
            try {
                childMod.addReads(parentMod);
            } catch (Throwable ignored) {
            }
            try {
                parentMod.addReads(childMod);
            } catch (Throwable ignored) {
            }

            try {
                parentMod.addExports("net.minecraft", childMod);
            } catch (Throwable ignored) {
            }
            try {
                parentMod.addOpens("net.minecraft", childMod);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    public RemappingClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
        ensureUnnamedModuleReads(this, parent);
    }

    public static void registerOverridePrefix(String prefix) {
        SHIM_OVERRIDE_PREFIXES.add(prefix);
    }

    public static void registerKnotDelegatePrefix(String dottedPrefix) {
        if (dottedPrefix == null || dottedPrefix.isEmpty()) return;
        String p = dottedPrefix.endsWith(".") ? dottedPrefix : dottedPrefix + ".";
        if (!isAllowedKnotDelegatePrefix(p)) return;
        KNOT_DELEGATE_PREFIXES.add(p);
    }

    public static void clearKnotDelegatePrefixes() {
        KNOT_DELEGATE_PREFIXES.clear();
    }

    public static void resetLoaderStaticState() {
        KNOT_DELEGATE_PREFIXES.clear();
        CACHED_FABRIC_TARGET = null;
    }

    public static void registerKnotDelegatePrefixesFromJar(java.io.File jar) {
        if (jar == null || !jar.isFile()) return;
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jar)) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                String name = en.nextElement().getName();
                if (!name.endsWith(".class") || !name.contains("/")) continue;
                int slash = name.lastIndexOf('/');
                if (slash <= 0) continue;
                String dir = name.substring(0, slash);
                String[] parts = dir.split("/");
                if (parts.length < 2) continue;
                String prefix;
                if (parts.length >= 3) {
                    prefix = parts[0] + "." + parts[1] + "." + parts[2] + ".";
                } else {
                    prefix = parts[0] + "." + parts[1] + ".";
                }
                if (!seen.add(prefix)) continue;
                registerKnotDelegatePrefix(prefix);
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean isAllowedKnotDelegatePrefix(String p) {
        return !p.startsWith("java.")
            && !p.startsWith("javax.")
            && !p.startsWith("jdk.")
            && !p.startsWith("sun.")
            && !p.startsWith("net.minecraft.")
            && !p.startsWith("net.neoforged.")
            && !p.startsWith("net.minecraftforge.")
            && !p.startsWith("com.mojang.")
            && !p.startsWith("net.fabricmc.");
    }

    private static String longestKnotDelegatePrefix(String name) {
        String best = null;
        for (String p : KNOT_DELEGATE_PREFIXES) {
            if (name.startsWith(p) && (best == null || p.length() > best.length())) {
                best = p;
            }
        }
        return best;
    }

    private static boolean canResolveMinecraft(ClassLoader cl) {
        if (cl == null) {
            return false;
        }
        try {
            Class.forName("net.minecraft.class_2960", false, cl);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean canResolveMojangSerialization(ClassLoader cl) {
        if (cl == null) {
            return false;
        }
        try {
            Class.forName("com.mojang.serialization.Codec", false, cl);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private ClassLoader resolveAppLoader() {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        return cl != null ? cl : getParent();
    }

    private static volatile ClassLoader CACHED_FABRIC_TARGET;

    private static ClassLoader fabricLauncherTargetClassLoader() {
        ClassLoader cached = CACHED_FABRIC_TARGET;
        if (cached != null) {
            return cached;
        }
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
                if (tcl instanceof ClassLoader cl) {
                    CACHED_FABRIC_TARGET = cl;
                    return cl;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String sanitizeBinaryClassRequest(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        String n = name.trim();
        if (n.length() < 2 || n.charAt(0) != 'L' || n.charAt(1) == '[') {
            return name;
        }
        n = n.substring(1);
        if (n.endsWith(";")) {
            n = n.substring(0, n.length() - 1);
        }
        return n.replace('/', '.');
    }

    private Class<?> defineLocallyIfPresent(String name, boolean resolve) {
        String path = name.replace('.', '/') + ".class";
        try (InputStream is = getResourceAsStream(path)) {
            if (is == null) return null;
            byte[] original = is.readAllBytes();
            byte[] remapped = remap(original);
            Class<?> c;
            synchronized (getClassLoadingLock(name)) {
                c = findLoadedClass(name);
                if (c == null) {
                    c = defineClassWithFallback(name, remapped);
                    dev.stolym.vyne.vcl.VCLFieldPopulator.populate(c);
                }
            }
            if (resolve) resolveClass(c);
            return c;
        } catch (IOException | LinkageError e) {
            return null;
        }
    }

    private static String normalizeSlashesInBinaryName(String name) {
        if (name == null || name.isEmpty() || name.indexOf('/') < 0) {
            return name;
        }
        return name.replace('/', '.');
    }

    private static boolean allowParentDelegateCnfeFallback(String prefix) {
        return prefix.startsWith("net.neoforged.")
            || prefix.startsWith("net.minecraftforge.");
    }

    private Class<?> defineClassWithFallback(String name, byte[] bytes) {
        try {
            return defineClass(name, bytes, 0, bytes.length);
        } catch (VerifyError e) {
            if (isStackMapRecomputeExcluded(name)) {
                throw e;
            }
            dev.stolym.vyne.util.VyneLogger.warn("[RCL] VerifyError for " + name + " — retrying with full frame recomputation...");
            try {
                byte[] recomputed = recomputeStackMapFramesForDynamicEngine(bytes);
                return defineClass(name, recomputed, 0, recomputed.length);
            } catch (Throwable t) {
                throw e;
            }
        }
    }

    protected Class<?> loadClass(String requestedName, boolean resolve) throws ClassNotFoundException {
        final String name = normalizeSlashesInBinaryName(sanitizeBinaryClassRequest(requestedName));
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c != null) return c;

            for (String prefix : APP_LOADER_PREFIXES) {
                if (name.startsWith(prefix)) {
                    if ("org.apache.commons.".equals(prefix) && name.startsWith("org.apache.commons.lang3.")) {
                        break;
                    }
                    try {
                        return getParent().loadClass(name);
                    } catch (ClassNotFoundException primaryMiss) {
                        try {
                            return resolveAppLoader().loadClass(name);
                        } catch (ClassNotFoundException ignored) {
                            throw primaryMiss;
                        }
                    }
                }
            }

            ClassLoader knotParentEarly = getParent();
            if (knotParentEarly != null && (longestKnotDelegatePrefix(name) != null || name.contains(".mixin.") || name.contains(".accessor."))) {
                try {
                    return knotParentEarly.loadClass(name);
                } catch (ClassNotFoundException ignored) {
                }
            }

            for (String prefix : SHIM_FORCE_PREFIXES) {
                if (name.startsWith(prefix)) {
                    String path = name.replace('.', '/') + ".class";
                    try (InputStream is = getResourceAsStream(path)) {
                        if (is != null) {
                            byte[] original = is.readAllBytes();
                            byte[] remapped = remap(original);
                            synchronized (getDefineLock(name)) {
                                c = findLoadedClass(name);
                                if (c == null) {
                                    c = defineClassWithFallback(name, remapped);
                                }
                            }
                            if (resolve) resolveClass(c);
                            dev.stolym.vyne.vcl.VCLFieldPopulator.populate(c);
                            return c;
                        }
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
            }

            if ("dev.stolym.vyne.compat.NeoForgeMenuTypeBridge".equals(name)) {
                String path = name.replace('.', '/') + ".class";
                try (InputStream is = getResourceAsStream(path)) {
                    if (is != null) {
                        byte[] original = is.readAllBytes();
                        byte[] remapped = remap(original);
                        synchronized (getDefineLock(name)) {
                            c = findLoadedClass(name);
                            if (c == null) {
                                c = defineClassWithFallback(name, remapped);
                            }
                        }
                        if (resolve) {
                            resolveClass(c);
                        }
                        dev.stolym.vyne.vcl.VCLFieldPopulator.populate(c);
                        return c;
                    }
                } catch (IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
            }

            for (String prefix : LOCAL_FIRST_PREFIXES) {
                if (name.startsWith(prefix)) {
                    Class<?> forced = defineLocallyIfPresent(name, resolve);
                    if (forced != null) return forced;
                }
            }

            parentDelegatePrefix:
            for (String prefix : PARENT_DELEGATE_PREFIXES) {
                if (name.startsWith(prefix)) {
                    if ("net.minecraftforge.".equals(prefix)
                        && name.startsWith(prefix + "fml.")) {
                        continue;
                    }

                    if (name.startsWith("net.minecraft.") || name.startsWith("net.neoforged.neoforge.client.")) {
                        String intermediary = name.startsWith("net.minecraft.") 
                            ? (dev.stolym.vyne.mappings.MappingRegistry.isReady()
                                ? dev.stolym.vyne.mappings.MappingRegistry.get()
                                    .mapClass(name.replace('.', '/')).replace('/', '.')
                                : name)
                            : name;

                        Class<?> warmed = dev.stolym.vyne.vcl.VCLManager.getWarmedGameClass(intermediary);
                        if (warmed == null && !intermediary.equals(name)) {
                            warmed = dev.stolym.vyne.vcl.VCLManager.getWarmedGameClass(name);
                        }
                        if (warmed == null && dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
                            try {
                                String slash = intermediary.replace('.', '/');
                                String alt = dev.stolym.vyne.mappings.MappingRegistry.get().unmapClass(slash);
                                if (alt != null && !alt.equals(slash)) {
                                    warmed = dev.stolym.vyne.vcl.VCLManager.getWarmedGameClass(
                                        alt.replace('/', '.'));
                                }
                                if (warmed == null) {
                                    String nameSlash = name.replace('.', '/');
                                    if (!nameSlash.contains("class_")) {
                                        String toInter = dev.stolym.vyne.mappings.MappingRegistry.get()
                                            .mapClass(nameSlash);
                                        if (toInter != null && !toInter.isBlank()) {
                                            warmed = dev.stolym.vyne.vcl.VCLManager.getWarmedGameClass(
                                                toInter.replace('/', '.'));
                                        }
                                    }
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                        final boolean creativeTabNested = name.contains("CreativeModeTab$")
                            || (name.startsWith("net.minecraft.class_1761$"));
                        if (warmed != null && !creativeTabNested) {
                            return warmed;
                        }

                        ClassLoader fabricTarget = fabricLauncherTargetClassLoader();
                        ClassLoader canonicalKnot = dev.stolym.vyne.vcl.VCLManager.getCanonicalKnotLoader();
                        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
                        final boolean knotInit = creativeTabNested
                            || intermediary.contains("class_1761$");
                        if (fabricTarget != null) {
                            try {
                                return Class.forName(intermediary, knotInit, fabricTarget);
                            } catch (Throwable ignored) {
                            }
                        }
                        if (canonicalKnot != null && canonicalKnot != fabricTarget) {
                            try {
                                return Class.forName(intermediary, knotInit, canonicalKnot);
                            } catch (Throwable ignored) {
                            }
                        }
                        try {
                            if (knotInit) {
                                return Class.forName(intermediary, true, getParent());
                            }
                            return getParent().loadClass(intermediary);
                        } catch (Throwable e) {
                            if (!(e instanceof ClassNotFoundException)
                                && !(e instanceof NoClassDefFoundError)) {
                                throw e;
                            }
                            ClassNotFoundException anchor = e instanceof ClassNotFoundException cnf
                                ? cnf
                                : new ClassNotFoundException(intermediary, e);
                            if (ctx != null && ctx != this && ctx != getParent() && ctx != fabricTarget
                                && ctx != canonicalKnot && canResolveMinecraft(ctx)) {
                                try {
                                    return Class.forName(intermediary, true, ctx);
                                } catch (ClassNotFoundException ignored) {
                                }
                            }

                            if (dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
                                String slash = name.replace('.', '/');
                                String moj = dev.stolym.vyne.mappings.MappingRegistry.get()
                                    .unmapClass(slash);
                                if (moj != null && !moj.equals(slash)) {
                                    String retry = dev.stolym.vyne.mappings.MappingRegistry.get()
                                        .mapClass(moj).replace('/', '.');
                                    if (!retry.equals(intermediary)) {
                                        Class<?> retryWarmed =
                                            dev.stolym.vyne.vcl.VCLManager.getWarmedGameClass(retry);
                                        if (retryWarmed != null) return retryWarmed;
                                        try {
                                            return getParent().loadClass(retry);
                                        } catch (ClassNotFoundException ignored2) {
                                            if (ctx != null && ctx != this && ctx != getParent()
                                                && ctx != fabricTarget && ctx != canonicalKnot
                                                && canResolveMinecraft(ctx)) {
                                                try {
                                                    return Class.forName(retry, false, ctx);
                                                } catch (ClassNotFoundException ignored3) {
                                                }
                                            }
                                            if (fabricTarget != null) {
                                                try {
                                                    return Class.forName(retry, false, fabricTarget);
                                                } catch (ClassNotFoundException ignored4) {
                                                }
                                            }
                                            if (canonicalKnot != null) {
                                                try {
                                                    return Class.forName(retry, false, canonicalKnot);
                                                } catch (ClassNotFoundException ignored5) {
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Class<?> clientSynth = trySynthesizeMissingClientClass(name);
                            if (clientSynth != null) return clientSynth;
                            logMinecraftCnfeDiagnostics(name, intermediary,
                                fabricTarget, canonicalKnot, ctx);
                            throw anchor;
                        }
                    }
                    if (name.startsWith("com.mojang.")) {
                        ClassLoader fabricTarget = fabricLauncherTargetClassLoader();
                        ClassLoader canonicalKnot = dev.stolym.vyne.vcl.VCLManager.getCanonicalKnotLoader();
                        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
                        ClassLoader par = getParent();
                        if (fabricTarget != null) {
                            try {
                                return Class.forName(name, false, fabricTarget);
                            } catch (ClassNotFoundException ignored) {
                            }
                        }
                        if (canonicalKnot != null && canonicalKnot != fabricTarget) {
                            try {
                                return Class.forName(name, false, canonicalKnot);
                            } catch (ClassNotFoundException ignored) {
                            }
                        }
                        if (par != null) {
                            try {
                                return par.loadClass(name);
                            } catch (ClassNotFoundException ignored) {
                            }
                        }
                        if (ctx != null && ctx != this && ctx != par
                            && ctx != fabricTarget && ctx != canonicalKnot
                            && canResolveMojangSerialization(ctx)) {
                            try {
                                return Class.forName(name, false, ctx);
                            } catch (ClassNotFoundException ignored) {
                            }
                        }
                        throw new ClassNotFoundException(name);
                    }
                    try {
                        return getParent().loadClass(name);
                    } catch (ClassNotFoundException parentMiss) {
                        if (allowParentDelegateCnfeFallback(prefix)) {
                            break parentDelegatePrefix;
                        }
                        throw parentMiss;
                    } catch (LinkageError parentLinkage) {
                        if (allowParentDelegateCnfeFallback(prefix)) {
                            break parentDelegatePrefix;
                        }
                        throw parentLinkage;
                    }
                }
            }

            String path = name.replace('.', '/') + ".class";

            if (SYNTHESIZED_CLASSES.contains(name)) {
                URL ownUrl = findResource(path);

                if (ownUrl == null) {
                    for (URL u : getURLs()) {
                        try {
                            URL entry = new URL("jar:" + u + "!/" + path);
                            try (InputStream probe = entry.openStream()) {
                                if (probe != null) {
                                    ownUrl = entry;
                                    break;
                                }
                            }
                        } catch (IOException ignored) {}
                    }
                }
                if (ownUrl != null && (name == null || !name.startsWith("net.minecraft."))) {
                    try (InputStream ownIs = ownUrl.openStream()) {
                        byte[] bytes = ownIs.readAllBytes();

                        byte[] remapped = remap(bytes);
                        synchronized (getDefineLock(name)) {
                            c = findLoadedClass(name);
                            if (c == null) {
                                c = defineClassWithFallback(name, remapped);
                            }
                        }
                        if (resolve) resolveClass(c);
                        dev.stolym.vyne.vcl.VCLFieldPopulator.populate(c);
                        return c;
                    } catch (IOException ioe) {
                        throw new ClassNotFoundException(name, ioe);
                    }
                }
            }

            for (String prefix : SHIM_OVERRIDE_PREFIXES) {
                if (name.startsWith(prefix)) {
                    for (URL url : getURLs()) {
                        try {
                            URL entry = new URL("jar:" + url + "!/" + path);
                            try (InputStream is = entry.openStream()) {
                                byte[] bytes = is.readAllBytes();
                                byte[] remapped = remap(bytes);
                                synchronized (getClassLoadingLock(name)) {
                                    c = findLoadedClass(name);
                                    if (c == null) {
                                        c = defineClassWithFallback(name, remapped);
                                    }
                                }
                                if (resolve) resolveClass(c);
                                dev.stolym.vyne.vcl.VCLFieldPopulator.populate(c);
                                return c;
                            }
                        } catch (IOException ignored) {}
                    }
                }
            }

            try (InputStream is = getResourceAsStream(path)) {
                if (is != null && (name == null || !name.startsWith("net.minecraft."))) {
                    byte[] original = is.readAllBytes();
                    byte[] remapped = remap(original);

                    try {
                        Class<?> svc = getParent().loadClass(
                            "net.fabricmc.loader.impl.launch.knot.MixinServiceKnot");
                        java.lang.reflect.Method getTransformer =
                            svc.getDeclaredMethod("getTransformer");
                        getTransformer.setAccessible(true);
                        Object transformer = getTransformer.invoke(null);
                        if (transformer != null) {
                            Class<?> ti = getParent().loadClass(
                                "org.spongepowered.asm.mixin.transformer.IMixinTransformer");
                            java.lang.reflect.Method transformClassBytes =
                                ti.getMethod("transformClassBytes",
                                    String.class, String.class, byte[].class);
                            transformClassBytes.setAccessible(true);
                            remapped = (byte[]) transformClassBytes.invoke(
                                transformer, name.replace('.', '/'), name, remapped);
                        }
                    } catch (Exception ignored) {}
                    remapped = recomputeStackMapAfterMixinIfNeeded(name, remapped);

                    synchronized (getClassLoadingLock(name)) {
                        c = findLoadedClass(name);
                        if (c == null) {
                            c = defineClassWithFallback(name, remapped);
                            dev.stolym.vyne.vcl.VCLFieldPopulator.populate(c);
                        }
                    }
                    if (resolve) resolveClass(c);
                    return c;
                }
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }

            ClassLoader par = getParent();
            if (par != null && (name == null || !name.startsWith("net.minecraft."))) {
                try (InputStream pin = par.getResourceAsStream(path)) {
                    if (pin != null) {
                        byte[] original = pin.readAllBytes();
                        byte[] remapped = remap(original);
                        try {
                            Class<?> svc = par.loadClass(
                                "net.fabricmc.loader.impl.launch.knot.MixinServiceKnot");
                            java.lang.reflect.Method getTransformer =
                                svc.getDeclaredMethod("getTransformer");
                            getTransformer.setAccessible(true);
                            Object transformer = getTransformer.invoke(null);
                            if (transformer != null) {
                                Class<?> ti = par.loadClass(
                                    "org.spongepowered.asm.mixin.transformer.IMixinTransformer");
                                java.lang.reflect.Method transformClassBytes =
                                    ti.getMethod("transformClassBytes",
                                        String.class, String.class, byte[].class);
                                transformClassBytes.setAccessible(true);
                                remapped = (byte[]) transformClassBytes.invoke(
                                    transformer, name.replace('.', '/'), name, remapped);
                            }
                        } catch (Exception ignored) {
                        }
                        remapped = recomputeStackMapAfterMixinIfNeeded(name, remapped);
                        synchronized (getClassLoadingLock(name)) {
                            c = findLoadedClass(name);
                            if (c == null) {
                                c = defineClassWithFallback(name, remapped);
                                dev.stolym.vyne.vcl.VCLFieldPopulator.populate(c);
                            }
                        }
                        if (resolve) {
                            resolveClass(c);
                        }
                        return c;
                    }
                } catch (IOException | LinkageError ignored) {
                }
            }

            String intermediaryName = dev.stolym.vyne.mappings.MappingRegistry.isReady()
                ? dev.stolym.vyne.mappings.MappingRegistry.get()
                    .mapClass(name.replace('.', '/')).replace('/', '.')
                : name;
            try {
                return getParent().loadClass(
                    intermediaryName.equals(name) ? name : intermediaryName);
            } catch (ClassNotFoundException parentFail) {
                Class<?> genericSynth = trySynthesizeGenericStub(name);
                if (genericSynth != null) {
                    if (resolve) resolveClass(genericSynth);
                    return genericSynth;
                }
                throw parentFail;
            }
        }
    }

    private static final java.util.Set<String> SYNTHESIZED_EVENT_CLASSES =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private static final java.util.Set<String> SYNTHESIZED_CLIENT_CLASSES =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final byte[] STUB_BYTES_NOT_FOUND = new byte[0];

    private static final java.util.concurrent.ConcurrentHashMap<String, byte[]> VIRTUAL_STUB_BYTECODE_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static final String STUB_SUPER_CACHE_MISS = "\u0000SUPER_MISS";
    private static final String STUB_SUPER_CACHE_IFACE = "\u0000IS_IFACE";

    private static final java.util.concurrent.ConcurrentHashMap<String, String> STUB_SUPER_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    private Class<?> trySynthesizeGenericStub(String name) {
        if (name == null) return null;
        String dots = name.replace('/', '.');

        Class<?> eventSynth = trySynthesizeMissingEventClass(dots);
        if (eventSynth != null) return eventSynth;
        Class<?> clientSynth = trySynthesizeMissingClientClass(dots);
        if (clientSynth != null) return clientSynth;

        if (dots.startsWith("java.") || dots.startsWith("javax.") || dots.startsWith("sun.") || dots.startsWith("jdk.")
            || dots.startsWith("dev.stolym.vyne.") || dots.startsWith("net.fabricmc.loader.")) {
            return null;
        }

        synchronized (getDefineLock(dots)) {
            Class<?> existing = findLoadedClass(dots);
            if (existing != null) return existing;

            byte[] bytes = getVirtualStubBytecode(dots);
            if (bytes == null) return null;

            dev.stolym.vyne.util.VyneLogger.info("[RCL] Synthesizing missing optional class: " + dots);

            Class<?> c = defineClass(dots, bytes, 0, bytes.length);
            registerSynthesizedClass(dots);
            return c;
        }
    }

    private Class<?> trySynthesizeMissingClientClass(String name) {
        if (name == null) return null;
        String dots = name.replace('/', '.');
        if (!dots.startsWith("net.minecraft.class_")
            && !dots.startsWith("net.minecraft.")
            && !dots.startsWith("net.neoforged.neoforge.client.")
            && !dots.startsWith("net.minecraftforge.client.")) {
            return null;
        }
        synchronized (getDefineLock(dots)) {
            Class<?> existing = findLoadedClass(dots);
            if (existing != null) return existing;

            String internal = dots.replace('.', '/');
            String probeResult = resolveStubSuperFromParent(internal);
            boolean asInterface;
            String superName;
            if (probeResult == null) {
                asInterface = true;
                superName = null;
            } else if (STUB_SUPER_NOT_FOUND.equals(probeResult)) {
                asInterface = false;
                superName = "java/lang/Object";
            } else {
                asInterface = false;
                superName = probeResult;
            }

            String[] knownIfaces =
                dev.stolym.vyne.mappings.IntermediaryStubSuperHints
                    .getObservedInterfacesFor(internal);
            if (knownIfaces != null) {
                java.util.List<String> filtered = new java.util.ArrayList<>(knownIfaces.length);
                for (String ki : knownIfaces) {
                    if (ki != null && !ki.equals(internal)) filtered.add(ki);
                }
                knownIfaces = filtered.isEmpty() ? null : filtered.toArray(new String[0]);
            }

            ClassWriter cw = new ClassWriter(0);
            if (asInterface) {
                cw.visit(Opcodes.V17,
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
                    internal, null, "java/lang/Object", knownIfaces);
            } else {
                cw.visit(Opcodes.V17,
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                    internal, null, superName, knownIfaces);
            }
            cw.visitEnd();
            byte[] bytes = cw.toByteArray();
            Class<?> c;
            try {
                c = defineClassWithFallback(dots, bytes);
            } catch (LinkageError le) {
                c = findLoadedClass(dots);
                if (c != null) return c;
                dev.stolym.vyne.util.VyneLogger.warn(
                    "[RemappingCL] defineClass failed for client-stub "
                    + dots + ": " + le);
                return null;
            }
            if (SYNTHESIZED_CLIENT_CLASSES.add(dots)) {
                dev.stolym.vyne.util.VyneLogger.info(
                    "[RemappingCL] Synthesized empty client-class stub: "
                    + dots + " (server-side fallback, @OnlyIn guards "
                    + "should keep runtime calls off this path)");
            }
            return c;
        }
    }

    private static final String STUB_SUPER_NOT_FOUND = "\u0000NOT_FOUND";

    private String resolveStubSuperFromParent(String internalName) {
        String cachedResult = STUB_SUPER_CACHE.get(internalName);
        if (cachedResult != null) {
            if (STUB_SUPER_CACHE_IFACE.equals(cachedResult)) return null;
            if (STUB_SUPER_CACHE_MISS.equals(cachedResult)) return STUB_SUPER_NOT_FOUND;
            return cachedResult;
        }

        String result = resolveStubSuperFromParentUncached(internalName);

        if (result == null) {
            STUB_SUPER_CACHE.put(internalName, STUB_SUPER_CACHE_IFACE);
        } else if (STUB_SUPER_NOT_FOUND.equals(result)) {
            if (dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
                STUB_SUPER_CACHE.put(internalName, STUB_SUPER_CACHE_MISS);
            }
        } else {
            STUB_SUPER_CACHE.put(internalName, result);
        }
        return result;
    }

    private String resolveStubSuperFromParentUncached(String internalName) {
        try {
            ClassLoader par = getParent();
            byte[] raw = null;
            if (par != null) {
                try (InputStream is = par.getResourceAsStream(internalName + ".class")) {
                    if (is != null) {
                        raw = is.readAllBytes();
                    }
                }
            }
            if (raw == null) {
                raw = dev.stolym.vyne.mappings.MappingComposer.readIntermediaryClassBytesProbe(
                    internalName);
            }
            if (raw == null) {
                String hinted =
                    dev.stolym.vyne.mappings.IntermediaryStubSuperHints.inferDirectSuperIntermediary(
                        internalName);
                if (hinted != null) {
                    return hinted;
                }
                if (dev.stolym.vyne.mappings.IntermediaryStubSuperHints.isKnownClientInterface(
                        internalName)) {
                    return null;
                }
                if (par != null) {
                    try {
                        Class<?> c = Class.forName(
                                internalName.replace('/', '.'), false, par);
                        if (c.isInterface()) {
                            return null;
                        }
                        Class<?> sup = c.getSuperclass();
                        return sup != null
                            ? sup.getName().replace('.', '/')
                            : "java/lang/Object";
                    } catch (Throwable ignored) {}
                }
                return STUB_SUPER_NOT_FOUND;
            }
            ClassReader cr = new ClassReader(raw);
            if ((cr.getAccess() & Opcodes.ACC_INTERFACE) != 0) {
                return null;
            }
            String s = cr.getSuperName();
            return s != null ? s : "java/lang/Object";
        } catch (Throwable ignored) {
            return STUB_SUPER_NOT_FOUND;
        }
    }

    private Class<?> trySynthesizeMissingEventClass(String name) {
        if (name == null) return null;

        if (!(name.startsWith("net.neoforged.neoforge.")
                || name.startsWith("net.minecraftforge."))) {
            return null;
        }

        String lower = name.toLowerCase(java.util.Locale.ROOT);
        boolean looksLikeEvent = lower.contains("event")
                || lower.endsWith(".pre") || lower.endsWith(".post")
                || lower.contains("$pre") || lower.contains("$post");
        if (!looksLikeEvent) return null;

        synchronized (getDefineLock(name)) {
            Class<?> existing = findLoadedClass(name);
            if (existing != null) return existing;

            byte[] bytes;
            try {
                bytes = generateSyntheticEventClass(name);
            } catch (Throwable t) {
                dev.stolym.vyne.util.VyneLogger.warn("[RemappingCL] Synthetic Event generation failed for "
                        + name + ": " + t);
                return null;
            }
            Class<?> c;
            try {
                c = defineClassWithFallback(name, bytes);
            } catch (LinkageError le) {
                c = findLoadedClass(name);
                if (c != null) return c;
                dev.stolym.vyne.util.VyneLogger.warn("[RemappingCL] defineClass failed for synthetic "
                        + name + ": " + le);
                return null;
            }
            if (SYNTHESIZED_EVENT_CLASSES.add(name)) {
                dev.stolym.vyne.util.VyneLogger.debug("[RemappingCL] Synthesized empty Event shim for missing class: "
                        + name
                        + " (registration succeeds, dispatch is inert).");
            }
            return c;
        }
    }

    private byte[] generateSyntheticEventClass(String binaryName) {
        String internal = binaryName.replace('.', '/');
        String superInternal = "net/neoforged/bus/api/Event";

        if (internal.contains("$")) {
            String outer = internal.substring(0, internal.lastIndexOf('$'));
            try {
                Class.forName(outer.replace('/', '.'), false, getParent());
                superInternal = outer;
            } catch (Throwable ignored) {
                if (outer.endsWith("BlockEvent")) {
                    superInternal = outer.replace('.', '/');
                }
            }
        }

        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                internal,
                null,
                superInternal,
                null);

        org.objectweb.asm.MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                superInternal, "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        throw new ClassNotFoundException(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        InputStream is = super.getResourceAsStream(name);
        if (is != null) return is;

        if (name != null && name.endsWith(".class") && !name.contains("/")) {
        } else if (name != null && name.endsWith(".class")) {
            String internalName = name.substring(0, name.length() - 6);
            String dottedName = internalName.replace('/', '.');
            if (!isProtectedPrefix(dottedName)) {
                byte[] stub = getVirtualStubBytecode(dottedName);
                if (stub != null) {
                    return new java.io.ByteArrayInputStream(stub);
                }
            }
        }
        return null;
    }

    private byte[] getVirtualStubBytecode(String dots) {
        if (isProtectedPrefix(dots)) return null;

        byte[] cached = VIRTUAL_STUB_BYTECODE_CACHE.get(dots);
        if (cached != null) {
            return cached == STUB_BYTES_NOT_FOUND ? null : cached;
        }

        String internal = dots.replace('.', '/');

        int flags;
        String superName;
        String[] interfaces;

        if (dev.stolym.vyne.mappings.IntermediaryStubSuperHints.isKnownClientInterface(internal)) {
            flags = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
            superName = "java/lang/Object";
            interfaces = dev.stolym.vyne.mappings.IntermediaryStubSuperHints.getObservedInterfacesFor(internal);
            dev.stolym.vyne.util.VyneLogger.info("[RemappingCL] Forced interface stub for critical client-class: " + dots);
            return finalizeStub(dots, internal, flags, superName, interfaces);
        }

        byte[] probeBytes = dev.stolym.vyne.mappings.MappingComposer.readIntermediaryClassBytesProbe(internal);

        if (probeBytes != null) {
            ClassReader cr = new ClassReader(probeBytes);
            flags = cr.getAccess();
            superName = cr.getSuperName();
            interfaces = cr.getInterfaces();
        } else {
            String probeResult = resolveStubSuperFromParent(internal);
            if (probeResult == null) {
                flags = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
                superName = "java/lang/Object";
            } else if (STUB_SUPER_NOT_FOUND.equals(probeResult)) {
                String simpleName = dots.contains(".") ? dots.substring(dots.lastIndexOf('.') + 1) : dots;
                boolean isIface = simpleName.length() > 1 && simpleName.startsWith("I") && Character.isUpperCase(simpleName.charAt(1));
                if (isIface) {
                    flags = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
                    superName = "java/lang/Object";
                } else {
                    flags = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER;
                    superName = (dots.endsWith("Exception") || dots.endsWith("Error")) ? "java/lang/Throwable" : "java/lang/Object";
                }
            } else {
                flags = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER;
                superName = probeResult;
            }

            if (STUB_SUPER_NOT_FOUND.equals(superName)) superName = "java/lang/Object";

            interfaces = dev.stolym.vyne.mappings.IntermediaryStubSuperHints.getObservedInterfacesFor(internal);
        }

        return finalizeStub(dots, internal, flags, superName, interfaces);
    }

    private byte[] finalizeStub(String dots, String internal, int flags, String superName, String[] interfaces) {
        if (interfaces != null) {
            java.util.List<String> filtered = new java.util.ArrayList<>(interfaces.length);
            for (String ki : interfaces) {
                if (ki != null && !ki.equals(internal)) filtered.add(ki);
            }
            interfaces = filtered.isEmpty() ? null : filtered.toArray(new String[0]);
        }

        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, flags, internal, null, superName, interfaces);
        cw.visitEnd();
        byte[] result = cw.toByteArray();
        VIRTUAL_STUB_BYTECODE_CACHE.put(dots, result);
        return result;
    }

    public static void invalidateCache() {
        VIRTUAL_STUB_BYTECODE_CACHE.clear();
        STUB_SUPER_CACHE.clear();
    }

    private static boolean isProtectedPrefix(String dots) {
        return dots.startsWith("java.") || dots.startsWith("javax.") || dots.startsWith("sun.") || dots.startsWith("jdk.")
            || dots.startsWith("dev.stolym.vyne.") || dots.startsWith("net.fabricmc.loader.");
    }

    @Override
    public String findLibrary(String libname) {
        String mapped = System.mapLibraryName(libname);
        for (URL url : getURLs()) {
            try {
                URL entry = new URL("jar:" + url + "!/" + mapped);
                java.io.InputStream is = entry.openStream();
                java.io.File tmp = java.io.File.createTempFile("vyne-native-", "-" + mapped);
                tmp.deleteOnExit();
                try (java.io.OutputStream os = new java.io.FileOutputStream(tmp)) {
                    is.transferTo(os);
                }
                is.close();
                return tmp.getAbsolutePath();
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static final String[] DIAG_REMAP_LOG_PREFIXES = {};

    private static final java.util.Set<String> LOGGED_REMAP_CLASSES =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    private byte[] remap(byte[] bytes) {
        MappingComposer.pushClassByteProbeLoader(this);
        try {
            bytes = dev.stolym.vyne.engine.dynamic.rewrite.MojmapClassHeaderRemapper.remap(bytes);
            try {
                String[] ifaces = new ClassReader(bytes).getInterfaces();
                if (ifaces != null) {
                    for (String iface : ifaces) {
                        if (iface != null && iface.startsWith("net/minecraft/class_")) {
                            dev.stolym.vyne.mappings.IntermediaryStubSuperHints
                                .observeSuperInterface(iface);
                        }
                    }
                }
            } catch (Throwable ignored) {}
            ClassReader reader = new ClassReader(bytes);
            try {
                String internal = reader.getClassName();
                if (internal != null) {
                    for (String prefix : DIAG_REMAP_LOG_PREFIXES) {
                        if (internal.startsWith(prefix) && LOGGED_REMAP_CLASSES.add(internal)) {
                            dev.stolym.vyne.util.VyneLogger.info(
                                "[RCL.remap] processing " + internal
                                + " (loader=" + Integer.toHexString(System.identityHashCode(this)) + ")");
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {}
            final ClassLoader frameSl = RemappingClassLoader.this;
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS) {
                @Override
                protected String getCommonSuperClass(String type1, String type2) {
                    return commonSuperTypeForFrameComputation(frameSl, type1, type2);
                }
            };
            VyneRemapper remapper = new VyneRemapper(Opcodes.ASM9, writer);
            reader.accept(remapper, 0);
            byte[] remapped = writer.toByteArray();

            remapped = patchInheritedFieldAccess(remapped);
            remapped = patchOptionalMenuSetCraftingSize(remapped);
            remapped = patchNeoForgeRecipeTypeSimple(remapped);

            remapped = patchDeferredRegisterCompatibility(remapped,  false);
            remapped = patchBlockStateDefinitionCompatibility(remapped);
            remapped = patchEntityBlockCompatibility(remapped);
            remapped = patchRecipeSerializerCodecCompatibility(remapped);
            remapped = patchCreativeTabBuilderCompatibility(remapped);
            remapped = patchCreativeTabBuilderNeoForgeExtensions(remapped);
            remapped = patchPayloadRegistrarCompatibility(remapped);
            remapped = patchFluidGetFluidType(remapped);
            remapped = patchComponentGetString(remapped);
            remapped = patchDeferredSoundTypeForBlockProperties(remapped);
            remapped = patchMissingClass3542Method15434(remapped);
            remapped = patchGetCapabilityRedirect(remapped);
            remapped = patchKeyMappingExtensionCalls(remapped);
            remapped = patchUnsupportedOperations(remapped);
            remapped = patchFinalMethodOverrides(remapped);
            return fixInterfaceSuperclasses(remapped);
        } finally {
            MappingComposer.popClassByteProbeLoader();
        }
    }

    private byte[] patchFinalMethodOverrides(byte[] bytes) {
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        boolean modified = false;

        if ("net/minecraft/class_2474".equals(cn.name)) {
            for (org.objectweb.asm.tree.MethodNode mn : cn.methods) {
                if ("method_10321".equals(mn.name) && (mn.access & Opcodes.ACC_FINAL) != 0) {
                    mn.access &= ~Opcodes.ACC_FINAL;
                    modified = true;
                }
            }
        }

        if (!modified) return bytes;
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private byte[] patchPayloadRegistrarCompatibility(byte[] bytes) {
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        boolean modified = false;

        final String registrarOwner = "net/neoforged/neoforge/network/registration/PayloadRegistrar";
        final String registrarRet = "L" + registrarOwner + ";";
        final String handlerType = "Lnet/neoforged/neoforge/network/handling/IPayloadHandler;";

        final java.util.Set<String> payloadMethods = java.util.Set.of(
            "playToClient", "playToServer", "playBidirectional",
            "configurationToClient", "configurationToServer", "configurationBidirectional",
            "commonToClient", "commonToServer", "commonBidirectional"
        );
        final String shimDesc = "(Ljava/lang/Object;Ljava/lang/Object;" + handlerType + ")" + registrarRet;

        for (org.objectweb.asm.tree.MethodNode mn : cn.methods) {
            if (mn.instructions == null) continue;
            for (org.objectweb.asm.tree.AbstractInsnNode insn : mn.instructions.toArray()) {
                if (!(insn instanceof org.objectweb.asm.tree.MethodInsnNode mi)) continue;
                if (mi.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                if (!registrarOwner.equals(mi.owner)) continue;
                if (!payloadMethods.contains(mi.name)) continue;
                if (shimDesc.equals(mi.desc)) continue;

                if (!mi.desc.endsWith(handlerType + ")" + registrarRet)) continue;
                if (!mi.desc.startsWith("(L")) continue;
                int firstSemi = mi.desc.indexOf(';', 1);
                if (firstSemi < 0 || firstSemi + 2 >= mi.desc.length()) continue;
                if (mi.desc.charAt(firstSemi + 1) != 'L') continue;

                mi.desc = shimDesc;
                modified = true;
            }
        }
        if (!modified) return bytes;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private byte[] patchFluidGetFluidType(byte[] bytes) {
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        boolean modified = false;

        final String fluidOwner = "net/minecraft/class_3611";
        final String bridgeOwner = "net/neoforged/neoforge/fluids/FluidTypeBridge";
        final String fluidTypeDesc = "Lnet/neoforged/neoforge/fluids/FluidType;";
        final String oldDesc = "()" + fluidTypeDesc;
        final String bridgeDesc = "(Ljava/lang/Object;)" + fluidTypeDesc;

        for (org.objectweb.asm.tree.MethodNode mn : cn.methods) {
            if (mn.instructions == null) continue;
            for (org.objectweb.asm.tree.AbstractInsnNode insn : mn.instructions.toArray()) {
                if (!(insn instanceof org.objectweb.asm.tree.MethodInsnNode mi)) continue;
                if (mi.getOpcode() != Opcodes.INVOKEVIRTUAL && mi.getOpcode() != Opcodes.INVOKEINTERFACE) continue;
                boolean isFluid = fluidOwner.equals(mi.owner) || mi.owner.startsWith("net/neoforged/neoforge/fluids/");
                if (!isFluid) continue;
                if (!"getFluidType".equals(mi.name)) continue;
                if (!oldDesc.equals(mi.desc)) continue;

                mi.setOpcode(Opcodes.INVOKESTATIC);
                mi.owner = bridgeOwner;
                mi.desc = bridgeDesc;
                mi.itf = false;
                modified = true;
            }
        }
        if (!modified) return bytes;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private byte[] patchComponentGetString(byte[] bytes) {
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        boolean modified = false;

        final String componentOwner = "net/minecraft/class_2561";
        final String bridgeOwner = "dev/stolym/vyne/compat/ComponentBridge";
        final String oldDesc = "()Ljava/lang/String;";
        final String bridgeDesc = "(Ljava/lang/Object;)Ljava/lang/String;";
        final java.util.Set<String> candidateNames = java.util.Set.of(
            "method_10851", "method_10858"
        );

        for (org.objectweb.asm.tree.MethodNode mn : cn.methods) {
            if (mn.instructions == null) continue;
            for (org.objectweb.asm.tree.AbstractInsnNode insn : mn.instructions.toArray()) {
                if (!(insn instanceof org.objectweb.asm.tree.MethodInsnNode mi)) continue;
                if (mi.getOpcode() != Opcodes.INVOKEVIRTUAL
                    && mi.getOpcode() != Opcodes.INVOKEINTERFACE) continue;
                if (!componentOwner.equals(mi.owner)) continue;
                if (!candidateNames.contains(mi.name)) continue;
                if (!oldDesc.equals(mi.desc)) continue;

                mi.setOpcode(Opcodes.INVOKESTATIC);
                mi.owner = bridgeOwner;
                mi.name = "getString";
                mi.desc = bridgeDesc;
                mi.itf = false;
                modified = true;
            }
        }
        if (!modified) return bytes;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private byte[] patchDeferredSoundTypeForBlockProperties(byte[] bytes) {
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        boolean modified = false;
        final java.util.Set<String> propsOwners = java.util.Set.of(
            "net/minecraft/class_4970$class_2251",
            "net/minecraft/world/level/block/state/BlockBehaviour$Properties"
        );
        final String soundArg = "Lnet/minecraft/class_2498;";
        final String singleSoundParam = "(" + soundArg + ")";
        final String shimOwner = "net/minecraftforge/vyne/VyneShims";
        final String shimName = "unwrapDeferredSoundTypeArg";
        final String shimDesc = "(Ljava/lang/Object;)Ljava/lang/Object;";

        for (org.objectweb.asm.tree.MethodNode mn : cn.methods) {
            if (mn.instructions == null) continue;
            for (org.objectweb.asm.tree.AbstractInsnNode insn : mn.instructions.toArray()) {
                if (!(insn instanceof org.objectweb.asm.tree.MethodInsnNode mi)) continue;
                if (mi.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                if (!propsOwners.contains(mi.owner)) continue;
                if (mi.desc == null || !mi.desc.startsWith(singleSoundParam)) continue;

                org.objectweb.asm.tree.InsnList inject = new org.objectweb.asm.tree.InsnList();
                inject.add(new org.objectweb.asm.tree.MethodInsnNode(
                    Opcodes.INVOKESTATIC, shimOwner, shimName, shimDesc, false));
                inject.add(new org.objectweb.asm.tree.TypeInsnNode(
                    Opcodes.CHECKCAST, "net/minecraft/class_2498"));
                mn.instructions.insertBefore(insn, inject);
                modified = true;
            }
        }
        if (!modified) return bytes;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private byte[] patchMissingClass3542Method15434(byte[] bytes) {
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        if ((cn.access & Opcodes.ACC_INTERFACE) != 0) {
            return bytes;
        }
        if ((cn.access & Opcodes.ACC_ABSTRACT) != 0) {
            return bytes;
        }
        if (cn.interfaces == null || cn.interfaces.isEmpty()) {
            return bytes;
        }
        final String iface3542 = "net/minecraft/class_3542";
        if (!cn.interfaces.contains(iface3542)) {
            return bytes;
        }
        final String bridgeName = "method_15434";
        final String bridgeDesc = "()Ljava/lang/String;";
        for (org.objectweb.asm.tree.MethodNode m : cn.methods) {
            if (bridgeName.equals(m.name) && bridgeDesc.equals(m.desc)) {
                return bytes;
            }
        }
        org.objectweb.asm.tree.MethodNode bridge = new org.objectweb.asm.tree.MethodNode(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
            bridgeName,
            bridgeDesc,
            null,
            null
        );
        bridge.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
        bridge.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "net/minecraftforge/vyne/VyneShims",
            "bridgeClass3542Method15434",
            "(Ljava/lang/Object;)Ljava/lang/String;",
            false
        ));
        bridge.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ARETURN));
        cn.methods.add(bridge);
        org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private byte[] patchGetCapabilityRedirect(byte[] bytes) {
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        boolean modified = false;

        final String bridgeOwner = "dev/stolym/vyne/compat/CapabilityBridge";
        final String capDesc = "Lnet/minecraftforge/common/capabilities/Capability;";
        final String lazyOptDesc = "Lnet/minecraftforge/common/util/LazyOptional;";
        final String directionDesc = "Lnet/minecraft/core/Direction;";

        final String originalSidedDesc = "(" + capDesc + directionDesc + ")" + lazyOptDesc;

        final String originalUnsidedDesc = "(" + capDesc + ")" + lazyOptDesc;

        final String bridgeSidedDesc =
                "(Ljava/lang/Object;" + capDesc + "Ljava/lang/Object;)" + lazyOptDesc;
        final String bridgeUnsidedDesc =
                "(Ljava/lang/Object;" + capDesc + ")" + lazyOptDesc;

        for (org.objectweb.asm.tree.MethodNode mn : cn.methods) {
            if (mn.instructions == null) continue;
            for (org.objectweb.asm.tree.AbstractInsnNode insn : mn.instructions.toArray()) {
                if (!(insn instanceof org.objectweb.asm.tree.MethodInsnNode mi)) continue;
                if (mi.getOpcode() != Opcodes.INVOKEVIRTUAL
                        && mi.getOpcode() != Opcodes.INVOKEINTERFACE) continue;
                if (!"getCapability".equals(mi.name)) continue;
                if (!CAPABILITY_TARGET_OWNERS.contains(mi.owner)) continue;

                if (originalSidedDesc.equals(mi.desc)) {
                    mi.setOpcode(Opcodes.INVOKESTATIC);
                    mi.owner = bridgeOwner;
                    mi.name = "getCapability";
                    mi.desc = bridgeSidedDesc;
                    mi.itf = false;
                    modified = true;
                } else if (originalUnsidedDesc.equals(mi.desc)) {
                    mi.setOpcode(Opcodes.INVOKESTATIC);
                    mi.owner = bridgeOwner;
                    mi.name = "getCapabilityUnsided";
                    mi.desc = bridgeUnsidedDesc;
                    mi.itf = false;
                    modified = true;
                }
            }
        }
        if (!modified) return bytes;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static final java.util.Set<String> CAPABILITY_TARGET_OWNERS = java.util.Set.of(
            "net/minecraft/class_1657",   
            "net/minecraft/class_1297",   
            "net/minecraft/class_1309",   
            "net/minecraft/class_1799",   
            "net/minecraft/class_2586",   
            "net/minecraft/class_1937",   
            "net/minecraft/class_2818",   
            "net/minecraft/class_3218",   
            "net/minecraft/class_3222"    
    );
    private static final java.util.Set<String> STACKMAP_RECOMPUTE_EXCLUDE = CAPABILITY_TARGET_OWNERS;
    public static boolean isStackMapRecomputeExcluded(String binaryOrSlashName) {
        if (binaryOrSlashName == null) {
            return false;
        }
        String slash = binaryOrSlashName.indexOf('/') >= 0
            ? binaryOrSlashName
            : binaryOrSlashName.replace('.', '/');
        return STACKMAP_RECOMPUTE_EXCLUDE.stream().anyMatch(slash::endsWith);
    }

    private static boolean environmentIsDedicatedServerFabric() {
        try {
            Class<?> fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader", false,
                RemappingClassLoader.class.getClassLoader());
            Object inst = fabricLoader.getMethod("getInstance").invoke(null);
            return !Boolean.TRUE.equals(inst.getClass().getMethod("isClient").invoke(inst));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean shouldSkipRenderHandFrameRecompute(byte[] remapped) {
        if (remapped == null || remapped.length < 32 || !environmentIsDedicatedServerFabric()) {
            return false;
        }
        String raw = new String(remapped, java.nio.charset.StandardCharsets.ISO_8859_1);
        return raw.contains("RenderHandEvent");
    }

    private static byte[] recomputeStackMapAfterMixinIfNeeded(String binaryName, byte[] remapped) {
        if (remapped == null || remapped.length == 0 || !dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
            return remapped;
        }
        if (binaryName != null) {
            String slash = binaryName.replace('.', '/');
            if (STACKMAP_RECOMPUTE_EXCLUDE.stream().anyMatch(slash::endsWith)) {
                return remapped;
            }
        }
        if (shouldSkipRenderHandFrameRecompute(remapped)) {
            return remapped;
        }
        return recomputeStackMapFramesForDynamicEngine(remapped);
    }

    private static ClassLoader frameComputationClassLoader() {
        ClassLoader probe = MappingComposer.peekClassByteProbeLoader();
        if (probe != null) {
            return probe;
        }
        ClassLoader knot = dev.stolym.vyne.vcl.VCLManager.getCanonicalKnotLoader();
        if (knot != null) {
            return knot;
        }
        return Thread.currentThread().getContextClassLoader();
    }

    private static String registrateItemProviderEntryCommon(ClassLoader sl, Class<?> c1, Class<?> c2) {
        ClassLoader[] loaders = {
            c1.getClassLoader(),
            c2.getClassLoader(),
            sl,
            Thread.currentThread().getContextClassLoader(),
        };
        for (ClassLoader cl : loaders) {
            if (cl == null) {
                continue;
            }
            try {
                Class<?> ip = Class.forName("com.tterrag.registrate.util.entry.ItemProviderEntry", false, cl);
                if (ip.isAssignableFrom(c1) && ip.isAssignableFrom(c2)) {
                    return "com/tterrag/registrate/util/entry/ItemProviderEntry";
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String registrateBlockItemEntryFrameMerge(String t1, String t2) {
        final String blockEntry = "com/tterrag/registrate/util/entry/BlockEntry";
        final String itemEntry = "com/tterrag/registrate/util/entry/ItemEntry";
        boolean t1Block = t1.equals(blockEntry) || t1.startsWith(blockEntry + "$");
        boolean t1Item = t1.equals(itemEntry) || t1.startsWith(itemEntry + "$");
        boolean t2Block = t2.equals(blockEntry) || t2.startsWith(blockEntry + "$");
        boolean t2Item = t2.equals(itemEntry) || t2.startsWith(itemEntry + "$");
        if ((t1Block && t2Item) || (t1Item && t2Block)) {
            return "com/tterrag/registrate/util/entry/ItemProviderEntry";
        }
        return null;
    }

    private static String mergeIteratorWithReferenceForFrames(ClassLoader sl, String type1, String type2) {
        final String iterator = "java/util/Iterator";
        boolean i1 = iterator.equals(type1);
        boolean i2 = iterator.equals(type2);
        if (!i1 && !i2) {
            return null;
        }
        if (type1.equals(type2)) {
            return type1;
        }
        String other = i1 ? type2 : type1;
        if (other.length() == 0 || other.charAt(0) == '[') {
            return "java/lang/Object";
        }
        if (sl == null) {
            return "java/lang/Object";
        }
        try {
            Class<?> iterClass = Class.forName("java.util.Iterator", false, sl);
            Class<?> otherClass = Class.forName(other.replace('/', '.'), false, sl);
            if (iterClass.isAssignableFrom(otherClass)) {
                return other;
            }
        } catch (Throwable ignored) {
        }
        return "java/lang/Object";
    }

    private static String preferClientGuiMergeOverObject(String type1, String type2) {
        boolean o1 = "java/lang/Object".equals(type1);
        boolean o2 = "java/lang/Object".equals(type2);
        if (o1 ^ o2) {
            String other = o1 ? type2 : type1;
            if ("net/minecraft/class_437".equals(other) || "net/minecraft/class_364".equals(other)) {
                return other;
            }
        }
        if (dev.stolym.vyne.mappings.MappingRegistry.isReady()
                && type1.startsWith("net/minecraft/class_")
                && type2.startsWith("net/minecraft/class_")) {
            try {
                dev.stolym.vyne.mappings.MappingComposer.ComposedMappings m =
                    dev.stolym.vyne.mappings.MappingRegistry.get();
                String m1 = m.unmapClass(type1);
                String m2 = m.unmapClass(type2);
                if (m1 != null && m2 != null) {
                    String u1 = m1.indexOf('.') >= 0 ? m1.replace('.', '/') : m1;
                    String u2 = m2.indexOf('.') >= 0 ? m2.replace('.', '/') : m2;
                    if (u1.startsWith("net/minecraft/client/gui/screens/")
                            && u2.startsWith("net/minecraft/client/gui/screens/")) {
                        return resolveScreenInter();
                    }
                    if (u1.startsWith("net/minecraft/client/gui/components/")
                            && u2.startsWith("net/minecraft/client/gui/components/")) {
                        return resolveEditBoxInter();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return "java/lang/Object";
    }

    private static String simpleInternalName(String internalName) {
        if (internalName == null) return "";
        int slash = internalName.lastIndexOf('/');
        return slash >= 0 ? internalName.substring(slash + 1) : internalName;
    }

    private static String resolveScreenInter() {
        if (dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
            try {
                String inter = dev.stolym.vyne.mappings.MappingRegistry.get()
                    .mapClass("net/minecraft/client/gui/screens/Screen");
                if (inter != null && !inter.isEmpty() && inter.contains("class_")) {
                    return inter;
                }
            } catch (Throwable ignored) {}
        }
        return "net/minecraft/class_437";
    }

    private static String resolveEditBoxInter() {
        if (dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
            try {
                String inter = dev.stolym.vyne.mappings.MappingRegistry.get()
                    .mapClass("net/minecraft/client/gui/components/EditBox");
                if (inter != null && !inter.isEmpty() && inter.contains("class_")) {
                    return inter;
                }
            } catch (Throwable ignored) {}
        }
        return "net/minecraft/class_364";
    }

    private static String commonSuperTypeForFrameComputation(ClassLoader sl, String type1, String type2) {
        if (sl == null || type1 == null || type2 == null) {
            return "java/lang/Object";
        }
        final String blockPosInter = "net/minecraft/class_2338";
        if (("[I".equals(type1) && blockPosInter.equals(type2))
            || (blockPosInter.equals(type1) && "[I".equals(type2))) {
            return "java/lang/Object";
        }
        boolean t1Obj = "java/lang/Object".equals(type1);
        boolean t2Obj = "java/lang/Object".equals(type2);
        boolean t1Arr = type1.charAt(0) == '[';
        boolean t2Arr = type2.charAt(0) == '[';
        if ((t1Obj || t2Obj) && (t1Arr || t2Arr)) {
            return "java/lang/Object";
        }
        if (t1Arr ^ t2Arr) {
            String arrType = t1Arr ? type1 : type2;
            String refType = t1Arr ? type2 : type1;
            if (!"java/lang/Object".equals(refType)) {
                try {
                    Class<?> arrC = Class.forName(arrType.replace('/', '.'), false, sl);
                    Class<?> refC = Class.forName(refType.replace('/', '.'), false, sl);
                    if (refC.isAssignableFrom(arrC)) {
                        return refType;
                    }
                    if (arrC.isAssignableFrom(refC)) {
                        return arrType;
                    }
                } catch (Throwable ignored) {
                    return "java/lang/Object";
                }
                return "java/lang/Object";
            }
        }
        if ((t1Obj && blockPosInter.equals(type2)) || (t2Obj && blockPosInter.equals(type1))) {
            return "java/lang/Object";
        }
        if (t1Obj || t2Obj) {
            String other = t1Obj ? type2 : type1;
            if (other.contains("ModelFile") || other.contains("ModelBuilder") || other.contains("ItemModelBuilder")) {
                return other;
            }
            if (other.equals("net/minecraft/class_1297")) {
                return other;
            }
            if (other.contains("Screen") || other.contains("Config")) {
                return other;
            }
        }
        if ((type1.contains("Screen") || type1.contains("Config")) && 
            (type2.contains("Screen") || type2.contains("Config"))) {
            return type1;
        }
        String registrateMerge = registrateBlockItemEntryFrameMerge(type1, type2);
        if (registrateMerge != null) {
            return registrateMerge;
        }

        boolean isCat2T1 = "D".equals(type1) || "J".equals(type1) || "D".equals(type1.replace("L", "")) || "J".equals(type1.replace("L", ""));
        boolean isCat2T2 = "D".equals(type2) || "J".equals(type2) || "D".equals(type2.replace("L", "")) || "J".equals(type2.replace("L", ""));

        if (isCat2T1 != isCat2T2) {
             return "java/lang/Object";
        }
        final String itemInter = "net/minecraft/class_1792";
        final String entityTypeInter = "net/minecraft/class_1299";
        if ((itemInter.equals(type1) && entityTypeInter.equals(type2))
            || (entityTypeInter.equals(type1) && itemInter.equals(type2))) {
            return "java/lang/Object";
        }
        final String blockInter = "net/minecraft/class_2248";
        final String propertyInter = "net/minecraft/class_2769";
        if ((blockInter.equals(type1) && propertyInter.equals(type2))
            || (propertyInter.equals(type1) && blockInter.equals(type2))) {
            return "java/lang/Object";
        }
        String iteratorMerge = mergeIteratorWithReferenceForFrames(sl, type1, type2);
        if (iteratorMerge != null) {
            return iteratorMerge;
        }
        try {
            Class<?> c1 = null;
            Class<?> c2 = null;
            try {
                c1 = Class.forName(type1.replace('/', '.'), false, sl);
                c2 = Class.forName(type2.replace('/', '.'), false, sl);
            } catch (Throwable t) {
                return resolveCommonSuperClassASM(type1, type2, sl);
            }

            if (c1.isAssignableFrom(c2)) {
                return type1;
            }
            if (c2.isAssignableFrom(c1)) {
                return type2;
            }
            if (c1.isInterface() || c2.isInterface()) {
                return "java/lang/Object";
            }
            Class<?> cand = c1;
            while (cand != null && cand != java.lang.Object.class && !cand.isAssignableFrom(c2)) {
                cand = cand.getSuperclass();
            }
            if (cand != null && cand != java.lang.Object.class) {
                return cand.getName().replace('.', '/');
            }
            cand = c2;
            while (cand != null && cand != java.lang.Object.class && !cand.isAssignableFrom(c1)) {
                cand = cand.getSuperclass();
            }
            if (cand != null && cand != java.lang.Object.class) {
                return cand.getName().replace('.', '/');
            }
            String registrate = registrateItemProviderEntryCommon(sl, c1, c2);
            if (registrate != null) {
                return registrate;
            }
            return preferClientGuiMergeOverObject(type1, type2);
        } catch (TypeNotPresentException | LinkageError e) {
            return resolveCommonSuperClassASM(type1, type2, sl);
        } catch (RuntimeException e) {
            Throwable c = e.getCause();
            if (c instanceof TypeNotPresentException || c instanceof ClassNotFoundException) {
                return resolveCommonSuperClassASM(type1, type2, sl);
            }
            if (e.toString().contains("VerifyError")) {
                return resolveCommonSuperClassASM(type1, type2, sl);
            }
            throw e;
        } catch (Throwable e) {
            return resolveCommonSuperClassASM(type1, type2, sl);
        }
    }

    private static String resolveCommonSuperClassASM(String type1, String type2, ClassLoader cl) {
        if (type1 == null || type2 == null || "java/lang/Object".equals(type1) || "java/lang/Object".equals(type2)) {
            return preferClientGuiMergeOverObject(type1, type2);
        }
        if (type1.equals(type2)) return type1;

        if ((type1.endsWith("Exception") || type1.endsWith("Error"))
            && (type2.endsWith("Exception") || type2.endsWith("Error"))) {
            if (!classExists(type1, cl) && !classExists(type2, cl)) {
                return "java/lang/Throwable";
            }
        }

        java.util.List<String> chain1 = getSuperChainASM(type1, cl);
        java.util.List<String> chain2 = getSuperChainASM(type2, cl);

        for (String s1 : chain1) {
            if (chain2.contains(s1)) {
                if ("java/lang/Object".equals(s1)) {
                    return preferClientGuiMergeOverObject(type1, type2);
                }
                return s1;
            }
        }
        return preferClientGuiMergeOverObject(type1, type2);
    }

    private static java.util.List<String> getSuperChainASM(String internalName, ClassLoader cl) {
        java.util.List<String> chain = new java.util.ArrayList<>();
        String current = internalName;
        while (current != null && !"java/lang/Object".equals(current)) {
            chain.add(current);
            current = getDirectSuperASM(current, cl);
        }
        chain.add("java/lang/Object");
        return chain;
    }

    private static boolean classExists(String internalName, ClassLoader cl) {
        if (internalName == null) return false;
        if (internalName.startsWith("java/")) return true;
        try {
            Class.forName(internalName.replace('/', '.'), false, cl);
            return true;
        } catch (Throwable ignored) {}
        return MappingComposer.readIntermediaryClassBytesProbe(internalName) != null;
    }

    private static String getDirectSuperASM(String internalName, ClassLoader cl) {
        if (internalName == null || "java/lang/Object".equals(internalName)) return null;

        try {
            byte[] bytes = MappingComposer.readIntermediaryClassBytesProbe(internalName);
            if (bytes != null) {
                String superName = new ClassReader(bytes).getSuperName();
                if (superName != null && dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
                    return dev.stolym.vyne.mappings.MappingRegistry.get().mapClass(superName);
                }
                return superName;
            }
        } catch (Throwable ignored) {}

        if (internalName.startsWith("java/") || internalName.startsWith("net/fabricmc/")) {
            try {
                Class<?> cls = Class.forName(internalName.replace('/', '.'), false, cl);
                Class<?> sup = cls.getSuperclass();
                if (sup == null) return null;
                String superName = sup.getName().replace('.', '/');
                if (dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
                    return dev.stolym.vyne.mappings.MappingRegistry.get().mapClass(superName);
                }
                return superName;
            } catch (Throwable ignored) {}
        }

        return "java/lang/Object";
    }

    private static ClassWriter newTreeRewriteClassWriter() {
        return new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected ClassLoader getClassLoader() {
                ClassLoader cl = frameComputationClassLoader();
                return cl != null ? cl : super.getClassLoader();
            }

            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                if (isCategory2(type1) != isCategory2(type2)) {
                    return "java/lang/Object";
                }

                String reg = registrateBlockItemEntryFrameMerge(type1, type2);
                if (reg != null) return reg;

                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (Throwable t) {
                    return commonSuperTypeForFrameComputation(frameComputationClassLoader(), type1, type2);
                }
            }
        };
    }

    private static boolean isCategory2(String desc) {
        if (desc == null || desc.isEmpty()) return false;
        return desc.equals("D") || desc.equals("J");
    }

    public static byte[] patchCreativeTabBuilderCompatibility(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        String crClass = cr.getClassName();
        boolean preserveFrames = crClass != null
                && STACKMAP_RECOMPUTE_EXCLUDE.stream().anyMatch(crClass::endsWith);
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        cr.accept(cn, preserveFrames ? 0 : ClassReader.SKIP_FRAMES);
        boolean modified = false;
        final String shimDesc = "()Ljava/lang/Object;";
        final String creativeOwner = dev.stolym.vyne.mappings.MappingRegistry.isReady()
            ? dev.stolym.vyne.mappings.MappingRegistry.get()
                .mapClass("net/minecraft/world/item/CreativeModeTab")
            : "net/minecraft/class_1761";

        for (org.objectweb.asm.tree.MethodNode mn : cn.methods) {
            for (org.objectweb.asm.tree.AbstractInsnNode insn : mn.instructions.toArray()) {
                if (!(insn instanceof org.objectweb.asm.tree.MethodInsnNode mi)) continue;
                if (mi.getOpcode() != Opcodes.INVOKESTATIC) continue;
                if (!creativeOwner.equals(mi.owner)
                    && !"net/minecraft/world/item/CreativeModeTab".equals(mi.owner)) {
                    continue;
                }
                if (!"builder".equals(mi.name)) {
                    continue;
                }
                if (mi.desc == null || !mi.desc.startsWith("()L") || !mi.desc.endsWith(";")) {
                    continue;
                }
                if (mi.desc.indexOf('$') < 0) {
                    continue;
                }
                String retInternal = mi.desc.substring(2, mi.desc.length() - 1);
                if (retInternal.indexOf('/') < 0 && retInternal.startsWith("net.")) {
                    retInternal = retInternal.replace('.', '/');
                }

                mi.owner = "net/minecraftforge/vyne/VyneShims";
                mi.name = "createCreativeTabBuilderCompat";
                mi.desc = shimDesc;
                mn.instructions.insert(mi, new org.objectweb.asm.tree.TypeInsnNode(
                    Opcodes.CHECKCAST, retInternal));
                modified = true;
            }
        }
        if (!modified) return bytes;
        ClassWriter cw;
        if (preserveFrames) {
            cw = new ClassWriter(ClassWriter.COMPUTE_MAXS) {
                @Override
                protected ClassLoader getClassLoader() {
                    ClassLoader cl = frameComputationClassLoader();
                    return cl != null ? cl : super.getClassLoader();
                }
            };
        } else {
            cw = newTreeRewriteClassWriter();
        }
        cn.accept(cw);
        return cw.toByteArray();
    }

    public static byte[] patchCreativeTabBuilderNeoForgeExtensions(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        String crClassNeoExt = cr.getClassName();
        boolean preserveFramesNeoExt = crClassNeoExt != null
                && STACKMAP_RECOMPUTE_EXCLUDE.stream().anyMatch(crClassNeoExt::endsWith);
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        cr.accept(cn, preserveFramesNeoExt ? 0 : ClassReader.SKIP_FRAMES);
        boolean modified = false;
        final String shimOwner = "net/minecraftforge/vyne/VyneShims";
        final String shimName = "creativeTabBuilderWithTabsBefore";
        final String shimDesc = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
        String mappedBuilder = "net/minecraft/class_1761$class_7913";
        if (dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
            mappedBuilder = dev.stolym.vyne.mappings.MappingRegistry.get()
                .mapClass("net/minecraft/world/item/CreativeModeTab$Builder");
        }
        final java.util.Set<String> builderOwners = new java.util.HashSet<>();
        builderOwners.add("net/minecraft/world/item/CreativeModeTab$Builder");
        builderOwners.add("net/minecraft/class_1761$class_7913");
        if (mappedBuilder != null) {
            builderOwners.add(mappedBuilder);
        }
        for (org.objectweb.asm.tree.MethodNode mn : cn.methods) {
            for (org.objectweb.asm.tree.AbstractInsnNode insn : mn.instructions.toArray()) {
                if (!(insn instanceof org.objectweb.asm.tree.MethodInsnNode mi)) {
                    continue;
                }
                if (mi.getOpcode() != Opcodes.INVOKEVIRTUAL) {
                    continue;
                }
                if (!"withTabsBefore".equals(mi.name) || mi.owner == null || !builderOwners.contains(mi.owner)) {
                    continue;
                }
                org.objectweb.asm.Type ret = org.objectweb.asm.Type.getReturnType(mi.desc);
                if (ret.getSort() != org.objectweb.asm.Type.OBJECT && ret.getSort() != org.objectweb.asm.Type.ARRAY) {
                    continue;
                }
                mi.setOpcode(Opcodes.INVOKESTATIC);
                mi.itf = false;
                mi.owner = shimOwner;
                mi.name = shimName;
                mi.desc = shimDesc;
                mn.instructions.insert(mi, new org.objectweb.asm.tree.TypeInsnNode(
                    Opcodes.CHECKCAST, ret.getInternalName()));
                modified = true;
            }
        }
        if (!modified) {
            return bytes;
        }
        ClassWriter cw;
        if (preserveFramesNeoExt) {
            cw = new ClassWriter(ClassWriter.COMPUTE_MAXS) {
                @Override
                protected ClassLoader getClassLoader() {
                    ClassLoader cl = frameComputationClassLoader();
                    return cl != null ? cl : super.getClassLoader();
                }
            };
        } else {
            cw = newTreeRewriteClassWriter();
        }
        cn.accept(cw);
        return cw.toByteArray();
    }

    public static byte[] patchInheritedFieldAccessForDynamicEngine(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || !dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
            return bytes;
        }
        ClassLoader parent = dev.stolym.vyne.vcl.VCLManager.getCanonicalKnotLoader();
        if (parent == null) {
            parent = Thread.currentThread().getContextClassLoader();
        }
        if (parent == null) {
            parent = MappingComposer.class.getClassLoader();
        }
        RemappingClassLoader rcl = new RemappingClassLoader(new java.net.URL[0], parent);
        return rcl.patchInheritedFieldAccess(bytes);
    }

    public static byte[] applyNeoForgeFabricBytecodePatchesForDynamicEngine(byte[] bytes) {
        byte[] a = patchNeoForgeRecipeTypeSimple(bytes);
        a = patchCreativeTabBuilderCompatibility(a);
        return patchCreativeTabBuilderNeoForgeExtensions(a);
    }

    public static byte[] applyVyneRemapperToModBytesForDynamicEngine(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || !dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
            return bytes;
        }
        try {
            ClassReader reader = new ClassReader(bytes);
            ClassLoader knot = dev.stolym.vyne.vcl.VCLManager.getCanonicalKnotLoader();
            final ClassLoader sl = knot != null ? knot : Thread.currentThread().getContextClassLoader();
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS) {
                @Override
                protected String getCommonSuperClass(String type1, String type2) {
                    return commonSuperTypeForFrameComputation(sl, type1, type2);
                }
            };
            reader.accept(new VyneRemapper(Opcodes.ASM9, writer), 0);
            return writer.toByteArray();
        } catch (Throwable t) {
            return bytes;
        }
    }

    public static byte[] recomputeStackMapFramesForDynamicEngine(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return bytes;
        }
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = newTreeRewriteClassWriter();
            cr.accept(cw, ClassReader.SKIP_FRAMES);
            return cw.toByteArray();
        } catch (Throwable t) {
            return bytes;
        }
    }

    private byte[] patchBlockStateDefinitionCompatibility(byte[] bytes) {
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(bytes).accept(cn, 0);

        org.objectweb.asm.tree.MethodNode target = cn.methods.stream()
            .filter(m -> (m.access & Opcodes.ACC_STATIC) == 0)
            .filter(m -> m.desc.endsWith(")V"))
            .filter(m -> m.desc.contains("StateManager$Builder;")
                || m.desc.contains("class_2689$class_2690;"))
            .filter(m -> m.name.equals("createBlockStateDefinition")
                || m.name.equals("appendProperties"))
            .findFirst()
            .orElse(null);

        if (target == null) return bytes;

        boolean hasIntermediaryOverride = cn.methods.stream().anyMatch(m ->
            m.name.equals("method_9515") && m.desc.equals(target.desc));
        if (hasIntermediaryOverride) return bytes;

        int access = target.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
        org.objectweb.asm.tree.MethodNode bridge = new org.objectweb.asm.tree.MethodNode(
            access | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE,
            "method_9515",
            target.desc,
            null,
            null
        );
        bridge.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
        bridge.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
        bridge.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(
            (target.access & Opcodes.ACC_PRIVATE) != 0 ? Opcodes.INVOKESPECIAL : Opcodes.INVOKEVIRTUAL,
            cn.name,
            target.name,
            target.desc,
            false
        ));
        bridge.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
        cn.methods.add(bridge);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private byte[] patchEntityBlockCompatibility(byte[] bytes) {
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(bytes).accept(cn, 0);

        final String entityBlockIface = "net/minecraft/class_2343";
        final String beDesc = "(Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;)Lnet/minecraft/class_2586;";
        final String bridgeName = "method_10123";

        if (cn.interfaces == null || !cn.interfaces.contains(entityBlockIface)) {
            return bytes;
        }

        boolean hasBridge = cn.methods.stream().anyMatch(m ->
            bridgeName.equals(m.name) && beDesc.equals(m.desc));
        if (hasBridge) {
            return bytes;
        }

        org.objectweb.asm.tree.MethodNode delegate = null;
        for (String preferred : new String[]{"newBlockEntity", "createBlockEntity"}) {
            delegate = cn.methods.stream()
                .filter(m -> (m.access & Opcodes.ACC_STATIC) == 0)
                .filter(m -> beDesc.equals(m.desc))
                .filter(m -> preferred.equals(m.name))
                .filter(m -> (m.access & Opcodes.ACC_ABSTRACT) == 0)
                .findFirst()
                .orElse(null);
            if (delegate != null) {
                break;
            }
        }
        if (delegate == null) {
            delegate = cn.methods.stream()
                .filter(m -> (m.access & Opcodes.ACC_STATIC) == 0)
                .filter(m -> beDesc.equals(m.desc))
                .filter(m -> !bridgeName.equals(m.name))
                .filter(m -> (m.access & Opcodes.ACC_ABSTRACT) == 0)
                .filter(m -> (m.access & Opcodes.ACC_SYNTHETIC) == 0)
                .findFirst()
                .orElse(null);
        }
        if (delegate == null) {
            return bytes;
        }

        int access = delegate.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
        org.objectweb.asm.tree.MethodNode bridge = new org.objectweb.asm.tree.MethodNode(
            access | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE,
            bridgeName,
            beDesc,
            null,
            null
        );
        bridge.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
        bridge.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
        bridge.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
        int opcode = (delegate.access & Opcodes.ACC_PRIVATE) != 0 ? Opcodes.INVOKESPECIAL : Opcodes.INVOKEVIRTUAL;
        bridge.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(
            opcode,
            cn.name,
            delegate.name,
            delegate.desc,
            false
        ));
        bridge.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ARETURN));
        cn.methods.add(bridge);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private byte[] patchRecipeSerializerCodecCompatibility(byte[] bytes) {
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(bytes).accept(cn, 0);

        final String recipeSerializerIface = "net/minecraft/class_1865";
        final String mapCodecDesc = "()Lcom/mojang/serialization/MapCodec;";
        final String intermediaryCodec = "method_53736";
        final String streamCodecDesc = "()Lnet/minecraft/class_9139;";
        final String intermediaryStreamCodec = "method_56104";

        if (cn.interfaces == null || !cn.interfaces.contains(recipeSerializerIface)) {
            return bytes;
        }

        boolean modified = false;

        boolean hasIntermediaryCodec = cn.methods.stream().anyMatch(m ->
            m.name.equals(intermediaryCodec) && m.desc.equals(mapCodecDesc));
        if (!hasIntermediaryCodec) {
            org.objectweb.asm.tree.MethodNode codecTarget = cn.methods.stream()
                .filter(m -> (m.access & Opcodes.ACC_STATIC) == 0)
                .filter(m -> m.desc.equals(mapCodecDesc))
                .filter(m -> m.name.equals("codec"))
                .findFirst()
                .orElse(null);

            if (codecTarget == null && dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
                try {
                    String mojName = dev.stolym.vyne.mappings.MappingRegistry.get()
                        .unmapMethod(recipeSerializerIface, intermediaryCodec, mapCodecDesc);
                    codecTarget = cn.methods.stream()
                        .filter(m -> (m.access & Opcodes.ACC_STATIC) == 0)
                        .filter(m -> m.desc.equals(mapCodecDesc))
                        .filter(m -> m.name.equals(mojName))
                        .findFirst()
                        .orElse(null);
                } catch (Throwable ignored) {
                }
            }

            if (codecTarget == null) {
                codecTarget = cn.methods.stream()
                    .filter(m -> (m.access & Opcodes.ACC_STATIC) == 0)
                    .filter(m -> m.desc.equals(mapCodecDesc))
                    .findFirst()
                    .orElse(null);
            }

            if (codecTarget != null) {
                org.objectweb.asm.tree.MethodNode bridge = new org.objectweb.asm.tree.MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE,
                    intermediaryCodec,
                    mapCodecDesc,
                    null,
                    null
                );
                bridge.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                bridge.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(
                    (codecTarget.access & Opcodes.ACC_PRIVATE) != 0 ? Opcodes.INVOKESPECIAL : Opcodes.INVOKEVIRTUAL,
                    cn.name,
                    codecTarget.name,
                    codecTarget.desc,
                    false
                ));
                bridge.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ARETURN));
                cn.methods.add(bridge);
                modified = true;
            }
        }

        boolean hasIntermediaryStream = cn.methods.stream().anyMatch(m ->
            m.name.equals(intermediaryStreamCodec) && m.desc.equals(streamCodecDesc));
        if (!hasIntermediaryStream) {
            org.objectweb.asm.tree.MethodNode streamTarget = cn.methods.stream()
                .filter(m -> (m.access & Opcodes.ACC_STATIC) == 0)
                .filter(m -> m.desc.equals(streamCodecDesc))
                .filter(m -> m.name.equals("streamCodec"))
                .findFirst()
                .orElse(null);

            if (streamTarget == null && dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
                try {
                    String mojName = dev.stolym.vyne.mappings.MappingRegistry.get()
                        .unmapMethod(recipeSerializerIface, intermediaryStreamCodec, streamCodecDesc);
                    streamTarget = cn.methods.stream()
                        .filter(m -> (m.access & Opcodes.ACC_STATIC) == 0)
                        .filter(m -> m.desc.equals(streamCodecDesc))
                        .filter(m -> m.name.equals(mojName))
                        .findFirst()
                        .orElse(null);
                } catch (Throwable ignored) {
                }
            }

            if (streamTarget == null) {
                streamTarget = cn.methods.stream()
                    .filter(m -> (m.access & Opcodes.ACC_STATIC) == 0)
                    .filter(m -> m.desc.equals(streamCodecDesc))
                    .findFirst()
                    .orElse(null);
            }

            if (streamTarget != null) {
                org.objectweb.asm.tree.MethodNode bridge = new org.objectweb.asm.tree.MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE,
                    intermediaryStreamCodec,
                    streamCodecDesc,
                    null,
                    null
                );
                bridge.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                bridge.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(
                    (streamTarget.access & Opcodes.ACC_PRIVATE) != 0 ? Opcodes.INVOKESPECIAL : Opcodes.INVOKEVIRTUAL,
                    cn.name,
                    streamTarget.name,
                    streamTarget.desc,
                    false
                ));
                bridge.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ARETURN));
                cn.methods.add(bridge);
                modified = true;
            }
        }

        if (!modified) {
            return bytes;
        }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private byte[] patchCodecStartCompatibility(byte[] bytes) {
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        boolean modified = false;
        final String instanceDesc =
            "Lcom/mojang/serialization/codecs/RecordCodecBuilder$Instance;";

        for (int n = 1; n <= 16; n++) {
            final String newRet =
                "Lcom/mojang/serialization/codecs/RecordCodecBuilder$Products$P" + n + ";";
            final String oldRet = "Lcom/mojang/datafixers/Products$P" + n + ";";
            final String fullParams = "(" + instanceDesc + ")";

            org.objectweb.asm.tree.MethodNode recordMn = findCodecStartMethod(cn, fullParams + newRet);
            org.objectweb.asm.tree.MethodNode datafixersMn = findCodecStartMethod(cn, fullParams + oldRet);

            if (recordMn != null && datafixersMn == null
                && (recordMn.access & Opcodes.ACC_ABSTRACT) == 0) {
                boolean oldBridgeExists = cn.methods.stream().anyMatch(m ->
                    m.name.equals("codecStart") && m.desc.equals(fullParams + oldRet));
                if (!oldBridgeExists) {
                    int access = recordMn.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED
                        | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC);
                    org.objectweb.asm.tree.MethodNode bridge = new org.objectweb.asm.tree.MethodNode(
                        access | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE,
                        "codecStart",
                        fullParams + oldRet,
                        null,
                        null
                    );

                    boolean isStatic = (recordMn.access & Opcodes.ACC_STATIC) != 0;
                    emitCodecStartInvoke(bridge, cn.name, recordMn, isStatic);
                    bridge.instructions.add(new org.objectweb.asm.tree.TypeInsnNode(
                        Opcodes.CHECKCAST, "com/mojang/datafixers/Products$P" + n));
                    bridge.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ARETURN));
                    cn.methods.add(bridge);
                    modified = true;
                }
            }

            if (datafixersMn != null && recordMn == null
                && (datafixersMn.access & Opcodes.ACC_ABSTRACT) == 0) {
                boolean recordBridgeExists = cn.methods.stream().anyMatch(m ->
                    m.name.equals("codecStart") && m.desc.equals(fullParams + newRet));
                if (recordBridgeExists) continue;

                int access = datafixersMn.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED
                    | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC);
                org.objectweb.asm.tree.MethodNode bridge = new org.objectweb.asm.tree.MethodNode(
                    access | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE,
                    "codecStart",
                    fullParams + newRet,
                    null,
                    null
                );
                boolean isStatic = (datafixersMn.access & Opcodes.ACC_STATIC) != 0;
                emitCodecStartInvoke(bridge, cn.name, datafixersMn, isStatic);
                bridge.instructions.add(new org.objectweb.asm.tree.TypeInsnNode(
                    Opcodes.CHECKCAST,
                    "com/mojang/serialization/codecs/RecordCodecBuilder$Products$P" + n));
                bridge.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ARETURN));
                cn.methods.add(bridge);
                modified = true;
            }
        }

        if (patchInheritedCodecStartBridges(cn)) {
            modified = true;
        }

        if (!modified) return bytes;
        dev.stolym.vyne.util.VyneLogger.debug("[VCL] patchCodecStartCompatibility: updated " + cn.name);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static final String RECORD_CODEC_PRODUCTS =
        "Lcom/mojang/serialization/codecs/RecordCodecBuilder$Products$P";

    private static int recordCodecProductsArity(String desc) {
        if (desc == null) {
            return -1;
        }
        int i = desc.indexOf(RECORD_CODEC_PRODUCTS);
        if (i < 0) {
            return -1;
        }
        int p = i + RECORD_CODEC_PRODUCTS.length();
        int end = p;
        while (end < desc.length() && Character.isDigit(desc.charAt(end))) {
            end++;
        }
        if (end == p) {
            return -1;
        }
        return Integer.parseInt(desc.substring(p, end));
    }

    private static final String RECORD_CODEC_INSTANCE_PARAM =
        "(Lcom/mojang/serialization/codecs/RecordCodecBuilder$Instance;)";

    private static boolean isRecordCodecDispatchDescriptor(String desc) {
        return desc != null
            && desc.startsWith(RECORD_CODEC_INSTANCE_PARAM)
            && desc.contains(RECORD_CODEC_PRODUCTS);
    }

    private static org.objectweb.asm.tree.MethodNode pickCanonicalRecordCodecProductsMethod(
        org.objectweb.asm.tree.ClassNode ownerCn
    ) {
        java.util.List<org.objectweb.asm.tree.MethodNode> cands = new java.util.ArrayList<>();
        for (org.objectweb.asm.tree.MethodNode m : ownerCn.methods) {
            if (!isRecordCodecDispatchDescriptor(m.desc)) {
                continue;
            }
            cands.add(m);
        }
        org.objectweb.asm.tree.MethodNode canonical = null;
        int bestRank = -1;
        for (org.objectweb.asm.tree.MethodNode m : cands) {
            int r = recordCodecProductsArity(m.desc);
            if (r > bestRank) {
                bestRank = r;
                canonical = m;
            } else if (r == bestRank && r >= 0 && canonical != null) {
                boolean curSyn = (canonical.access & Opcodes.ACC_SYNTHETIC) != 0;
                boolean mSyn = (m.access & Opcodes.ACC_SYNTHETIC) != 0;
                if (curSyn && !mSyn) {
                    canonical = m;
                }
            }
        }
        return canonical;
    }

    private String computeCanonicalCodecStartDesc(String ownerInternal) {
        if (ownerInternal == null || ownerInternal.startsWith("java/")
            || ownerInternal.startsWith("jdk/")
            || ownerInternal.startsWith("com/sun/")) {
            return null;
        }
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        String walk = ownerInternal;
        while (walk != null && !"java/lang/Object".equals(walk)) {
            if (!seen.add(walk)) {
                break;
            }
            byte[] raw = tryLoadRawClassBytes(walk);
            if (raw == null) {
                break;
            }
            org.objectweb.asm.tree.ClassNode on = new org.objectweb.asm.tree.ClassNode();
            new ClassReader(raw).accept(on,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            org.objectweb.asm.tree.MethodNode canon = pickCanonicalRecordCodecProductsMethod(on);
            if (canon != null) {
                return canon.desc;
            }
            walk = on.superName;
        }
        return null;
    }

    private byte[] patchCodecStartInsnDescriptorsToMatch(byte[] bytes) {
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(bytes).accept(cn, 0);

        boolean changed = false;
        for (org.objectweb.asm.tree.MethodNode mn : cn.methods) {
            for (org.objectweb.asm.tree.AbstractInsnNode insn : mn.instructions.toArray()) {
                if (!(insn instanceof org.objectweb.asm.tree.MethodInsnNode mi)) {
                    continue;
                }
                if (!isRecordCodecDispatchDescriptor(mi.desc)) {
                    continue;
                }
                String canonDesc = canonicalCodecStartDesc.computeIfAbsent(mi.owner, o -> {
                    String c = computeCanonicalCodecStartDesc(o);
                    return c != null ? c : "";
                });
                if (canonDesc.isEmpty() || canonDesc.equals(mi.desc)) {
                    continue;
                }
                mi.desc = canonDesc;
                changed = true;
            }
        }
        if (!changed) {
            return bytes;
        }
        org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private boolean patchInheritedCodecStartBridges(org.objectweb.asm.tree.ClassNode cn) {
        final String instanceDesc =
            "Lcom/mojang/serialization/codecs/RecordCodecBuilder$Instance;";
        final String fullParams = "(" + instanceDesc + ")";
        boolean changed = false;
        java.util.ArrayDeque<String> deque = new java.util.ArrayDeque<>();
        if (cn.superName != null) {
            deque.add(cn.superName);
        }
        for (String itf : cn.interfaces) {
            deque.add(itf);
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (!deque.isEmpty()) {
            String walk = deque.removeFirst();
            if (walk == null
                || "java/lang/Object".equals(walk)
                || walk.startsWith("java/")
                || !seen.add(walk)) {
                continue;
            }
            byte[] sbytes = tryLoadRawClassBytes(walk);
            if (sbytes == null) {
                continue;
            }
            org.objectweb.asm.tree.ClassNode sn = new org.objectweb.asm.tree.ClassNode();
            new ClassReader(sbytes).accept(sn,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (sn.superName != null) {
                deque.add(sn.superName);
            }
            for (String itf : sn.interfaces) {
                deque.add(itf);
            }
            for (int n = 1; n <= 16; n++) {
                final String oldRet = "Lcom/mojang/datafixers/Products$P" + n + ";";
                final String newRet =
                    "Lcom/mojang/serialization/codecs/RecordCodecBuilder$Products$P" + n + ";";
                org.objectweb.asm.tree.MethodNode datafixersMn =
                    findCodecStartMethod(sn, fullParams + oldRet);
                if (datafixersMn == null || (datafixersMn.access & Opcodes.ACC_ABSTRACT) != 0) {
                    continue;
                }
                if ((datafixersMn.access & Opcodes.ACC_PRIVATE) != 0) {
                    continue;
                }
                if (findCodecStartMethod(cn, fullParams + newRet) != null
                    || findCodecStartMethod(cn, fullParams + oldRet) != null) {
                    continue;
                }
                boolean defInterface = (sn.access & Opcodes.ACC_INTERFACE) != 0;
                addDelegatingCodecStartBridge(
                    cn, walk, datafixersMn, n, fullParams, newRet, defInterface);
                changed = true;
            }
        }
        return changed;
    }

    private byte[] tryLoadRawClassBytes(String internalName) {
        String path = internalName + ".class";
        try (InputStream is = getResourceAsStream(path)) {
            if (is != null) {
                return is.readAllBytes();
            }
        } catch (IOException ignored) {
        }

        ClassLoader p = getParent();
        while (p != null) {
            try (InputStream is = p.getResourceAsStream(path)) {
                if (is != null) {
                    return is.readAllBytes();
                }
            } catch (IOException ignored) {
            }
            p = p.getParent();
        }
        return null;
    }

    private static void addDelegatingCodecStartBridge(
        org.objectweb.asm.tree.ClassNode cn,
        String definingSuperInternal,
        org.objectweb.asm.tree.MethodNode superMethod,
        int n,
        String fullParams,
        String newRet,
        boolean definingIsInterface) {
        boolean isStatic = (superMethod.access & Opcodes.ACC_STATIC) != 0;
        int access = superMethod.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED
            | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC);
        org.objectweb.asm.tree.MethodNode bridge = new org.objectweb.asm.tree.MethodNode(
            access | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE,
            "codecStart",
            fullParams + newRet,
            null,
            null
        );
        int local = 0;
        if (!isStatic) {
            bridge.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, local++));
        }
        org.objectweb.asm.Type[] args =
            org.objectweb.asm.Type.getArgumentTypes(superMethod.desc);
        for (org.objectweb.asm.Type arg : args) {
            bridge.instructions.add(
                new org.objectweb.asm.tree.VarInsnNode(arg.getOpcode(Opcodes.ILOAD), local));
            local += arg.getSize();
        }
        int op;
        if (isStatic) {
            op = Opcodes.INVOKESTATIC;
        } else if (definingIsInterface) {
            op = Opcodes.INVOKEINTERFACE;
        } else {
            op = Opcodes.INVOKESPECIAL;
        }
        bridge.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(
            op, definingSuperInternal, "codecStart", superMethod.desc,
            op == Opcodes.INVOKEINTERFACE));
        bridge.instructions.add(new org.objectweb.asm.tree.TypeInsnNode(
            Opcodes.CHECKCAST,
            "com/mojang/serialization/codecs/RecordCodecBuilder$Products$P" + n));
        bridge.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ARETURN));
        cn.methods.add(bridge);
    }

    private static org.objectweb.asm.tree.MethodNode findCodecStartMethod(
        org.objectweb.asm.tree.ClassNode cn, String desc) {
        for (org.objectweb.asm.tree.MethodNode m : cn.methods) {
            if ("codecStart".equals(m.name) && desc.equals(m.desc)) {
                return m;
            }
        }
        return null;
    }

    private static void emitCodecStartInvoke(
        org.objectweb.asm.tree.MethodNode bridge,
        String ownerInternal,
        org.objectweb.asm.tree.MethodNode target,
        boolean isStatic) {
        org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(target.desc);
        int local = 0;
        if (!isStatic) {
            bridge.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, local++));
        }
        for (org.objectweb.asm.Type arg : args) {
            bridge.instructions.add(new org.objectweb.asm.tree.VarInsnNode(arg.getOpcode(Opcodes.ILOAD), local));
            local += arg.getSize();
        }
        int op;
        if (isStatic) {
            op = Opcodes.INVOKESTATIC;
        } else if ((target.access & Opcodes.ACC_PRIVATE) != 0) {
            op = Opcodes.INVOKESPECIAL;
        } else {
            op = Opcodes.INVOKEVIRTUAL;
        }
        bridge.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(
            op, ownerInternal, "codecStart", target.desc, false));
    }

    private byte[] patchDeferredRegisterCompatibility(byte[] bytes, boolean enable) {
        if (!enable) return bytes;
        return patchDeferredRegisterCompatibility(bytes);
    }

    private byte[] patchDeferredRegisterCompatibility(byte[] bytes) {
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        boolean modified = false;
        final String owner = "net/neoforged/neoforge/registries/DeferredRegister";
        final String oldDesc =
            "(Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/neoforged/neoforge/registries/DeferredHolder;";
        final String newDesc =
            "(Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/neoforged/neoforge/registries/RegistryObject;";

        for (org.objectweb.asm.tree.MethodNode mn : cn.methods) {
            for (org.objectweb.asm.tree.AbstractInsnNode insn : mn.instructions.toArray()) {
                if (!(insn instanceof org.objectweb.asm.tree.MethodInsnNode mi)) continue;
                if (!mi.owner.equals(owner) || !mi.name.equals("register")) continue;
                if (!mi.desc.equals(oldDesc)) continue;
                mi.desc = newDesc;
                modified = true;
            }
        }
        if (!modified) return bytes;
        org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(0);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private byte[] patchInheritedFieldAccess(byte[] bytes) {
        if (!dev.stolym.vyne.mappings.MappingRegistry.isReady()) return bytes;
        ClassReader preRead = new ClassReader(bytes);
        String preReadClass = preRead.getClassName();
        boolean preserveFrames = preReadClass != null
                && STACKMAP_RECOMPUTE_EXCLUDE.stream().anyMatch(preReadClass::endsWith);
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        preRead.accept(cn, preserveFrames ? 0 : ClassReader.SKIP_FRAMES);
        boolean modified = false;
        for (org.objectweb.asm.tree.MethodNode mn : cn.methods) {
            for (org.objectweb.asm.tree.AbstractInsnNode insn : mn.instructions.toArray()) {
                if (!(insn instanceof org.objectweb.asm.tree.FieldInsnNode fi)) continue;

                if ("stateDefinition".equals(fi.name)) {
                    dev.stolym.vyne.mappings.MappingComposer.ComposedMappings map =
                        dev.stolym.vyne.mappings.MappingRegistry.get();
                    String inter = resolveStateDefinitionFieldInterName(fi.owner);
                    if (inter == null || inter.equals(fi.name)) {
                        inter = map.mapField("net/minecraft/world/level/block/Block", "stateDefinition");
                    }
                    if (inter != null && !inter.equals(fi.name)) {
                        fi.name = inter;
                        modified = true;
                        continue;
                    }
                }

                if ("DISTANCE".equals(fi.name) || "PERSISTENT".equals(fi.name)
                        || "WATERLOGGED".equals(fi.name)) {
                    if (hierarchyExtendsMojmapClass(fi.owner,
                            "net/minecraft/world/level/block/LeavesBlock")) {
                        dev.stolym.vyne.mappings.MappingComposer.ComposedMappings map =
                            dev.stolym.vyne.mappings.MappingRegistry.get();
                        String n = map.mapField(
                            "net/minecraft/world/level/block/LeavesBlock", fi.name);
                        if (!n.equals(fi.name)) {
                            fi.name = n;
                            fi.owner = map.mapClass("net/minecraft/world/level/block/LeavesBlock");
                            modified = true;
                            continue;
                        }
                    }
                }

                if ("AGE".equals(fi.name) && fi.desc != null
                        && (fi.desc.contains("class_2758")
                            || fi.desc.contains("IntegerProperty"))) {
                    dev.stolym.vyne.mappings.MappingComposer.ComposedMappings map =
                        dev.stolym.vyne.mappings.MappingRegistry.get();
                    boolean stem = hierarchyExtendsMojmapClass(fi.owner,
                        "net/minecraft/world/level/block/AbstractPlantStemBlock");
                    boolean head = hierarchyExtendsMojmapClass(fi.owner,
                        "net/minecraft/world/level/block/GrowingPlantHeadBlock");
                    if (stem || head) {
                        String n = map.mapField(
                            "net/minecraft/world/level/block/AbstractPlantStemBlock", "AGE");
                        if (n.equals(fi.name) && head) {
                            n = map.mapField(
                                "net/minecraft/world/level/block/GrowingPlantHeadBlock", "AGE");
                        }
                        if (!n.equals(fi.name)) {
                            fi.name = n;
                            fi.owner = map.mapClass(
                                "net/minecraft/world/level/block/AbstractPlantStemBlock");
                            modified = true;
                            continue;
                        }
                    }
                }

                if ("FACING".equals(fi.name) && fi.desc != null
                        && (fi.desc.contains("DirectionProperty")
                            || fi.desc.contains("class_2753"))) {
                    dev.stolym.vyne.mappings.MappingComposer.ComposedMappings mapFacing =
                        dev.stolym.vyne.mappings.MappingRegistry.get();
                    final String[] mojFacingAnchors = {
                        "net/minecraft/world/level/block/HorizontalDirectionalBlock",
                        "net/minecraft/world/level/block/FaceAttachedHorizontalDirectionalBlock",
                        "net/minecraft/world/level/block/DirectionalBlock",
                    };
                    String declaringMojFacing = null;
                    String sWalk = fi.owner;
                    for (int d = 0; d < 64 && sWalk != null; d++) {
                        String unWalk = mapFacing.unmapClass(sWalk);
                        if (unWalk != null) {
                            for (String anchor : mojFacingAnchors) {
                                if (anchor.equals(unWalk)) {
                                    declaringMojFacing = anchor;
                                    break;
                                }
                            }
                        }
                        if (declaringMojFacing != null) {
                            break;
                        }
                        for (String anchor : mojFacingAnchors) {
                            if (sWalk.equals(mapFacing.mapClass(anchor))) {
                                declaringMojFacing = anchor;
                                break;
                            }
                        }
                        if (declaringMojFacing != null) {
                            break;
                        }
                        String supWalk;
                        if (d == 0 && fi.owner.equals(cn.name)) {
                            supWalk = cn.superName;
                        } else {
                            supWalk = readSuperInternalNameFromClasspath(sWalk);
                        }
                        sWalk = supWalk;
                    }
                    if (declaringMojFacing != null) {
                        fi.name = mapFacing.mapField(declaringMojFacing, "FACING");
                        fi.owner = mapFacing.mapClass(declaringMojFacing);
                        modified = true;
                        continue;
                    }
                }

                boolean staticRef = fi.getOpcode() == Opcodes.GETSTATIC
                    || fi.getOpcode() == Opcodes.PUTSTATIC;
                if (staticRef && fi.owner != null && fi.name != null) {
                    char c0 = fi.owner.charAt(0);
                    if (c0 != '[' && !fi.owner.startsWith("java/")
                        && !fi.owner.startsWith("jdk/") && !fi.owner.startsWith("sun/")
                        && !fi.owner.startsWith("net/minecraft/")
                        && !fi.owner.startsWith("com/mojang/")
                        && !fi.owner.startsWith("org/lwjgl/")) {
                        String decl = findDeclaringInternalForStaticField(
                            fi.owner, fi.name, fi.desc, cn);
                        if (decl != null && !decl.equals(fi.owner)) {
                            fi.owner = decl;
                            modified = true;
                            continue;
                        }
                    }
                }
                String mappedName = resolveFieldThroughHierarchy(
                    fi.owner, fi.name, fi.desc, staticRef);
                if (!mappedName.equals(fi.name)) {
                    fi.name = mappedName;
                    modified = true;
                }
            }
        }
        if (!modified) return bytes;
        ClassWriter cw;
        if (preserveFrames) {
            cw = new ClassWriter(ClassWriter.COMPUTE_MAXS) {
                @Override
                protected ClassLoader getClassLoader() {
                    ClassLoader cl = frameComputationClassLoader();
                    return cl != null ? cl : super.getClassLoader();
                }
            };
        } else {
            cw = newTreeRewriteClassWriter();
        }
        cn.accept(cw);
        return cw.toByteArray();
    }

    private String resolveFieldThroughHierarchy(String owner, String fieldName, String fieldDesc,
                                                boolean staticRef) {
        dev.stolym.vyne.mappings.MappingComposer.ComposedMappings mappings =
            dev.stolym.vyne.mappings.MappingRegistry.get();
        String mojOwner = mappings.unmapClass(owner);
        String mapped = mappings.mapField(mojOwner, fieldName);
        if (!mapped.equals(fieldName)) return mapped;
        mapped = mappings.mapField(owner, fieldName);
        if (!mapped.equals(fieldName)) return mapped;
        if (staticRef) {
            return fieldName;
        }
        try {
            Class<?> cls = Class.forName(owner.replace('/', '.'), false, this);
            while (cls != null) {
                String interOwner = cls.getName().replace('.', '/');
                String moj = mappings.unmapClass(interOwner);
                mapped = mappings.mapField(moj, fieldName);
                if (!mapped.equals(fieldName)) return mapped;
                mapped = mappings.mapField(interOwner, fieldName);
                if (!mapped.equals(fieldName)) return mapped;
                cls = cls.getSuperclass();
            }
        } catch (Throwable ignored) {}
        if ("stateDefinition".equals(fieldName) && fieldDesc != null
                && (fieldDesc.contains("class_2689")
                    || fieldDesc.contains("world/level/block/state/StateDefinition"))) {
            String viaHierarchy = resolveStateDefinitionMappedNameInternal(owner);
            if (viaHierarchy != null && !viaHierarchy.equals(fieldName)) {
                return viaHierarchy;
            }
        }
        return fieldName;
    }

    private String resolveStateDefinitionFieldInterName(String ownerSlash) {
        return resolveStateDefinitionMappedNameInternal(ownerSlash);
    }

    private String resolveStateDefinitionMappedNameInternal(String ownerSlash) {
        if (!dev.stolym.vyne.mappings.MappingRegistry.isReady() || ownerSlash == null) {
            return null;
        }
        dev.stolym.vyne.mappings.MappingComposer.ComposedMappings mappings =
            dev.stolym.vyne.mappings.MappingRegistry.get();
        if (hierarchyExtendsMojmapClass(ownerSlash, "net/minecraft/world/level/block/Block")) {
            return mappings.mapField("net/minecraft/world/level/block/Block", "stateDefinition");
        }
        if (hierarchyExtendsMojmapClass(ownerSlash, "net/minecraft/world/level/material/Fluid")) {
            return mappings.mapField("net/minecraft/world/level/material/Fluid", "stateDefinition");
        }
        return null;
    }

    private boolean hierarchyExtendsMojmapClass(String ownerSlash, String mojClassSlash) {
        dev.stolym.vyne.mappings.MappingComposer.ComposedMappings mappings =
            dev.stolym.vyne.mappings.MappingRegistry.get();
        String targetInter = mappings.mapClass(mojClassSlash);
        String s = ownerSlash;
        for (int depth = 0; depth < 64 && s != null; depth++) {
            if (s.equals(targetInter)) {
                return true;
            }
            String un = mappings.unmapClass(s);
            if (mojClassSlash.equals(un)) {
                return true;
            }
            s = readSuperInternalNameFromClasspath(s);
        }
        return false;
    }

    private byte[] readRawClassBytesForRcl(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            return null;
        }
        String path = internalName + ".class";
        try {
            ClassLoader probe = dev.stolym.vyne.mappings.MappingComposer.peekClassByteProbeLoader();
            if (probe != null) {
                try (InputStream pin = probe.getResourceAsStream(path)) {
                    if (pin != null) {
                        byte[] fromProbe = pin.readAllBytes();
                        if (fromProbe.length > 0) {
                            return fromProbe;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            byte[] raw = null;
            try (InputStream is = getResourceAsStream(path)) {
                if (is != null) {
                    raw = is.readAllBytes();
                }
            }
            if (raw == null || raw.length == 0) {
                try (InputStream is2 = getResourceAsStream("/" + path)) {
                    if (is2 != null) {
                        raw = is2.readAllBytes();
                    }
                }
            }
            if (raw == null || raw.length == 0) {
                ClassLoader par = getParent();
                if (par != null) {
                    try (InputStream is3 = par.getResourceAsStream(path)) {
                        if (is3 != null) {
                            raw = is3.readAllBytes();
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (raw == null || raw.length == 0) {
                raw = dev.stolym.vyne.mappings.MappingComposer.readIntermediaryClassBytesProbe(internalName);
            }
            return raw != null && raw.length > 0 ? raw : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String findDeclaringInternalForStaticField(
        String owner,
        String fieldName,
        String fieldDesc,
        org.objectweb.asm.tree.ClassNode definingCn) {
        String s = owner;
        for (int depth = 0; depth < 64 && s != null; depth++) {
            org.objectweb.asm.tree.ClassNode node;
            if (definingCn != null && s.equals(definingCn.name)) {
                node = definingCn;
            } else {
                byte[] raw = readRawClassBytesForRcl(s);
                if (raw == null || raw.length == 0) {
                    return null;
                }
                node = new org.objectweb.asm.tree.ClassNode();
                new ClassReader(raw).accept(node,
                    ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
            for (org.objectweb.asm.tree.FieldNode fn : node.fields) {
                if (!fieldName.equals(fn.name)) {
                    continue;
                }
                if ((fn.access & Opcodes.ACC_STATIC) != 0) {
                    return node.name;
                }
            }
            s = node.superName;
        }
        return null;
    }

    private String readSuperInternalNameFromClasspath(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            return null;
        }
        try {
            byte[] raw = readRawClassBytesForRcl(internalName);
            if (raw == null) {
                return null;
            }
            return new ClassReader(raw).getSuperName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private byte[] patchOptionalMenuSetCraftingSize(byte[] bytes) {
        if (!dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
            return bytes;
        }
        dev.stolym.vyne.mappings.MappingComposer.ComposedMappings map =
            dev.stolym.vyne.mappings.MappingRegistry.get();
        String craftOwner = map.mapClass("net/minecraft/world/inventory/CraftingMenu");
        String patternOwner = map.mapClass("net/minecraft/world/item/crafting/ShapedRecipePattern");
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        boolean modified = false;
        for (org.objectweb.asm.tree.MethodNode mn : cn.methods) {
            if (mn.instructions == null) {
                continue;
            }
            for (org.objectweb.asm.tree.AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn instanceof org.objectweb.asm.tree.MethodInsnNode mi) {
                    int op = mi.getOpcode();
                    if ("setCraftingSize".equals(mi.name) && "(II)V".equals(mi.desc)) {
                        if ((op == Opcodes.INVOKEVIRTUAL || op == Opcodes.INVOKESPECIAL)
                                && craftOwner.equals(mi.owner)) {
                            mi.setOpcode(Opcodes.INVOKESTATIC);
                            mi.owner = "net/minecraftforge/vyne/VyneShims";
                            mi.name = "invokeSetCraftingSizeIfPresent";
                            mi.desc = "(Ljava/lang/Object;II)V";
                            mi.itf = false;
                            modified = true;
                            continue;
                        }
                        if (op == Opcodes.INVOKESTATIC && patternOwner.equals(mi.owner)) {
                            mi.owner = "net/minecraftforge/vyne/VyneShims";
                            mi.name = "invokeShapedRecipePatternSetCraftingSizeIfPresent";
                            mi.itf = false;
                            modified = true;
                        }
                    }
                    continue;
                }
                if (!(insn instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode indy)) {
                    continue;
                }
                Object[] args = indy.bsmArgs;
                if (args == null) {
                    continue;
                }
                for (int i = 0; i < args.length; i++) {
                    if (!(args[i] instanceof org.objectweb.asm.Handle h)) {
                        continue;
                    }
                    if (!"setCraftingSize".equals(h.getName()) || h.getDesc() == null) {
                        continue;
                    }
                    int tag = h.getTag();
                    if ((tag == Opcodes.H_INVOKEVIRTUAL || tag == Opcodes.H_INVOKESPECIAL)
                            && craftOwner.equals(h.getOwner())
                            && "(II)V".equals(h.getDesc())) {
                        args[i] = new org.objectweb.asm.Handle(
                            Opcodes.H_INVOKESTATIC,
                            "net/minecraftforge/vyne/VyneShims",
                            "invokeSetCraftingSizeIfPresent",
                            "(Ljava/lang/Object;II)V",
                            false);
                        modified = true;
                    } else if (tag == Opcodes.H_INVOKESTATIC
                            && patternOwner.equals(h.getOwner())
                            && "(II)V".equals(h.getDesc())) {
                        args[i] = new org.objectweb.asm.Handle(
                            Opcodes.H_INVOKESTATIC,
                            "net/minecraftforge/vyne/VyneShims",
                            "invokeShapedRecipePatternSetCraftingSizeIfPresent",
                            "(II)V",
                            false);
                        modified = true;
                    }
                }
            }
        }
        if (!modified) {
            return bytes;
        }
        org.objectweb.asm.ClassWriter cw =
            new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    public static byte[] patchNeoForgeRecipeTypeSimple(byte[] bytes) {
        if (!dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
            return bytes;
        }
        String recipeOwner = dev.stolym.vyne.mappings.MappingRegistry.get()
            .mapClass("net/minecraft/world/item/crafting/RecipeType");
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        boolean modified = false;
        for (org.objectweb.asm.tree.MethodNode mn : cn.methods) {
            if (mn.instructions == null) {
                continue;
            }
            for (org.objectweb.asm.tree.AbstractInsnNode insn : mn.instructions.toArray()) {
                if (!(insn instanceof org.objectweb.asm.tree.MethodInsnNode mi)) {
                    continue;
                }
                if (mi.getOpcode() != Opcodes.INVOKESTATIC) {
                    continue;
                }
                boolean recipeOwnerMatch = recipeOwner.equals(mi.owner)
                    || "net/minecraft/world/item/crafting/RecipeType".equals(mi.owner);
                if (!recipeOwnerMatch || !"simple".equals(mi.name) || mi.desc == null) {
                    continue;
                }
                boolean idArg = mi.desc.startsWith("(Lnet/minecraft/class_2960;)")
                    || mi.desc.startsWith("(Lnet/minecraft/resources/ResourceLocation;");
                if (!idArg) {
                    continue;
                }
                if (!mi.desc.contains(")L" + recipeOwner + ";")
                    && !mi.desc.endsWith(")Lnet/minecraft/world/item/crafting/RecipeType;")) {
                    continue;
                }
                int retStart = mi.desc.indexOf(")L");
                if (retStart < 0) {
                    continue;
                }
                String retInternal = mi.desc.substring(retStart + 2, mi.desc.length() - 1);
                if (retInternal.indexOf('/') < 0 && retInternal.startsWith("net.")) {
                    retInternal = retInternal.replace('.', '/');
                }
                mi.owner = "net/minecraftforge/vyne/VyneShims";
                mi.name = "recipeTypeSimpleCompat";
                mi.desc = "(Lnet/minecraft/class_2960;)Ljava/lang/Object;";
                mi.itf = false;
                mn.instructions.insert(mi, new org.objectweb.asm.tree.TypeInsnNode(
                    Opcodes.CHECKCAST, retInternal));
                modified = true;
            }
        }
        if (!modified) {
            return bytes;
        }
        org.objectweb.asm.ClassWriter cw =
            new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private byte[] patchKeyMappingExtensionCalls(byte[] bytes) {
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        boolean modified = false;
        final String keyMappingOwner = "net/minecraft/class_304";
        final String inputKeyDesc = "Lnet/minecraft/class_3675$class_306;";
        final String targetDesc = "(" + inputKeyDesc + ")Z";
        final String shimDesc = "(Ljava/lang/Object;Ljava/lang/Object;)Z";

        for (org.objectweb.asm.tree.MethodNode mn : cn.methods) {
            if (mn.instructions == null) continue;
            for (org.objectweb.asm.tree.AbstractInsnNode insn : mn.instructions.toArray()) {
                if (!(insn instanceof org.objectweb.asm.tree.MethodInsnNode mi)) continue;
                if (mi.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                if (!keyMappingOwner.equals(mi.owner)) continue;
                if (!"isActiveAndMatches".equals(mi.name)) continue;
                if (!targetDesc.equals(mi.desc)) continue;

                mi.setOpcode(Opcodes.INVOKESTATIC);
                mi.owner = "net/minecraftforge/vyne/VyneShims";
                mi.desc = shimDesc;
                modified = true;
            }
        }
        if (!modified) return bytes;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private byte[] patchUnsupportedOperations(byte[] bytes) {
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        boolean modified = false;
        for (org.objectweb.asm.tree.MethodNode mn : cn.methods) {
            if ((mn.access & Opcodes.ACC_ABSTRACT) != 0) continue;
            if (mn.name.equals("<clinit>") || mn.name.equals("<init>")) continue;
            boolean hasUnsupported = false;
            for (org.objectweb.asm.tree.AbstractInsnNode insn : mn.instructions) {
                if (insn instanceof org.objectweb.asm.tree.TypeInsnNode ti
                        && ti.getOpcode() == Opcodes.NEW
                        && ti.desc.equals("java/lang/UnsupportedOperationException")) {
                    hasUnsupported = true;
                    break;
                }
            }
            if (!hasUnsupported) continue;
            String key = cn.name.replace('/', '.') + "#" + mn.name + mn.desc;
            dev.stolym.vyne.vcl.VyneMethodDispatchTable.Dispatch dispatch =
                dev.stolym.vyne.vcl.VyneMethodDispatchTable.lookup(key);
            if (dispatch == null)
                dispatch = dev.stolym.vyne.vcl.VyneMethodDispatchTable
                    .lookupBySignature(mn.name, mn.desc);
            org.objectweb.asm.Type ret = org.objectweb.asm.Type.getReturnType(mn.desc);
            mn.instructions.clear();
            mn.tryCatchBlocks.clear();
            mn.localVariables = null;
            if (dispatch == null) {
                emitDefaultReturn(mn, ret);
            } else {
                emitDispatchCall(mn, key, ret,
                    org.objectweb.asm.Type.getArgumentTypes(mn.desc),
                    (mn.access & Opcodes.ACC_STATIC) != 0);
            }
            modified = true;
        }
        if (!modified) return bytes;
        ClassWriter writer = buildWriter();
        cn.accept(writer);
        return writer.toByteArray();
    }

    private void emitDefaultReturn(org.objectweb.asm.tree.MethodNode mn,
                                   org.objectweb.asm.Type ret) {
        switch (ret.getSort()) {
            case org.objectweb.asm.Type.VOID ->
                mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
            case org.objectweb.asm.Type.BOOLEAN,
                 org.objectweb.asm.Type.BYTE,
                 org.objectweb.asm.Type.CHAR,
                 org.objectweb.asm.Type.SHORT,
                 org.objectweb.asm.Type.INT -> {
                mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_0));
                mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IRETURN));
            }
            case org.objectweb.asm.Type.LONG -> {
                mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.LCONST_0));
                mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.LRETURN));
            }
            case org.objectweb.asm.Type.FLOAT -> {
                mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.FCONST_0));
                mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.FRETURN));
            }
            case org.objectweb.asm.Type.DOUBLE -> {
                mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.DCONST_0));
                mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.DRETURN));
            }
            default -> {
                mn.instructions.add(new org.objectweb.asm.tree.LdcInsnNode(ret.getDescriptor()));
                mn.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "dev/stolym/vyne/vcl/VyneStubDefaults",
                    "defaultFor",
                    "(Ljava/lang/String;)Ljava/lang/Object;",
                    false));
                mn.instructions.add(new org.objectweb.asm.tree.TypeInsnNode(
                    Opcodes.CHECKCAST, ret.getInternalName()));
                mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ARETURN));
            }
        }
    }

    private void emitDispatchCall(org.objectweb.asm.tree.MethodNode mn, String key,
                                  org.objectweb.asm.Type ret,
                                  org.objectweb.asm.Type[] args, boolean isStatic) {
        mn.instructions.add(new org.objectweb.asm.tree.LdcInsnNode(key));
        mn.instructions.add(isStatic
            ? new org.objectweb.asm.tree.InsnNode(Opcodes.ACONST_NULL)
            : new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new org.objectweb.asm.tree.LdcInsnNode(args.length));
        mn.instructions.add(new org.objectweb.asm.tree.TypeInsnNode(
            Opcodes.ANEWARRAY, "java/lang/Object"));
        int slot = isStatic ? 0 : 1;
        for (int i = 0; i < args.length; i++) {
            mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.DUP));
            mn.instructions.add(new org.objectweb.asm.tree.LdcInsnNode(i));
            mn.instructions.add(new org.objectweb.asm.tree.VarInsnNode(
                args[i].getOpcode(Opcodes.ILOAD), slot));
            slot += args[i].getSize();
            if (args[i].getSort() < org.objectweb.asm.Type.ARRAY) {
                String box = boxedType(args[i]);
                mn.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(
                    Opcodes.INVOKESTATIC, box, "valueOf",
                    "(" + args[i].getDescriptor() + ")L" + box + ";", false));
            }
            mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.AASTORE));
        }
        mn.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "dev/stolym/vyne/vcl/VyneMethodDispatchTable", "invoke",
            "(Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
            false));
        if (ret.getSort() == org.objectweb.asm.Type.VOID) {
            mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.POP));
            mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
        } else if (ret.getSort() < org.objectweb.asm.Type.ARRAY) {
            String unbox = boxedType(ret);
            mn.instructions.add(new org.objectweb.asm.tree.TypeInsnNode(
                Opcodes.CHECKCAST, unbox));
            mn.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, unbox, unboxMethod(ret),
                "()" + ret.getDescriptor(), false));
            mn.instructions.add(new org.objectweb.asm.tree.InsnNode(
                ret.getOpcode(Opcodes.IRETURN)));
        } else {
            mn.instructions.add(new org.objectweb.asm.tree.TypeInsnNode(
                Opcodes.CHECKCAST, ret.getInternalName()));
            mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ARETURN));
        }
    }

    private ClassWriter buildWriter() {
        return new ClassWriter(ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                try {
                    Class<?> c1 = Class.forName(type1.replace('/', '.'), false,
                        RemappingClassLoader.this);
                    Class<?> c2 = Class.forName(type2.replace('/', '.'), false,
                        RemappingClassLoader.this);
                    if (c1.isAssignableFrom(c2)) return type1;
                    if (c2.isAssignableFrom(c1)) return type2;
                    if (c1.isInterface() || c2.isInterface()) return "java/lang/Object";
                    do { c1 = c1.getSuperclass(); } while (!c1.isAssignableFrom(c2));
                    return c1.getName().replace('.', '/');
                } catch (Exception e) {
                    return "java/lang/Object";
                }
            }
        };
    }

    private static String boxedType(org.objectweb.asm.Type t) {
        return switch (t.getSort()) {
            case org.objectweb.asm.Type.BOOLEAN -> "java/lang/Boolean";
            case org.objectweb.asm.Type.BYTE    -> "java/lang/Byte";
            case org.objectweb.asm.Type.CHAR    -> "java/lang/Character";
            case org.objectweb.asm.Type.SHORT   -> "java/lang/Short";
            case org.objectweb.asm.Type.INT     -> "java/lang/Integer";
            case org.objectweb.asm.Type.LONG    -> "java/lang/Long";
            case org.objectweb.asm.Type.FLOAT   -> "java/lang/Float";
            case org.objectweb.asm.Type.DOUBLE  -> "java/lang/Double";
            default -> throw new IllegalArgumentException("Not a primitive: " + t);
        };
    }

    private static String unboxMethod(org.objectweb.asm.Type t) {
        return switch (t.getSort()) {
            case org.objectweb.asm.Type.BOOLEAN -> "booleanValue";
            case org.objectweb.asm.Type.BYTE    -> "byteValue";
            case org.objectweb.asm.Type.CHAR    -> "charValue";
            case org.objectweb.asm.Type.SHORT   -> "shortValue";
            case org.objectweb.asm.Type.INT     -> "intValue";
            case org.objectweb.asm.Type.LONG    -> "longValue";
            case org.objectweb.asm.Type.FLOAT   -> "floatValue";
            case org.objectweb.asm.Type.DOUBLE  -> "doubleValue";
            default -> throw new IllegalArgumentException("Not a primitive: " + t);
        };
    }

    private byte[] fixInterfaceSuperclasses(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        reader.accept(cn, 0);

        if (cn.superName == null || cn.superName.equals("java/lang/Object")) return bytes;
        if (!cn.name.startsWith("net/minecraft/") && !cn.name.startsWith("com/mojang/")) {
            return bytes;
        }

        boolean superIsInterface = false;
        try {
            Class<?> superCls = loadClass(cn.superName.replace('/', '.'), false);
            superIsInterface = superCls.isInterface();
        } catch (ClassNotFoundException | LinkageError ignored) {}

        if (!superIsInterface) return bytes;

        final String oldSuper = cn.superName;
        if (cn.interfaces == null) cn.interfaces = new java.util.ArrayList<>();
        cn.interfaces.add(oldSuper);
        cn.superName = "java/lang/Object";

        try {
            Class<?> iface = loadClass(oldSuper.replace('/', '.'), false);
            for (java.lang.reflect.Method ifaceMethod : iface.getMethods()) {
                String ifaceName = ifaceMethod.getName();
                String ifaceDesc = org.objectweb.asm.Type.getMethodDescriptor(ifaceMethod);
                boolean implemented = cn.methods.stream()
                    .anyMatch(mn -> mn.name.equals(ifaceName) && mn.desc.equals(ifaceDesc));
                if (implemented) continue;

                String mojmapName = dev.stolym.vyne.mappings.MappingRegistry.isReady()
                    ? dev.stolym.vyne.mappings.MappingRegistry.get()
                        .unmapMethod(oldSuper, ifaceName, ifaceDesc)
                    : ifaceName;

                org.objectweb.asm.tree.MethodNode existing = cn.methods.stream()
                    .filter(mn -> mn.name.equals(mojmapName) && mn.desc.equals(ifaceDesc))
                    .findFirst().orElse(null);

                org.objectweb.asm.Type stubRet = org.objectweb.asm.Type.getReturnType(ifaceDesc);
                org.objectweb.asm.Type[] stubArgs =
                    org.objectweb.asm.Type.getArgumentTypes(ifaceDesc);
                org.objectweb.asm.tree.MethodNode stub = new org.objectweb.asm.tree.MethodNode(
                    Opcodes.ACC_PUBLIC, ifaceName, ifaceDesc, null, null);

                if (existing != null) {
                    stub.instructions.add(new org.objectweb.asm.tree.VarInsnNode(
                        Opcodes.ALOAD, 0));
                    int slot = 1;
                    for (org.objectweb.asm.Type arg : stubArgs) {
                        stub.instructions.add(new org.objectweb.asm.tree.VarInsnNode(
                            arg.getOpcode(Opcodes.ILOAD), slot));
                        slot += arg.getSize();
                    }
                    stub.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL, cn.name, mojmapName, ifaceDesc, false));
                    stub.instructions.add(new org.objectweb.asm.tree.InsnNode(
                        stubRet.getOpcode(Opcodes.IRETURN)));
                } else {
                    String dispatchKey = cn.name.replace('/', '.') + "#" + ifaceName + ifaceDesc;
                    dev.stolym.vyne.vcl.VyneMethodDispatchTable.Dispatch preDispatch =
                        dev.stolym.vyne.vcl.VyneMethodDispatchTable.lookup(dispatchKey);
                    if (preDispatch == null)
                        preDispatch = dev.stolym.vyne.vcl.VyneMethodDispatchTable
                            .lookupBySignature(ifaceName, ifaceDesc);

                    if (preDispatch != null) {
                        emitDispatchCall(stub, dispatchKey, stubRet, stubArgs, false);
                    } else {
                        emitDefaultReturn(stub, stubRet);
                    }
                }
                cn.methods.add(stub);
            }
        } catch (Throwable t) {
            dev.stolym.vyne.util.VyneLogger.warn("[RCL-BRIDGE] Bridge gen failed for " + cn.name + ": " + t);
        }

        for (org.objectweb.asm.tree.MethodNode mn : cn.methods) {
            if (!mn.name.equals("<init>")) continue;
            org.objectweb.asm.tree.InsnList insns = mn.instructions;
            for (int i = 0; i < insns.size(); i++) {
                org.objectweb.asm.tree.AbstractInsnNode insn = insns.get(i);
                if (insn instanceof org.objectweb.asm.tree.MethodInsnNode min
                        && min.getOpcode() == Opcodes.INVOKESPECIAL
                        && min.owner.equals(oldSuper)
                        && min.name.equals("<init>")) {
                    org.objectweb.asm.tree.InsnList replacement =
                        new org.objectweb.asm.tree.InsnList();
                    int argCount =
                        org.objectweb.asm.Type.getArgumentTypes(min.desc).length;
                    for (int j = 0; j < argCount; j++)
                        replacement.add(new org.objectweb.asm.tree.InsnNode(Opcodes.POP));
                    replacement.add(new org.objectweb.asm.tree.MethodInsnNode(
                        Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
                    insns.insert(insn, replacement);
                    insns.remove(insn);
                    break;
                }
            }
        }

        ClassWriter writer = buildWriter();
        cn.accept(writer);
        return writer.toByteArray();
    }
}
