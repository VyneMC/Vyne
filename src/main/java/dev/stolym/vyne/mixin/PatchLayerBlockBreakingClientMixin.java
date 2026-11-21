/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stolym.vyne.patchlayer.FabricClient;
import net.minecraft.class_1799;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.class_636")
public abstract class PatchLayerBlockBreakingClientMixin {
    @WrapOperation(method = "method_2922", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1799;shouldCauseBlockBreakReset(Lnet/minecraft/class_1799;)Z"))
    private boolean vyne$blockBreakReset(class_1799 oldStack, class_1799 newStack, Operation<Boolean> original) {
        return FabricClient.blockBreakReset(this, oldStack, newStack, original.call(oldStack, newStack));
    }
}
