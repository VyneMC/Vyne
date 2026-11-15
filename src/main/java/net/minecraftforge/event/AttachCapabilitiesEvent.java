/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.event;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancelableEvent;

public class AttachCapabilitiesEvent<T> extends Event implements ICancelableEvent {
    private final Class<T> type;
    private final T object;
    private final java.util.Map<net.minecraft.resources.ResourceLocation,
            net.minecraftforge.common.capabilities.ICapabilityProvider> providers =
            new java.util.LinkedHashMap<>();

    public AttachCapabilitiesEvent() {
        this(null, null);
    }

    @SuppressWarnings("unchecked")
    public AttachCapabilitiesEvent(Class<T> type, T object) {
        this.type = type;
        this.object = object;
    }

    public Class<T> getGenericType() { return type; }
    public T getObject() { return object; }

    public void addCapability(net.minecraft.resources.ResourceLocation key,
                              net.minecraftforge.common.capabilities.ICapabilityProvider provider) {
        if (key != null && provider != null) {
            providers.put(key, provider);
        }
    }

    public java.util.Map<net.minecraft.resources.ResourceLocation,
            net.minecraftforge.common.capabilities.ICapabilityProvider> getCapabilities() {
        return java.util.Collections.unmodifiableMap(providers);
    }

    public void addListener(Runnable listener) {
    }
}
