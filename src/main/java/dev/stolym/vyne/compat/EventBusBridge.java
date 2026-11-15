/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.compat;

import net.neoforged.bus.api.VyneEventBus;

public final class EventBusBridge {
    private EventBusBridge() {}

    public static net.minecraftforge.eventbus.api.IEventBus forgeBus() {
        return VyneEventBus.global();
    }

    public static net.neoforged.neoforge.eventbus.api.IEventBus neoBus() {
        return VyneEventBus.global();
    }

    public static net.neoforged.bus.api.IEventBus busApiBus() {
        return VyneEventBus.global();
    }
}
