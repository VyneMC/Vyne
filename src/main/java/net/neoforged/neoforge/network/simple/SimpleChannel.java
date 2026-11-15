/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.network.simple;

import dev.stolym.vyne.util.VyneLogger;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class SimpleChannel {
    private final ResourceLocation name;

    private SimpleChannel(ResourceLocation name) {
        this.name = name;
    }

    public static SimpleChannel create(ResourceLocation name) {
        VyneLogger.debug("[VCL-Shim] SimpleChannel.create(" + name + ")");
        return new SimpleChannel(name);
    }

    public static SimpleChannel create(ResourceLocation name, Supplier<String> networkProtocolVersion,
            Function<String, Boolean> clientAcceptedVersions, Function<String, Boolean> serverAcceptedVersions) {
        return create(name);
    }

    public <MSG> void registerMessage(int index, Class<MSG> messageType,
            BiConsumer<MSG, ?> encoder, Function<?, MSG> decoder, BiConsumer<MSG, ?> handler) {
        VyneLogger.debug("[VCL-Shim] SimpleChannel.registerMessage(" + index + ", " + messageType.getSimpleName() + ") — no-op stub");
    }

    public <MSG> void registerMessage(int index, Class<MSG> messageType,
            BiConsumer<MSG, ?> encoder, Function<?, MSG> decoder, BiConsumer<MSG, ?> handler, java.util.Optional<?> direction) {
        registerMessage(index, messageType, encoder, decoder, handler);
    }

    public <MSG> MessageBuilder<MSG> messageBuilder(Class<MSG> type, int id) {
        return new MessageBuilder<>(this, type);
    }

    public <MSG> MessageBuilder<MSG> messageBuilder(Class<MSG> type, int id, Object direction) {
        return new MessageBuilder<>(this, type);
    }

    public void send(Object target, Object message) {
        VyneLogger.debug("[VCL-Shim] SimpleChannel.send() — no-op stub");
    }

    public void sendToServer(Object message) {
        VyneLogger.debug("[VCL-Shim] SimpleChannel.sendToServer() — no-op stub");
    }

    public void reply(Object message, Object context) {
        VyneLogger.debug("[VCL-Shim] SimpleChannel.reply() — no-op stub");
    }

    public boolean isRemotePresent(Object player) {
        return false;
    }

    public static final class MessageBuilder<MSG> {
        private final SimpleChannel channel;
        private final Class<MSG> type;

        private MessageBuilder(SimpleChannel channel, Class<MSG> type) {
            this.channel = channel;
            this.type = type;
        }

        public MessageBuilder<MSG> encoder(BiConsumer<MSG, ?> encoder) { return this; }
        public MessageBuilder<MSG> decoder(Function<?, MSG> decoder) { return this; }
        public MessageBuilder<MSG> consumer(BiConsumer<MSG, ?> handler) { return this; }
        public MessageBuilder<MSG> consumer(Object handler) { return this; }
        public MessageBuilder<MSG> direction(Object direction) { return this; }
        public MessageBuilder<MSG> noResponse() { return this; }

        public void add() {
            VyneLogger.debug("[VCL-Shim] MessageBuilder.add() for " + type.getSimpleName() + " — no-op stub");
        }
    }
}
