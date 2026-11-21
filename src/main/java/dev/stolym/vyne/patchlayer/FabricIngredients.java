/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.lang.reflect.Method;

public final class FabricIngredients {
    private static final String CUSTOM = "net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient";

    private FabricIngredients() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Codec wrap(Codec neoForge, boolean allowEmpty) {
        Codec<Object> original = neoForge;
        return Codec.either(custom(allowEmpty).codec(), original).xmap(FabricIngredients::fromEither, FabricIngredients::toEither);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static MapCodec wrapMap(MapCodec neoForge) {
        MapCodec<Object> original = neoForge;
        return Codec.mapEither(custom(false), original).xmap(FabricIngredients::fromEither, FabricIngredients::toEither);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static MapCodec<Object> custom(boolean allowEmpty) {
        try {
            ClassLoader loader = FabricIngredients.class.getClassLoader();
            Codec<Object> serializers = (Codec) Class.forName("net.fabricmc.fabric.impl.recipe.ingredient.CustomIngredientImpl", true, loader)
                    .getField("CODEC").get(null);
            Method getSerializer = Class.forName(CUSTOM, true, loader).getMethod("getSerializer");
            Method getCodec = Class.forName("net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer", true, loader)
                    .getMethod("getCodec", boolean.class);
            return serializers.dispatchMap("fabric:type",
                    ingredient -> invoke(getSerializer, ingredient),
                    serializer -> (MapCodec<Object>) invoke(getCodec, serializer, allowEmpty));
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw failure(e);
        }
    }

    private static Object fromEither(Either<Object, Object> either) {
        return either.map(custom -> {
            try {
                return custom.getClass().getMethod("toVanilla").invoke(custom);
            } catch (ReflectiveOperationException e) {
                throw failure(e);
            }
        }, ingredient -> ingredient);
    }

    private static Either<Object, Object> toEither(Object ingredient) {
        Object custom = fabricCustom(ingredient);
        return custom != null ? Either.left(custom) : Either.right(ingredient);
    }

    private static Object fabricCustom(Object ingredient) {
        try {
            for (Method m : ingredient.getClass().getMethods()) {
                if (m.getName().equals("getCustomIngredient") && m.getParameterCount() == 0 && m.getReturnType().getName().equals(CUSTOM)) {
                    return m.invoke(ingredient);
                }
            }
            return null;
        } catch (ReflectiveOperationException e) {
            throw failure(e);
        }
    }

    private static Object invoke(Method method, Object owner, Object... args) {
        try {
            return method.invoke(owner, args);
        } catch (ReflectiveOperationException e) {
            throw failure(e);
        }
    }

    private static IllegalStateException failure(Throwable t) {
        return new IllegalStateException("Fabric custom ingredients are unavailable on Vyne's patch layer", t);
    }
}
