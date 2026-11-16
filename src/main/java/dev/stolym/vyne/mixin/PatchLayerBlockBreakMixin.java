/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.patchlayer.FabricBlockBreak;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.class_3225")
public abstract class PatchLayerBlockBreakMixin {
    @Inject(method = "method_14266",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/class_2248;method_9576(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_1657;)Lnet/minecraft/class_2680;"),
            cancellable = true)
    private void vyne$beforeBlockBreak(class_2338 pos, CallbackInfoReturnable<Boolean> cir) {
        if (!FabricBlockBreak.before(this, pos)) cir.setReturnValue(false);
    }

    @Inject(method = "removeBlock", at = @At("HEAD"))
    private void vyne$rememberBlockEntity(class_2338 pos, class_2680 state, boolean canHarvest, CallbackInfoReturnable<Boolean> cir) {
        FabricBlockBreak.rememberBlockEntity(this, pos);
    }

    @Inject(method = "removeBlock", at = @At("RETURN"))
    private void vyne$afterBlockBreak(class_2338 pos, class_2680 state, boolean canHarvest, CallbackInfoReturnable<Boolean> cir) {
        FabricBlockBreak.after(this, pos, state, cir.getReturnValueZ());
    }
}
