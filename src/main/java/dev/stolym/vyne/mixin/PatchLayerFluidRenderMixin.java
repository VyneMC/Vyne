/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stolym.vyne.patchlayer.FabricClient;
import net.minecraft.class_1058;
import net.minecraft.class_1920;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_3610;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.class_775")
public abstract class PatchLayerFluidRenderMixin {
    @ModifyExpressionValue(method = "method_3347", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/textures/FluidSpriteCache;getFluidSprites(Lnet/minecraft/class_1920;Lnet/minecraft/class_2338;Lnet/minecraft/class_3610;)[Lnet/minecraft/class_1058;"))
    private class_1058[] vyne$sprites(class_1058[] neoForge) {
        return (class_1058[]) FabricClient.fluidSprites(neoForge);
    }

    @WrapOperation(method = "method_3347", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/class_2680;shouldDisplayFluidOverlay(Lnet/minecraft/class_1920;Lnet/minecraft/class_2338;Lnet/minecraft/class_3610;)Z"))
    private boolean vyne$overlay(class_2680 neighbor, class_1920 level, class_2338 pos, class_3610 fluid, Operation<Boolean> original) {
        return FabricClient.fluidOverlay(original.call(neighbor, level, pos, fluid), neighbor);
    }
}
