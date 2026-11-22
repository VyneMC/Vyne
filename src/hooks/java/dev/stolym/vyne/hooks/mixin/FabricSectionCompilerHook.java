/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.impl.client.indigo.renderer.accessor.AccessChunkRendererRegion;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.TerrainRenderContext;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(SectionCompiler.class)
public abstract class FabricSectionCompilerHook {
    private static final String COMPILE = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;";

    @Shadow
    abstract BufferBuilder getOrBeginLayer(Map<RenderType, BufferBuilder> layers, SectionBufferBuilderPack buffers, RenderType layer);

    @Inject(method = COMPILE, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/core/BlockPos;betweenClosed(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/lang/Iterable;"))
    private void vyne$prepareIndigo(CallbackInfoReturnable<?> cir, @Local(argsOnly = true) RenderChunkRegion region,
                                    @Local(argsOnly = true) SectionBufferBuilderPack buffers, @Local Map<RenderType, BufferBuilder> layers,
                                    @Share("lastBlock") LocalLongRef lastBlock) {
        TerrainRenderContext context = TerrainRenderContext.POOL.get();
        context.prepare(region, layer -> getOrBeginLayer(layers, buffers, layer));
        ((AccessChunkRendererRegion) region).fabric_setRenderer(context);
        lastBlock.set(Long.MIN_VALUE);
    }

    @WrapOperation(method = COMPILE, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V"))
    private void vyne$renderBlock(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack pose, VertexConsumer consumer,
                                  boolean checkSides, RandomSource random, ModelData data, RenderType layer, Operation<Void> original,
                                  @Local(argsOnly = true) RenderChunkRegion region, @Share("lastBlock") LocalLongRef lastBlock) {
        BakedModel model = dispatcher.getBlockModel(state);
        TerrainRenderContext context = ((AccessChunkRendererRegion) region).fabric_getRenderer();
        if (((FabricBakedModel) model).isVanillaAdapter() || context == null) {
            original.call(dispatcher, state, pos, level, pose, consumer, checkSides, random, data, layer);
            return;
        }
        long key = pos.asLong();
        if (lastBlock.get() == key) return;
        lastBlock.set(key);
        context.tessellateBlock(state, pos, model, pose);
    }

    @Inject(method = COMPILE, at = @At("RETURN"))
    private void vyne$releaseIndigo(CallbackInfoReturnable<?> cir, @Local(argsOnly = true) RenderChunkRegion region) {
        TerrainRenderContext context = ((AccessChunkRendererRegion) region).fabric_getRenderer();
        if (context == null) return;
        context.release();
        ((AccessChunkRendererRegion) region).fabric_setRenderer(null);
    }
}