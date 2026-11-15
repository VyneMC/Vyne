/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.loaders;

import dev.stolym.vyne.util.VyneLogger;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KnotModClasspathBridge {
    private static final AtomicBoolean NEO_FORGE_REGISTRIES_BOOTSTRAP_INSTALLED = new AtomicBoolean(false);
    private static final AtomicBoolean VYNE_JAR_REGISTERED = new AtomicBoolean(false);

    private KnotModClasspathBridge() {}

    public static void markVyneJarRegistered() {
        VYNE_JAR_REGISTERED.set(true);
    }

    public static void addVyneJar() {
        if (!VYNE_JAR_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        try {
            Path vyneJar = dev.stolym.vyne.convert.VyneGameJar.prepare();
            if (Files.isRegularFile(vyneJar)) {
                addRewriteJars(List.of(vyneJar));
            } else {
                VYNE_JAR_REGISTERED.set(false);
            }
        } catch (Throwable t) {
            VYNE_JAR_REGISTERED.set(false);
            VyneLogger.warn("[KnotClasspath] Could not register Vyne jar on Knot: " + t);
        }
    }

    public static void installEmbeddedNeoForgeRegistriesBootstrap(Class<?> anchor) {
        if (anchor == null) {
            return;
        }
        if (!NEO_FORGE_REGISTRIES_BOOTSTRAP_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        String resource = "META-INF/vyne/bootstrap/vyne-neoforge-registries-bootstrap.jar";
        ClassLoader cl = anchor.getClassLoader();
        if (cl == null) {
            NEO_FORGE_REGISTRIES_BOOTSTRAP_INSTALLED.set(false);
            return;
        }
        try (InputStream in = cl.getResourceAsStream(resource)) {
            if (in == null) {
                NEO_FORGE_REGISTRIES_BOOTSTRAP_INSTALLED.set(false);
                VyneLogger.debug("[KnotClasspath] No embedded " + resource + " (IDE split classpath?)");
                return;
            }
            Path tmp = Files.createTempFile("vyne-neo-reg-bootstrap", ".jar");
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            addRewriteJars(List.of(tmp));
            VyneLogger.info("[KnotClasspath] NeoForgeRegistries bootstrap JAR registered on Knot.");
        } catch (Throwable t) {
            NEO_FORGE_REGISTRIES_BOOTSTRAP_INSTALLED.set(false);
            VyneLogger.warn("[KnotClasspath] NeoForgeRegistries bootstrap install failed: "
                + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static void addRewriteJars(Iterable<Path> paths) {
        if (paths == null) return;
        try {
            Class<?> flb = Class.forName("net.fabricmc.loader.impl.launch.FabricLauncherBase");
            Object launcher = flb.getMethod("getLauncher").invoke(null);
            if (launcher == null) return;
            Method add = launcher.getClass().getMethod(
                "addToClassPath", Path.class, String[].class);
            for (Path p : paths) {
                if (p == null || !Files.isRegularFile(p)) continue;
                Path abs = p.toAbsolutePath().normalize();
                add.invoke(launcher, abs, new String[0]);
                VyneLogger.info("[KnotClasspath] Registered mod JAR on Knot: " + abs.getFileName() + " (Path: " + abs + ")");
            }
        } catch (Throwable t) {
            VyneLogger.error("[KnotClasspath] Failed to register rewrite jars on Knot: "
                + t.getClass().getSimpleName() + ": " + t.getMessage(), t);
        }
    }
}
