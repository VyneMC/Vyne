/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.stolym.vyne.patchlayer.FabricItems;
import net.minecraft.class_3298;
import net.minecraft.class_5321;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_7655")
public abstract class PatchLayerRegistryLoaderMixin {
    @Inject(method = "method_56915", at = @At("HEAD"))
    private static void vyne$rememberResource(CallbackInfo ci, @Local(argsOnly = true) class_3298 resource) {
        FabricItems.loadingFrom(resource);
    }

    @Inject(method = "method_56915", at = @At("RETURN"))
    private static void vyne$forgetResource(CallbackInfo ci) {
        FabricItems.loadingFrom(null);
    }

    @ModifyArg(method = "/^lambda\\$loadElementFromResource\\$\\d+$/",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/class_2385;method_10272(Lnet/minecraft/class_5321;Ljava/lang/Object;Lnet/minecraft/class_9248;)Lnet/minecraft/class_6880$class_6883;"),
            index = 1)
    private static Object vyne$modifyEnchantment(Object value, @Local(argsOnly = true) class_5321<?> key) {
        return FabricItems.modifyEnchantment(key, value);
    }
}
