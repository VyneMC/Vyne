/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import net.minecraft.class_2680;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.class_2591")
public abstract class BlockEntitySoftValidateMixin {
    @Inject(
            method = "method_20526(Lnet/minecraft/class_2680;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void vyne$permissiveIsValid(class_2680 state, CallbackInfoReturnable<Boolean> cir) {
        try {
            cir.setReturnValue(Boolean.TRUE);
        } catch (Throwable ignored) {
        }
    }
}
