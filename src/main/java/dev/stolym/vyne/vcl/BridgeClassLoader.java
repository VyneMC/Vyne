/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

@Deprecated
public final class BridgeClassLoader extends URLClassLoader {
    private static final Set<String> SHARED_PACKAGES = Set.of(
        "org.apache.commons.lang3.",
        "com.google.gson.",
        "com.google.common.",
        "com.google.guava.",
        "org.apache.logging.",
        "org.slf4j.",
        "io.netty.",
        "it.unimi.dsi.fastutil.",
        "com.ibm.icu.",
        "com.mojang.",

        "net.minecraft.",
        "dev.stolym.vyne.vcl.",
        "dev.stolym.vyne.registry.",
        "dev.stolym.vyne.util.",
        "net.neoforged.neoforge.registries."
    );

    public BridgeClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    public BridgeClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
        try {
            String cp = System.getProperty("java.class.path");
            if (cp != null) {
                for (String path : cp.split(java.io.File.pathSeparator)) {
                    if (path.isEmpty()) continue;
                    addURL(new java.io.File(path).toURI().toURL());
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    protected Class<?> loadClass(String requestedName, boolean resolve) throws ClassNotFoundException {
        final String name = normalizeSlashesInBinaryName(sanitizeBinaryClassRequest(requestedName));
        if (name.startsWith("net.neoforged.") || name.startsWith("net.minecraftforge.")) {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded != null) {
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
                try {
                    Class<?> c = findClass(name);
                    if (resolve) {
                        resolveClass(c);
                    }
                    return c;
                } catch (ClassNotFoundException ignored) {
                }
            }
            return super.loadClass(name, resolve);
        }
        if (isShared(name)) {
            if (needsFabricGameClassLoader(name)) {
                ClassLoader game = resolveFabricGameClassLoader();
                if (game != null) {
                    try {
                        Class<?> c = game.loadClass(name);
                        if (resolve) {
                            resolveClass(c);
                        }
                        return c;
                    } catch (ClassNotFoundException ignored) {
                    }
                    try {
                        Class<?> c = Class.forName(name, false, game);
                        if (resolve) {
                            resolveClass(c);
                        }
                        return c;
                    } catch (ClassNotFoundException ignored) {
                    }
                }
            }
            ClassLoader walk = getParent();
            while (walk != null) {
                try {
                    Class<?> c = walk.loadClass(name);
                    if (resolve) resolveClass(c);
                    return c;
                } catch (ClassNotFoundException ignored) {}
                walk = walk.getParent();
            }
            ClassLoader sys = ClassLoader.getSystemClassLoader();
            if (sys != null && sys != this) {
                try {
                    Class<?> c = sys.loadClass(name);
                    if (resolve) resolveClass(c);
                    return c;
                } catch (ClassNotFoundException ignored) {}
            }
        }
        return super.loadClass(name, resolve);
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

    private static String normalizeSlashesInBinaryName(String name) {
        if (name == null || name.isEmpty() || name.indexOf('/') < 0) {
            return name;
        }
        return name.replace('/', '.');
    }

    private static boolean needsFabricGameClassLoader(String name) {
        return name.startsWith("com.mojang.") || name.startsWith("net.minecraft.");
    }

    private static final ThreadLocal<Boolean> RESOLVING = ThreadLocal.withInitial(() -> false);
    private static volatile ClassLoader CACHED_FABRIC_GAME;

    private static ClassLoader resolveFabricGameClassLoader() {
        ClassLoader cached = CACHED_FABRIC_GAME;
        if (cached != null) {
            return cached;
        }
        if (RESOLVING.get()) {
            return null;
        }
        RESOLVING.set(true);
        try {
            try {
                ClassLoader ck = VCLManager.getCanonicalKnotLoader();
                if (ck != null && canLoadCodec(ck)) {
                    CACHED_FABRIC_GAME = ck;
                    return ck;
                }
            } catch (Throwable ignored) {
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
                        CACHED_FABRIC_GAME = cl;
                        return cl;
                    }
                } catch (Throwable ignored) {
                }
            }
            try {
                ClassLoader ck = VCLManager.getCanonicalKnotLoader();
                if (ck != null) {
                    CACHED_FABRIC_GAME = ck;
                    return ck;
                }
            } catch (Throwable ignored) {
            }
        } finally {
            RESOLVING.set(false);
        }
        try {
            ClassLoader ctx = Thread.currentThread().getContextClassLoader();
            ClassLoader walk = ctx;
            while (walk != null) {
                if (canLoadMinecraft(walk) || canLoadCodec(walk)) {
                    CACHED_FABRIC_GAME = walk;
                    return walk;
                }
                walk = walk.getParent();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean canLoadCodec(ClassLoader cl) {
        try {
            cl.loadClass("com.mojang.serialization.Codec");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean canLoadMinecraft(ClassLoader cl) {
        try {
            cl.loadClass("net.minecraft.class_2960");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isShared(String name) {
        for (String pkg : SHARED_PACKAGES) {
            if (name.startsWith(pkg)) return true;
        }
        return false;
    }
}
