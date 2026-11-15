/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.client.event;

import net.neoforged.bus.api.Event;

public class RegisterKeyMappingsEvent extends Event {
    public void register(net.minecraft.class_304 mapping) {
        dev.stolym.vyne.client.FabricKeyBindingBridge.enqueue(mapping);
    }
}
