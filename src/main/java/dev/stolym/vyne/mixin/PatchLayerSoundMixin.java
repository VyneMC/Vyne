/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stolym.vyne.patchlayer.FabricClient;
import net.minecraft.class_1111;
import net.minecraft.class_1113;
import net.minecraft.class_4237;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;

@Mixin(targets = "net.minecraft.class_1140")
public abstract class PatchLayerSoundMixin {
    @WrapOperation(method = "method_4854", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/class_1113;getStream(Lnet/minecraft/class_4237;Lnet/minecraft/class_1111;Z)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<?> vyne$stream(class_1113 instance, class_4237 buffers, class_1111 sound, boolean looping, Operation<CompletableFuture<?>> original) {
        return FabricClient.soundStream(instance, buffers, sound, looping, () -> original.call(instance, buffers, sound, looping));
    }
}
