/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.util.VyneLogger;
import dev.stolym.vyne.vcl.VCLManager;
import dev.stolym.vyne.registry.CropBlockDatapackBootstrap;
import dev.stolym.vyne.registry.VyneRegistryInjector;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.class_7655")
public abstract class RegistryLoaderVyneSyncMixin {
    @Inject(method = "method_56515", at = @At("HEAD"))
    private static void vyne$syncBeforeLoadFromResource56515(CallbackInfoReturnable<Object> cir) {
        runPreRegistryLoaderSync("method_56515");
    }

    @Inject(method = "method_45122", at = @At("HEAD"))
    private static void vyne$syncBeforeLoadFromResource(CallbackInfo ci) {
        runPreRegistryLoaderSync("method_45122");
    }

    private static void runPreRegistryLoaderSync(String site) {
        if (Boolean.getBoolean("vyne.registry.skipPreLoaderSync")) {
            return;
        }
        ClassLoader knot = VCLManager.getCanonicalKnotLoader();
        if (knot == null) {
            knot = Thread.currentThread().getContextClassLoader();
        }
        try {
            CropBlockDatapackBootstrap.runBeforeRegistryLoader(knot);
            VyneRegistryInjector.invalidateCache();
            try {
                DeferredRegister.fillAllRegistryGapsIntoVyneGeneric();
            } catch (Throwable ignored) {
            }
            int snapBlocks = VyneRegistryInjector.snapshotLiveBuiltInIntoGeneric(knot, "minecraft:block");
            int snapItems = VyneRegistryInjector.snapshotLiveBuiltInIntoGeneric(knot, "minecraft:item");
            if (snapBlocks + snapItems > 0) {
                VyneLogger.info("[VyneRegistryInjector] Pre-RegistryLoader(" + site + ") live snapshot: "
                    + snapBlocks + " block(s), " + snapItems + " item(s) into VyneGenericRegistry");
            }
            int n = VyneRegistryInjector.syncAllTrackedGenericEntriesToLiveRegistry(knot);
            if (n > 0 && dev.stolym.vyne.util.VyneLogLevel.isVerbose()) {
                VyneLogger.debug("[VyneRegistryInjector] Pre-RegistryLoader(" + site + ") sync pushed "
                    + n + " entr(y/ies).");
            }
        } catch (Throwable t) {
            VyneLogger.warn("[VyneRegistryInjector] Pre-RegistryLoader(" + site + ") sync failed: "
                + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }
}
