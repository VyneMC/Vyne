/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.world.RegistryHealthPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_3283")
public abstract class PackRepositoryVyneMixin {
    @Inject(method = "method_14445", at = @At("RETURN"), remap = false)
    private void vyne$stripUnhealthyModPacks(CallbackInfo ci) {
        RegistryHealthPolicy.stripSuppressedPacks(this);
    }
}
