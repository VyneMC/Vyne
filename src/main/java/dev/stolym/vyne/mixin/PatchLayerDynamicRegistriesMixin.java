/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.patchlayer.FabricDynamicRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

@Mixin(targets = "net.minecraft.class_7237")
public abstract class PatchLayerDynamicRegistriesMixin {
    @ModifyArg(method = "method_42098",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/class_7237;method_45143(Lnet/minecraft/class_3300;Lnet/minecraft/class_7780;Lnet/minecraft/class_7659;Ljava/util/List;)Lnet/minecraft/class_7780;"),
            index = 3)
    private static List<?> vyne$mergeDynamicRegistries(List<?> neoForge) {
        return FabricDynamicRegistries.merge(neoForge);
    }
}
