/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.fabric;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.fabricmc.fabric.api.recipe.v1.ingredient.FabricIngredient;
import net.fabricmc.fabric.impl.recipe.ingredient.CustomIngredientImpl;
import net.minecraft.world.item.crafting.Ingredient;

public final class FabricIngredientHooks {
    private FabricIngredientHooks() {}

    public static Codec<Ingredient> wrap(Codec<Ingredient> neoForge, boolean allowEmpty) {
        return Codec.either(custom(allowEmpty).codec(), neoForge).xmap(FabricIngredientHooks::fromEither, FabricIngredientHooks::toEither);
    }

    public static MapCodec<Ingredient> wrapMap(MapCodec<Ingredient> neoForge) {
        return Codec.mapEither(custom(false), neoForge).xmap(FabricIngredientHooks::fromEither, FabricIngredientHooks::toEither);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static MapCodec<CustomIngredient> custom(boolean allowEmpty) {
        return CustomIngredientImpl.CODEC.dispatchMap("fabric:type", CustomIngredient::getSerializer,
                serializer -> (MapCodec) ((CustomIngredientSerializer) serializer).getCodec(allowEmpty));
    }

    private static Ingredient fromEither(Either<CustomIngredient, Ingredient> either) {
        return either.map(CustomIngredient::toVanilla, ingredient -> ingredient);
    }

    private static Either<CustomIngredient, Ingredient> toEither(Ingredient ingredient) {
        CustomIngredient custom = ((FabricIngredient) (Object) ingredient).getCustomIngredient();
        return custom != null ? Either.left(custom) : Either.right(ingredient);
    }
}
