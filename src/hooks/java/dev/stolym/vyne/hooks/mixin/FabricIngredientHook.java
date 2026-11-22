/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.stolym.vyne.hooks.fabric.FabricIngredientHooks;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Ingredient.class)
public abstract class FabricIngredientHook {
    private static final String CODEC = "Lnet/neoforged/neoforge/common/crafting/CraftingHelper;makeIngredientCodec(Z)Lcom/mojang/serialization/Codec;";

    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", ordinal = 0, target = CODEC))
    private static Codec<Ingredient> vyne$codec(Codec<Ingredient> neoForge) {
        return FabricIngredientHooks.wrap(neoForge, true);
    }

    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", ordinal = 1, target = CODEC))
    private static Codec<Ingredient> vyne$codecNonEmpty(Codec<Ingredient> neoForge) {
        return FabricIngredientHooks.wrap(neoForge, false);
    }

    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/common/crafting/CraftingHelper;makeIngredientMapCodec()Lcom/mojang/serialization/MapCodec;"))
    private static MapCodec<Ingredient> vyne$mapCodec(MapCodec<Ingredient> neoForge) {
        return FabricIngredientHooks.wrapMap(neoForge);
    }
}
