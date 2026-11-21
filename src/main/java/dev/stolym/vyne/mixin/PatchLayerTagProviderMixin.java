/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.stolym.vyne.patchlayer.FabricResources;
import net.minecraft.class_3495;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.minecraft.class_2474")
public abstract class PatchLayerTagProviderMixin {
    @ModifyArg(method = "method_27046", index = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/class_7475;<init>(Ljava/util/List;ZLjava/util/List;)V"))
    private boolean vyne$replace(boolean replace, @Local class_3495 builder) {
        return replace || FabricResources.replacedByFabric(builder);
    }
}
