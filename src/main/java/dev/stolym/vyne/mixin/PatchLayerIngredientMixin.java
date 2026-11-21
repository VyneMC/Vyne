/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.stolym.vyne.patchlayer.FabricIngredients;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.class_1856")
public abstract class PatchLayerIngredientMixin {
    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/neoforged/neoforge/common/crafting/CraftingHelper;makeIngredientCodec(Z)Lcom/mojang/serialization/Codec;"))
    private static Codec<?> vyne$codec(Codec<?> neoForge) {
        return FabricIngredients.wrap(neoForge, true);
    }

    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/neoforged/neoforge/common/crafting/CraftingHelper;makeIngredientCodec(Z)Lcom/mojang/serialization/Codec;"))
    private static Codec<?> vyne$codecNonEmpty(Codec<?> neoForge) {
        return FabricIngredients.wrap(neoForge, false);
    }

    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/common/crafting/CraftingHelper;makeIngredientMapCodec()Lcom/mojang/serialization/MapCodec;"))
    private static MapCodec<?> vyne$mapCodec(MapCodec<?> neoForge) {
        return FabricIngredients.wrapMap(neoForge);
    }
}
