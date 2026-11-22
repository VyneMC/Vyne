/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.stolym.vyne.hooks.fabric.FabricPayloadHooks;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerboundCustomPayloadPacket.class)
public abstract class FabricServerboundPayloadHook {
    private static final String NEOFORGE_CODEC = "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;Lnet/minecraft/network/ConnectionProtocol;Lnet/minecraft/network/protocol/PacketFlow;)Lnet/minecraft/network/codec/StreamCodec;";

    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", ordinal = 0, target = NEOFORGE_CODEC))
    private static <B extends FriendlyByteBuf> StreamCodec<B, CustomPacketPayload> vyne$playPayloads(StreamCodec<B, CustomPacketPayload> codec) {
        return FabricPayloadHooks.withFabricPayloads(codec, PayloadTypeRegistryImpl.PLAY_C2S);
    }

    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", ordinal = 1, target = NEOFORGE_CODEC))
    private static <B extends FriendlyByteBuf> StreamCodec<B, CustomPacketPayload> vyne$configurationPayloads(StreamCodec<B, CustomPacketPayload> codec) {
        return FabricPayloadHooks.withFabricPayloads(codec, PayloadTypeRegistryImpl.CONFIGURATION_C2S);
    }
}
