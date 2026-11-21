/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stolym.vyne.patchlayer.FabricScreens;
import net.minecraft.class_332;
import net.minecraft.class_437;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.class_757")
public abstract class PatchLayerScreenRenderMixin {
    @WrapOperation(method = "method_3192", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;drawScreen(Lnet/minecraft/class_437;Lnet/minecraft/class_332;IIF)V"))
    private void vyne$renderScreen(class_437 screen, class_332 graphics, int mouseX, int mouseY, float delta, Operation<Void> original) {
        FabricScreens.fire("beforeRender", "beforeRender", screen, graphics, mouseX, mouseY, delta);
        original.call(screen, graphics, mouseX, mouseY, delta);
        FabricScreens.fire("afterRender", "afterRender", screen, graphics, mouseX, mouseY, delta);
    }
}
