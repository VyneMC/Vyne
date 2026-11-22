/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.fabricmc.fabric.impl.client.model.loading.ModelLoaderHooks;
import net.fabricmc.fabric.impl.client.model.loading.ModelLoadingEventDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;

@Mixin(ModelBakery.ModelBakerImpl.class)
public abstract class FabricModelBakeHook {
    private static final String BAKE = "bake(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/resources/model/ModelState;Ljava/util/function/Function;)Lnet/minecraft/client/resources/model/BakedModel;";
    private static final String BAKE_UNCACHED = "Lnet/minecraft/client/resources/model/ModelBakery$ModelBakerImpl;bakeUncached(Lnet/minecraft/client/resources/model/UnbakedModel;Lnet/minecraft/client/resources/model/ModelState;Ljava/util/function/Function;)Lnet/minecraft/client/resources/model/BakedModel;";

    @Shadow @Final private ModelBakery this$0;

    @ModifyArg(method = BAKE, index = 0, at = @At(value = "INVOKE", target = BAKE_UNCACHED))
    private UnbakedModel vyne$beforeBake(UnbakedModel unbaked, @Local(argsOnly = true) ResourceLocation id, @Local(argsOnly = true) ModelState state,
                                         @Local(argsOnly = true) Function<Material, TextureAtlasSprite> sprites, @Share("unbaked") LocalRef<UnbakedModel> modified) {
        ModelLoadingEventDispatcher dispatcher = ((ModelLoaderHooks) this$0).fabric_getDispatcher();
        UnbakedModel result = dispatcher == null ? unbaked : dispatcher.modifyModelBeforeBake(unbaked, id, null, sprites, state, (ModelBaker) this);
        modified.set(result);
        return result;
    }

    @ModifyExpressionValue(method = BAKE, at = @At(value = "INVOKE", target = BAKE_UNCACHED))
    private BakedModel vyne$afterBake(BakedModel baked, @Local(argsOnly = true) ResourceLocation id, @Local(argsOnly = true) ModelState state,
                                      @Local(argsOnly = true) Function<Material, TextureAtlasSprite> sprites, @Share("unbaked") LocalRef<UnbakedModel> unbaked) {
        ModelLoadingEventDispatcher dispatcher = ((ModelLoaderHooks) this$0).fabric_getDispatcher();
        return dispatcher == null ? baked : dispatcher.modifyModelAfterBake(baked, id, null, unbaked.get(), sprites, state, (ModelBaker) this);
    }
}