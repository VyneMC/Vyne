/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import dev.stolym.vyne.core.VyneMode;
import dev.stolym.vyne.util.VyneLogger;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class PatchLayerEvents {
    private static boolean registered;

    private PatchLayerEvents() {}

    public static synchronized void register() {
        if (registered || !VyneMode.hooks()) return;
        registered = true;
        FabricLoader fabric = FabricLoader.getInstance();
        try {
            if (fabric.isModLoaded("fabric-content-registries-v0")) {
                listen("net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent", FabricFuel::onBurnTime);
                VyneLogger.info("[PatchLayer] Fabric furnace fuels answered through NeoForge's FurnaceFuelBurnTimeEvent");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            VyneLogger.warn("[PatchLayer] Could not register Fabric API bridges: " + e);
        }
    }

    private static void listen(String eventClass, Consumer<Object> listener) throws ReflectiveOperationException {
        ClassLoader loader = PatchLayerEvents.class.getClassLoader();
        Object bus = Class.forName("net.neoforged.neoforge.common.NeoForge", true, loader).getField("EVENT_BUS").get(null);
        Object normal = Class.forName("net.neoforged.bus.api.EventPriority", true, loader).getField("NORMAL").get(null);
        Class<?> type = Class.forName(eventClass, true, loader);
        for (Method m : bus.getClass().getMethods()) {
            if (m.getName().equals("addListener") && m.getParameterCount() == 4 && m.getParameterTypes()[2] == Class.class) {
                m.invoke(bus, normal, false, type, listener);
                return;
            }
        }
        throw new NoSuchMethodException("IEventBus.addListener(EventPriority, boolean, Class, Consumer)");
    }
}
