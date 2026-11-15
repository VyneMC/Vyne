/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.client;

import dev.stolym.vyne.util.VyneLogger;

import java.lang.reflect.Constructor;

public final class ClientModEvents {
    private static final String[] KEY_MAPPING_EVENTS = {
            "net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent",
            "net.minecraftforge.client.event.RegisterKeyMappingsEvent",
    };

    private ClientModEvents() {}

    public static void fireRegisterKeyMappings(Object options) {
        if (options == null) return;
        ClassLoader loader = options.getClass().getClassLoader();
        int posted = 0;
        for (String eventName : KEY_MAPPING_EVENTS) {
            Object event = newEvent(loader, eventName, options);
            if (event == null) continue;
            posted += postToModBuses(event);
        }
        int bridged = 0;
        try {
            bridged = FabricKeyBindingBridge.flushQueued();
        } catch (Throwable t) {
            VyneLogger.warn("[KeyBind] Could not bridge queued key mappings: " + t);
        }
        VyneLogger.info("[KeyBind] RegisterKeyMappingsEvent posted to " + posted + " mod bus(es)"
                + (bridged > 0 ? ", " + bridged + " mapping(s) bridged" : ""));
    }

    private static Object newEvent(ClassLoader loader, String className, Object argument) {
        try {
            Class<?> type = Class.forName(className, true, loader);
            for (Constructor<?> ctor : type.getDeclaredConstructors()) {
                if (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0].isInstance(argument)) {
                    ctor.setAccessible(true);
                    return ctor.newInstance(argument);
                }
            }
            Constructor<?> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ClassNotFoundException | NoClassDefFoundError | NoSuchMethodException e) {
            return null;
        } catch (Throwable t) {
            VyneLogger.warn("[ClientEvents] Could not create " + className + ": " + t);
            return null;
        }
    }

    public static int postToModBuses(Object event) {
        return dev.stolym.vyne.core.ModBusRegistry.post(event);
    }
}
