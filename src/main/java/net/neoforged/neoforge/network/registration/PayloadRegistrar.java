/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.network.registration;

import net.neoforged.neoforge.network.handling.IPayloadHandler;

public class PayloadRegistrar {
    public static IPayloadRegistrar of(String modId) {
        return IPayloadRegistrar.createProxy(modId);
    }

    public PayloadRegistrar() {
    }

    public IPayloadRegistrar play(Object type, Object reader) {
        return IPayloadRegistrar.createProxy("unknown");
    }

    public IPayloadRegistrar configuration(Object type, Object reader) {
        return IPayloadRegistrar.createProxy("unknown");
    }

    public PayloadRegistrar playToClient(Object type, Object codec, IPayloadHandler<?> handler) {
        return this;
    }

    public PayloadRegistrar playToServer(Object type, Object codec, IPayloadHandler<?> handler) {
        return this;
    }

    public PayloadRegistrar playBidirectional(Object type, Object codec, IPayloadHandler<?> handler) {
        return this;
    }

    public PayloadRegistrar configurationToClient(Object type, Object codec, IPayloadHandler<?> handler) {
        return this;
    }

    public PayloadRegistrar configurationToServer(Object type, Object codec, IPayloadHandler<?> handler) {
        return this;
    }

    public PayloadRegistrar configurationBidirectional(Object type, Object codec, IPayloadHandler<?> handler) {
        return this;
    }

    public PayloadRegistrar commonToClient(Object type, Object codec, IPayloadHandler<?> handler) {
        return this;
    }

    public PayloadRegistrar commonToServer(Object type, Object codec, IPayloadHandler<?> handler) {
        return this;
    }

    public PayloadRegistrar commonBidirectional(Object type, Object codec, IPayloadHandler<?> handler) {
        return this;
    }

    public PayloadRegistrar versioned(String version) {
        return this;
    }

    public PayloadRegistrar optional() {
        return this;
    }

    public PayloadRegistrar executesOn(Object thread) {
        return this;
    }
}
