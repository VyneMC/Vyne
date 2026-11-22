/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class FabricBlockBreakHook {
    @Shadow
    protected ServerLevel level;

    @Shadow
    @Final
    protected ServerPlayer player;

    @Unique
    private BlockEntity vyne$brokenBlockEntity;

    @Inject(method = "destroyBlock", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;playerWillDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private void vyne$beforeBlockBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, blockEntity)) return;
        PlayerBlockBreakEvents.CANCELED.invoker().onBlockBreakCanceled(level, player, pos, state, blockEntity);
        player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        if (blockEntity != null) {
            Packet<?> update = blockEntity.getUpdatePacket();
            if (update != null) player.connection.send(update);
        }
        cir.setReturnValue(false);
    }

    @Inject(method = "removeBlock", at = @At("HEAD"))
    private void vyne$rememberBlockEntity(BlockPos pos, BlockState state, boolean canHarvest, CallbackInfoReturnable<Boolean> cir) {
        vyne$brokenBlockEntity = level.getBlockEntity(pos);
    }

    @Inject(method = "removeBlock", at = @At("RETURN"))
    private void vyne$afterBlockBreak(BlockPos pos, BlockState state, boolean canHarvest, CallbackInfoReturnable<Boolean> cir) {
        BlockEntity blockEntity = vyne$brokenBlockEntity;
        vyne$brokenBlockEntity = null;
        if (cir.getReturnValueZ()) PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, blockEntity);
    }
}
