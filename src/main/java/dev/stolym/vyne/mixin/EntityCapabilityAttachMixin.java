/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.compat.CapabilityBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_1297")
public abstract class EntityCapabilityAttachMixin {
    @Inject(
            method = "<init>(Lnet/minecraft/class_1299;Lnet/minecraft/class_1937;)V",
            at = @At("RETURN"))
    private void vyne$attachCapabilityProviders(CallbackInfo ci) {
        if (!dev.stolym.vyne.event.NeoForgeEvents.enabled()) return;
        try {
            CapabilityBridge.attachDefaultProviders(this);
        } catch (Throwable ignored) {
        }
    }
}
