/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stolym.vyne.patchlayer.FabricScreens;
import net.minecraft.class_437;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.class_310")
public abstract class PatchLayerScreenTickMixin {
    @WrapOperation(method = "/^lambda\\$tick\\$\\d+$/", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_437;method_25393()V"))
    private void vyne$screenTick(class_437 screen, Operation<Void> original) {
        FabricScreens.fire("beforeTick", "beforeTick", screen);
        original.call(screen);
        FabricScreens.fire("afterTick", "afterTick", screen);
    }
}
