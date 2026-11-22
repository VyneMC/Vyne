/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity;
import net.fabricmc.fabric.impl.blockview.client.RenderDataMapConsumer;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(RenderRegionCache.class)
public abstract class FabricRenderRegionHook {
    @Inject(method = "createRegion(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/SectionPos;Z)Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;",
            at = @At("RETURN"))
    private void vyne$renderData(Level level, SectionPos section, boolean nonEmpty, CallbackInfoReturnable<RenderChunkRegion> cir) {
        RenderChunkRegion region = cir.getReturnValue();
        if (region == null) return;
        int minX = (section.x() - 1) << 4, minY = (section.y() - 1) << 4, minZ = (section.z() - 1) << 4;
        int maxX = ((section.x() + 1) << 4) + 15, maxY = ((section.y() + 1) << 4) + 15, maxZ = ((section.z() + 1) << 4) + 15;
        Long2ObjectOpenHashMap<Object> data = null;
        for (int cx = section.x() - 1; cx <= section.x() + 1; cx++) {
            for (int cz = section.z() - 1; cz <= section.z() + 1; cz++) {
                for (Map.Entry<BlockPos, BlockEntity> entry : level.getChunk(cx, cz).getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (pos.getX() < minX || pos.getX() > maxX || pos.getY() < minY || pos.getY() > maxY || pos.getZ() < minZ || pos.getZ() > maxZ) continue;
                    Object renderData = ((RenderDataBlockEntity) entry.getValue()).getRenderData();
                    if (renderData == null) continue;
                    if (data == null) data = new Long2ObjectOpenHashMap<>();
                    data.put(pos.asLong(), renderData);
                }
            }
        }
        if (data != null) ((RenderDataMapConsumer) region).fabric_acceptRenderDataMap(data);
    }
}