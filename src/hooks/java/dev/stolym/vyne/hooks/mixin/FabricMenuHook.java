/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.FabricScreenHandlerFactory;
import net.fabricmc.fabric.impl.screenhandler.Networking;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public abstract class FabricMenuHook {
    @WrapOperation(method = "openMenu(Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)Ljava/util/OptionalInt;", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/MenuProvider;shouldTriggerClientSideContainerClosingOnOpen()Z"))
    private boolean vyne$closeCurrentScreen(MenuProvider provider, Operation<Boolean> original) {
        return original.call(provider) && ((FabricScreenHandlerFactory) provider).shouldCloseCurrentScreen();
    }

    @WrapOperation(method = "openMenu(Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)Ljava/util/OptionalInt;", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void vyne$openPacket(ServerGamePacketListenerImpl connection, Packet<?> packet, Operation<Void> original,
                                 @Local(argsOnly = true) MenuProvider provider, @Local AbstractContainerMenu menu) {
        if (!vyne$openedByFabric(provider, menu)) original.call(connection, packet);
    }

    @WrapOperation(method = "openMenu(Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)Ljava/util/OptionalInt;", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"))
    private void vyne$openPayload(ServerGamePacketListenerImpl connection, CustomPacketPayload payload, Operation<Void> original,
                                  @Local(argsOnly = true) MenuProvider provider, @Local AbstractContainerMenu menu) {
        if (!vyne$openedByFabric(provider, menu)) original.call(connection, payload);
    }

    private boolean vyne$openedByFabric(MenuProvider provider, AbstractContainerMenu menu) {
        if (!(provider instanceof ExtendedScreenHandlerFactory<?> factory)) return false;
        Networking.sendOpenPacket((ServerPlayer) (Object) this, factory, menu, menu.containerId);
        return true;
    }
}
