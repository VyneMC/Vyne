/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.stolym.vyne.patchlayer.FabricNetworking;
import net.minecraft.class_9139;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.class_2658")
public abstract class PatchLayerClientboundPayloadMixin {
    private static final String NEOFORGE_CODEC = "Lnet/minecraft/class_8710;codec(Lnet/minecraft/class_8710$class_9153;Ljava/util/List;Lnet/minecraft/class_2539;Lnet/minecraft/class_2598;)Lnet/minecraft/class_9139;";

    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", ordinal = 0, target = NEOFORGE_CODEC))
    private static class_9139 vyne$playPayloads(class_9139 codec) {
        return FabricNetworking.withFabricPayloads(codec, "PLAY_S2C");
    }

    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", ordinal = 1, target = NEOFORGE_CODEC))
    private static class_9139 vyne$configurationPayloads(class_9139 codec) {
        return FabricNetworking.withFabricPayloads(codec, "CONFIGURATION_S2C");
    }
}
