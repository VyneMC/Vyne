/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.stolym.vyne.hooks.fabric.FabricItemHooks;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RegistryDataLoader.class)
public abstract class FabricEnchantmentLoadHook {
    @Inject(method = "loadElementFromResource", at = @At("HEAD"))
    private static void vyne$rememberResource(CallbackInfo ci, @Local(argsOnly = true) Resource resource) {
        FabricItemHooks.loadingFrom(resource);
    }

    @Inject(method = "loadElementFromResource", at = @At("RETURN"))
    private static void vyne$forgetResource(CallbackInfo ci) {
        FabricItemHooks.loadingFrom(null);
    }

    @ModifyArg(method = "lambda:loadElementFromResource", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/WritableRegistry;register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;"))
    private static Object vyne$modifyEnchantment(Object value, @Local(argsOnly = true) ResourceKey<?> key) {
        return FabricItemHooks.modifyEnchantment(key, value);
    }
}
