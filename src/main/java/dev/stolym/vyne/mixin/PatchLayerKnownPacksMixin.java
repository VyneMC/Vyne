/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.patchlayer.FabricResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

@Mixin(targets = "net.minecraft.class_8610")
public abstract class PatchLayerKnownPacksMixin {
    @ModifyArg(method = "runConfiguration", index = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/class_9223;<init>(Ljava/util/List;Lnet/minecraft/class_7780;)V"))
    private List<?> vyne$knownPacks(List<?> serverPacks) {
        return FabricResources.knownPacks(this, serverPacks);
    }
}
