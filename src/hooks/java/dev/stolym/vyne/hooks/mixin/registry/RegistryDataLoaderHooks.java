/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin.registry;

import net.minecraft.resources.RegistryDataLoader;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(RegistryDataLoader.class)
public abstract class RegistryDataLoaderHooks {
    @Shadow @Final @Mutable public static List<RegistryDataLoader.RegistryData<?>> SYNCHRONIZED_REGISTRIES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void vyne$keepNetworkableRegistries(CallbackInfo ci) {
        SYNCHRONIZED_REGISTRIES = DataPackRegistriesHooks.grabNetworkableRegistries(SYNCHRONIZED_REGISTRIES);
    }
}
