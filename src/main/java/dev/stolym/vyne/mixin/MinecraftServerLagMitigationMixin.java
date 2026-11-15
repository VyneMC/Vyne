/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.server.ServerLagMitigator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_3176")
public abstract class MinecraftServerLagMitigationMixin {
    @Inject(method = "method_3813", at = @At("HEAD"), remap = false)
    private void vyne$lagTickHead(CallbackInfo ci) {
        ServerLagMitigator.onServerTickStart();
    }

    @Inject(method = "method_3813", at = @At("RETURN"), remap = false)
    private void vyne$lagTickReturn(CallbackInfo ci) {
        ServerLagMitigator.onServerTickEnd(this);
    }
}
