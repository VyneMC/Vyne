/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import dev.stolym.vyne.util.VyneLogger;

import java.lang.reflect.Method;

public final class FabricScreens {
    private static final String EVENTS = "net.fabricmc.fabric.api.client.screen.v1.ScreenEvents";

    private FabricScreens() {}

    public static void fire(String event, String listenerMethod, Object screen, Object... extra) {
        try {
            Method factory = null;
            for (Method m : Class.forName(EVENTS, true, FabricScreens.class.getClassLoader()).getMethods()) {
                if (m.getName().equals(event) && m.getParameterCount() == 1) factory = m;
            }
            Object fabricEvent = factory.invoke(null, screen);
            Object invoker = Class.forName("net.fabricmc.fabric.api.event.Event", true, FabricScreens.class.getClassLoader()).getMethod("invoker").invoke(fabricEvent);
            Object[] args = new Object[extra.length + 1];
            args[0] = screen;
            System.arraycopy(extra, 0, args, 1, extra.length);
            for (Class<?> type : invoker.getClass().getInterfaces()) {
                for (Method m : type.getMethods()) {
                    if (m.getName().equals(listenerMethod) && m.getParameterCount() == args.length) {
                        m.invoke(invoker, args);
                        return;
                    }
                }
            }
            throw new NoSuchMethodException(EVENTS + "." + event + " listener " + listenerMethod);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            if (e.getCause() instanceof Error error) throw error;
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException | RuntimeException e) {
            VyneLogger.warn("[PatchLayer] Fabric screen event " + event + " could not be fired: " + e);
        }
    }
}
