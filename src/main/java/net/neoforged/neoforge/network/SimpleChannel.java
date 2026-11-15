/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.network;

import dev.stolym.vyne.util.VyneLogger;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SimpleChannel {
    public <MSG> SimpleChannel registerMessage(int id, Class<MSG> messageType, BiConsumer<MSG, Object> encoder, Function<Object, MSG> decoder, BiConsumer<MSG, Supplier<Object>> messageConsumer) {
        return this;
    }

    public <MSG> SimpleChannel registerMessage(int id, Class<MSG> messageType,
            BiConsumer<MSG, Object> encoder, Function<Object, MSG> decoder,
            BiConsumer<MSG, Supplier<Object>> messageConsumer, NetworkDirection direction) {
        return registerMessage(id, messageType, encoder, decoder, messageConsumer);
    }

    public <MSG> MessageBuilder<MSG> messageBuilder(Class<MSG> messageType, int id) {
        return new MessageBuilder<>(this, messageType, id);
    }

    public <MSG> MessageBuilder<MSG> messageBuilder(Class<MSG> messageType, int id, NetworkDirection direction) {
        VyneLogger.debug("[Vyne-Shim] SimpleChannel.messageBuilder(" + messageType.getSimpleName() + ", " + id + ", " + direction + ")");
        return new MessageBuilder<>(this, messageType, id);
    }

    public void sendToServer(Object packet) {
        VyneLogger.debug("[Vyne-Shim] SimpleChannel.sendToServer — no-op ("
            + (packet != null ? packet.getClass().getSimpleName() : "null") + ")");
    }

    public void send(Object packet, Object target) {
        VyneLogger.debug("[Vyne-Shim] SimpleChannel.send(packet, Object) — no-op ("
            + (packet != null ? packet.getClass().getSimpleName() : "null") + ")");
    }

    public void sendTo(Object packet, Object connection, NetworkDirection direction) {
        VyneLogger.debug("[Vyne-Shim] SimpleChannel.sendTo(packet, connection, " + direction + ") — no-op");
    }

    public void reply(Object packet, Object context) {
        VyneLogger.debug("[Vyne-Shim] SimpleChannel.reply — no-op ("
            + (packet != null ? packet.getClass().getSimpleName() : "null") + ")");
    }

    public String getProtocolVersion() { return "1"; }

    public boolean isRemotePresent(Object connection) { return false; }

    public static final class MessageBuilder<MSG> {
        private final SimpleChannel parent;
        private final Class<MSG> messageType;
        private final int id;

        private MessageBuilder(SimpleChannel parent, Class<MSG> messageType, int id) {
            this.parent = parent;
            this.messageType = messageType;
            this.id = id;
        }

        public <BUF> MessageBuilder<MSG> encoder(BiConsumer<MSG, BUF> encoder) {
            return this;
        }

        public <BUF> MessageBuilder<MSG> decoder(Function<BUF, MSG> decoder) {
            return this;
        }

        public MessageBuilder<MSG> consumer(BiConsumer<MSG, Supplier<?>> consumer) {
            return this;
        }

        public MessageBuilder<MSG> consumerNetworkThread(BiConsumer<MSG, Supplier<?>> consumer) {
            return this;
        }

        public MessageBuilder<MSG> consumerMainThread(BiConsumer<MSG, Supplier<?>> consumer) {
            return this;
        }

        public MessageBuilder<MSG> noResponse() {
            return this;
        }

        public SimpleChannel add() {
            VyneLogger.debug("[Vyne-Shim] SimpleChannel.MessageBuilder.add() for " + messageType.getSimpleName() + " id=" + id + " — no-op stub (Fabric networking not wired)");
            return parent;
        }
    }
}
