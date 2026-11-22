/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.impl.client.rendering.fluid.FluidRenderHandlerInfo;
import net.fabricmc.fabric.impl.client.rendering.fluid.FluidRenderingImpl;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LiquidBlockRenderer.class)
public abstract class FabricFluidRenderHook {
    @ModifyExpressionValue(method = "tesselate", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/textures/FluidSpriteCache;getFluidSprites(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;)[Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
    private TextureAtlasSprite[] vyne$sprites(TextureAtlasSprite[] neoForge) {
        FluidRenderHandlerInfo info = FluidRenderingImpl.getCurrentInfo();
        return info == null || info.handler == null ? neoForge : info.sprites;
    }

    @WrapOperation(method = "tesselate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;shouldDisplayFluidOverlay(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;)Z"))
    private boolean vyne$overlay(BlockState neighbor, BlockAndTintGetter level, BlockPos pos, FluidState fluid, Operation<Boolean> original) {
        return original.call(neighbor, level, pos, fluid) || FluidRenderHandlerRegistry.INSTANCE.isBlockTransparent(neighbor.getBlock());
    }
}