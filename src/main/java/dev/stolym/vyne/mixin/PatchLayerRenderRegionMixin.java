/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.patchlayer.FabricRenderData;
import net.minecraft.class_1937;
import net.minecraft.class_4076;
import net.minecraft.class_853;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.class_6850")
public abstract class PatchLayerRenderRegionMixin {
    @Inject(method = "createRegion", at = @At("RETURN"))
    private void vyne$renderData(class_1937 level, class_4076 section, boolean nonEmpty, CallbackInfoReturnable<class_853> cir) {
        FabricRenderData.attach(cir.getReturnValue(), level, section);
    }
}
