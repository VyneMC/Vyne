/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.fabric.impl.datagen.TagBuilderHooks;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.TagBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(TagsProvider.class)
public abstract class FabricTagProviderHook {
    @ModifyArg(method = "lambda$run$5", index = 1, at = @At(value = "INVOKE", target = "Lnet/minecraft/tags/TagFile;<init>(Ljava/util/List;ZLjava/util/List;)V"))
    private boolean vyne$replace(boolean replace, @Local TagBuilder builder) {
        return replace || ((TagBuilderHooks) builder).fabric_isReplaced();
    }
}
