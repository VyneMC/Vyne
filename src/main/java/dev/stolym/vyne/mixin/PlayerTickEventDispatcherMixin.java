/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.event.VyneGameplayDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_1657")
public abstract class PlayerTickEventDispatcherMixin {
    @Inject(method = "method_5773", at = @At("HEAD"), require = 0)
    private void vyne$playerTickPre(CallbackInfo ci) {
        VyneGameplayDispatcher.firePlayerTickPre(this);
    }

    @Inject(method = "method_5773", at = @At("RETURN"), require = 0)
    private void vyne$playerTickPost(CallbackInfo ci) {
        VyneGameplayDispatcher.firePlayerTickPost(this);
    }
}
