/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.fabric;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.core.VyneLauncher;
import dev.stolym.vyne.registry.VyneRegistryGate;
import dev.stolym.vyne.registry.VyneRegistryInjector;
import dev.stolym.vyne.util.MixinExtrasCompat;
import dev.stolym.vyne.util.VyneLogger;
import dev.stolym.vyne.vcl.VCLManager;
import net.fabricmc.api.ModInitializer;

import java.util.List;

public final class VyneModLifecycle implements ModInitializer {
    @Override
    public void onInitialize() {
        ClassLoader selfCl = VyneModLifecycle.class.getClassLoader();
        VyneLogger.info("[Vyne] Official Entrypoint: Booting VyneModLifecycle. Loader: " + selfCl);

        if (dev.stolym.vyne.core.VyneMode.patchedGame()) {
            VyneLogger.info("[Vyne] NeoForge's own lifecycle runs NeoForge mods; Vyne's compatibility lifecycle is off.");
            MixinExtrasCompat.prepare(selfCl);
            return;
        }

        if (dev.stolym.vyne.loaders.RealNeoForgeApiMount.isEnabled()) {
            dev.stolym.vyne.loaders.RealNeoForgeApiMount.mount(VyneLauncher.getNeoForgeApiJar());
        }

        dev.stolym.vyne.loaders.KnotModClasspathBridge.addVyneJar();

        List<DetectedMod> pending = VyneLauncher.getPendingVCLMods();
        if (pending != null && !pending.isEmpty()) {
            VyneLogger.info("[Vyne] Ensuring " + pending.size() + " mods are on the Knot classpath...");
            dev.stolym.vyne.loaders.KnotModClasspathBridge.addRewriteJars(
                pending.stream().map(DetectedMod::getJarPath)
                    .map(dev.stolym.vyne.convert.ModConverter::runtimeJar).collect(java.util.stream.Collectors.toList())
            );
        }

        ClassLoader threadCl = Thread.currentThread().getContextClassLoader();

        ClassLoader knot = selfCl;
        try {
            Class<?> flb = Class.forName("net.fabricmc.loader.impl.launch.FabricLauncherBase");
            Object launcher = flb.getMethod("getLauncher").invoke(null);
            if (launcher != null) {
                knot = (ClassLoader) launcher.getClass().getMethod("getTargetClassLoader").invoke(launcher);
                VyneLogger.info("[Vyne] Discovered official Knot loader: " + knot);
            }
        } catch (Throwable t) {
            VyneLogger.warn("[Vyne] Could not resolve official Knot loader via FabricLauncherBase, falling back to self-loader.");
        }

        if (pending != null && !pending.isEmpty()) {
            VyneLogger.phase("VYNE EARLY INIT");
            VyneLogger.info("[Vyne] Bootstrapping VCL for " + pending.size() + " mod(s) to populate registries...");
            VCLManager.initializeLifecycle(knot, pending);
        }

        VyneLogger.info("[Vyne] Finalizing proactive registry sync...");
        VyneRegistryInjector.syncDatapackCriticalRegistries(knot);

        VyneLogger.info("[Vyne] Awaiting critical registry gate...");
        VyneRegistryGate.await();
        VyneLogger.info("[Vyne] Registry gate unlocked. Proceeding.");

        ClassLoader runtimeCl = Thread.currentThread().getContextClassLoader();
        MixinExtrasCompat.prepare(runtimeCl);
        if (selfCl != runtimeCl) {
            MixinExtrasCompat.prepare(selfCl);
        }

        try { 
            dev.stolym.vyne.resources.VyneModPackInjector.install(); 
        } catch (Throwable t) {
            System.out.println("[VynePacks] Injector threw during setup: " + t);
        }
    }
}
