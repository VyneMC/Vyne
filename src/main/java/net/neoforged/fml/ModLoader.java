/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.fml;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import java.util.function.Function;

public final class ModLoader {
    private ModLoader() {}

    public static <T extends Event & IModBusEvent> void postEvent(T event) {
        dev.stolym.vyne.core.ModBusRegistry.post(event);
    }

    public static <T extends Event & IModBusEvent> T postEventWithReturn(T event) {
        dev.stolym.vyne.core.ModBusRegistry.post(event);
        return event;
    }

    public static <T extends Event & IModBusEvent> void postEventWrapContainerInModOrder(T event) {
        dev.stolym.vyne.core.ModBusRegistry.post(event);
    }

    public static <T extends Event & IModBusEvent> void runEventGenerator(Function<ModContainer, T> generator) {
        for (String modId : dev.stolym.vyne.core.ModBusRegistry.modIds()) {
            ModList.get().getModContainerById(modId).ifPresent(container -> {
                T event = generator.apply(container);
                if (event != null) dev.stolym.vyne.core.ModBusRegistry.postTo(modId, event);
            });
        }
    }

    public static boolean hasErrors() {
        return false;
    }
}
