/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.stolym.vyne.patchlayer.FabricModels;
import net.minecraft.class_1087;
import net.minecraft.class_1088;
import net.minecraft.class_1100;
import net.minecraft.class_2960;
import net.minecraft.class_3665;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;

@Mixin(targets = "net.minecraft.class_1088$class_7778")
public abstract class PatchLayerModelBakeMixin {
    @WrapOperation(method = "bake(Lnet/minecraft/class_2960;Lnet/minecraft/class_3665;Ljava/util/function/Function;)Lnet/minecraft/class_1087;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1088$class_7778;bakeUncached(Lnet/minecraft/class_1100;Lnet/minecraft/class_3665;Ljava/util/function/Function;)Lnet/minecraft/class_1087;"))
    private class_1087 vyne$modelModifiers(class_1088.class_7778 baker, class_1100 unbaked, class_3665 state, Function<?, ?> sprites,
                                           Operation<class_1087> original, @Local(argsOnly = true) class_2960 id) {
        return (class_1087) FabricModels.bake(baker, id, unbaked, state, sprites,
                model -> original.call(baker, (class_1100) model, state, sprites));
    }
}
