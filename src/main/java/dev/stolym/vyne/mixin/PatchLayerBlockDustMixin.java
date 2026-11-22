/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.stolym.vyne.patchlayer.FabricInteraction;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_638;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.class_727")
public abstract class PatchLayerBlockDustMixin {
    @ModifyExpressionValue(method = "<init>(Lnet/minecraft/class_638;DDDDDDLnet/minecraft/class_2680;Lnet/minecraft/class_2338;)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/extensions/common/IClientBlockExtensions;areBreakingParticlesTinted(Lnet/minecraft/class_2680;Lnet/minecraft/class_638;Lnet/minecraft/class_2338;)Z"))
    private boolean vyne$fabricTint(boolean tinted, @Local(argsOnly = true) class_2680 state, @Local(argsOnly = true) class_638 level,
                                     @Local(argsOnly = true) class_2338 pos) {
        return tinted && FabricInteraction.allowBlockDustTint(state, level, pos);
    }
}
