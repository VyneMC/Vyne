/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.stolym.vyne.patchlayer.FabricIndigo;
import net.minecraft.class_1920;
import net.minecraft.class_1921;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_287;
import net.minecraft.class_4076;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import net.minecraft.class_5819;
import net.minecraft.class_750;
import net.minecraft.class_776;
import net.minecraft.class_8251;
import net.minecraft.class_853;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(targets = "net.minecraft.class_9810")
public abstract class PatchLayerSectionCompilerMixin {
    @Shadow
    abstract class_287 method_60903(Map<class_1921, class_287> layers, class_750 buffers, class_1921 layer);

    @Inject(method = "compile", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/class_2338;method_10097(Lnet/minecraft/class_2338;Lnet/minecraft/class_2338;)Ljava/lang/Iterable;"))
    private void vyne$prepareIndigo(class_4076 section, class_853 region, class_8251 sorting, class_750 buffers, List<?> extra,
                                    CallbackInfoReturnable<?> cir, @Local Map<class_1921, class_287> layers) {
        FabricIndigo.prepare(region, layer -> method_60903(layers, buffers, (class_1921) layer));
    }

    @WrapOperation(method = "compile", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/class_776;renderBatched(Lnet/minecraft/class_2680;Lnet/minecraft/class_2338;Lnet/minecraft/class_1920;Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;ZLnet/minecraft/class_5819;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/class_1921;)V"))
    private void vyne$renderBlock(class_776 dispatcher, class_2680 state, class_2338 pos, class_1920 level, class_4587 pose, class_4588 consumer,
                                  boolean checkSides, class_5819 random, ModelData data, class_1921 layer, Operation<Void> original,
                                  @Local(argsOnly = true) class_853 region) {
        if (!FabricIndigo.render(region, dispatcher, state, pos, pose)) {
            original.call(dispatcher, state, pos, level, pose, consumer, checkSides, random, data, layer);
        }
    }

    @Inject(method = "compile", at = @At("RETURN"))
    private void vyne$releaseIndigo(class_4076 section, class_853 region, class_8251 sorting, class_750 buffers, List<?> extra, CallbackInfoReturnable<?> cir) {
        FabricIndigo.release(region);
    }
}
