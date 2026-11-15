/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.util.VyneLogger;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class VyneMixinIntegration {
    private static volatile Object mixinTransformer;
    private static volatile boolean resolved = false;

    private VyneMixinIntegration() {}

    public static void initialize(ClassLoader knotCl) {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> svc = knotCl.loadClass(
                "net.fabricmc.loader.impl.launch.knot.MixinServiceKnot");
            Method getTransformer = svc.getDeclaredMethod("getTransformer");
            getTransformer.setAccessible(true);
            mixinTransformer = getTransformer.invoke(null);
            if (mixinTransformer != null) {
                VyneLogger.info("[VyneMixin] IMixinTransformer acquired from Knot.");
            } else {
                VyneLogger.warn("[VyneMixin] IMixinTransformer is null — mixin processing disabled.");
            }
        } catch (Throwable t) {
            VyneLogger.warn("[VyneMixin] Could not acquire IMixinTransformer: " + t.getMessage());
        }
    }

    public static byte[] transform(String internalName, String className, byte[] bytes,
                                    ClassLoader knotCl) {
        if (mixinTransformer == null) {
            initialize(knotCl);
            if (mixinTransformer == null) return bytes;
        }
        try {
            Class<?> ti = knotCl.loadClass(
                "org.spongepowered.asm.mixin.transformer.IMixinTransformer");
            Method m = ti.getMethod("transformClassBytes",
                String.class, String.class, byte[].class);
            m.setAccessible(true);
            byte[] result = (byte[]) m.invoke(mixinTransformer, internalName, className, bytes);
            return result != null ? result : bytes;
        } catch (Throwable t) {
            VyneLogger.debug("[VyneMixin] transform failed for " + className + ": " + t);
            return bytes;
        }
    }

    public static void registerMixinConfig(String mixinJsonPath, ClassLoader knotCl) {
        try {
            Class<?> mixinEnv = knotCl.loadClass(
                "org.spongepowered.asm.mixin.MixinEnvironment");
            Class<?> mixinBootstrap = knotCl.loadClass(
                "org.spongepowered.asm.mixin.Mixins");
            Method addConfig = mixinBootstrap.getMethod("addConfiguration", String.class);
            addConfig.invoke(null, mixinJsonPath);
            VyneLogger.info("[VyneMixin] Registered mixin config: " + mixinJsonPath);
        } catch (Throwable t) {
            VyneLogger.warn("[VyneMixin] Failed to register mixin config '"
                + mixinJsonPath + "': " + t.getMessage());
        }
    }

    public static void applyMixinsFromJar(java.nio.file.Path jarPath, ClassLoader knotCl) {
        List<String> mixinConfigs = discoverMixinConfigs(jarPath);
        for (String config : mixinConfigs) {
            registerMixinConfig(config, knotCl);
        }
        if (!mixinConfigs.isEmpty()) {
            triggerMixinPhase(knotCl);
        }
    }

    private static void collectMixinConfigsFromToml(String content, List<String> configs) {
        if (content == null || content.isEmpty()) return;
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?im)config\\s*=\\s*\"([^\"]+)\"")
            .matcher(content);
        while (m.find()) {
            String name = m.group(1).trim();
            if (name.endsWith(".mixins.json") || "mixins.json".equals(name)) {
                if (!configs.contains(name)) {
                    configs.add(name);
                }
            }
        }
    }

    private static List<String> discoverMixinConfigs(java.nio.file.Path jarPath) {
        List<String> configs = new ArrayList<>();
        try (ZipFile zf = new ZipFile(jarPath.toFile())) {
            java.util.Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".mixins.json") || name.equals("mixins.json")) {
                    configs.add(name);
                }
            }

            ZipEntry modToml = zf.getEntry("META-INF/mods.toml");
            if (modToml != null) {
                try (InputStream is = zf.getInputStream(modToml)) {
                    collectMixinConfigsFromToml(new String(is.readAllBytes()), configs);
                }
            }

            ZipEntry forgeToml = zf.getEntry("META-INF/neoforge.mods.toml");
            if (forgeToml != null) {
                try (InputStream is = zf.getInputStream(forgeToml)) {
                    collectMixinConfigsFromToml(new String(is.readAllBytes()), configs);
                }
            }
        } catch (Throwable t) {
            VyneLogger.debug("[VyneMixin] discoverMixinConfigs failed for "
                + jarPath + ": " + t);
        }
        return configs;
    }

    private static void triggerMixinPhase(ClassLoader knotCl) {
        try {
            Class<?> mixinEnvClass = knotCl.loadClass(
                "org.spongepowered.asm.mixin.MixinEnvironment");
            Method getCurrentEnv = mixinEnvClass.getMethod("getCurrentEnvironment");
            Object env = getCurrentEnv.invoke(null);
            if (env == null) return;

            Method getPhase = env.getClass().getMethod("getPhase");
            Object phase = getPhase.invoke(env);
            VyneLogger.debug("[VyneMixin] Current mixin phase: " + phase);
        } catch (Throwable ignored) {}
    }

    public static boolean hasMixins(java.nio.file.Path jarPath) {
        return !discoverMixinConfigs(jarPath).isEmpty();
    }
}
