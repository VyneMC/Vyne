/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.stolym.vyne.patchlayer.FabricModels;
import net.minecraft.class_1087;
import net.minecraft.class_1100;
import net.minecraft.class_2960;
import net.minecraft.class_3665;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;

@Mixin(targets = "net.minecraft.class_1088$class_7778")
public abstract class PatchLayerModelBakeMixin {
    private static final String BAKE = "bake(Lnet/minecraft/class_2960;Lnet/minecraft/class_3665;Ljava/util/function/Function;)Lnet/minecraft/class_1087;";
    private static final String BAKE_UNCACHED = "Lnet/minecraft/class_1088$class_7778;bakeUncached(Lnet/minecraft/class_1100;Lnet/minecraft/class_3665;Ljava/util/function/Function;)Lnet/minecraft/class_1087;";

    @ModifyArg(method = BAKE, index = 0, at = @At(value = "INVOKE", target = BAKE_UNCACHED))
    private class_1100 vyne$beforeBake(class_1100 unbaked, @Local(argsOnly = true) class_2960 id,
                                       @Local(argsOnly = true) class_3665 state, @Local(argsOnly = true) Function<?, ?> sprites) {
        return (class_1100) FabricModels.beforeBake(this, id, unbaked, state, sprites);
    }

    @ModifyExpressionValue(method = BAKE, at = @At(value = "INVOKE", target = BAKE_UNCACHED))
    private class_1087 vyne$afterBake(class_1087 baked, @Local(argsOnly = true) class_2960 id,
                                      @Local(argsOnly = true) class_3665 state, @Local(argsOnly = true) Function<?, ?> sprites) {
        return (class_1087) FabricModels.afterBake(this, id, baked, state, sprites);
    }
}
