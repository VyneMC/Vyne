/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.fabric;

import net.fabricmc.fabric.impl.networking.FabricCustomPayloadPacketCodec;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class FabricPayloadHooks {
    private FabricPayloadHooks() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <B extends FriendlyByteBuf> StreamCodec<B, CustomPacketPayload> withFabricPayloads(StreamCodec<B, CustomPacketPayload> codec,
                                                                                                    PayloadTypeRegistryImpl<?> registry) {
        ((FabricCustomPayloadPacketCodec) codec).fabric_setPacketCodecProvider((buf, id) -> registry.get(id));
        return codec;
    }
}
