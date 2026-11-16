/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import java.lang.reflect.Method;

final class FabricEvents {
    private FabricEvents() {}

    static Object fire(String holder, String event, String method, Object... args) throws ReflectiveOperationException {
        ClassLoader loader = FabricEvents.class.getClassLoader();
        Object instance = Class.forName(holder, true, loader).getField(event).get(null);
        Object invoker = Class.forName("net.fabricmc.fabric.api.event.Event", true, loader).getMethod("invoker").invoke(instance);
        for (Class<?> type : invoker.getClass().getInterfaces()) {
            for (Method m : type.getMethods()) {
                if (m.getName().equals(method) && m.getParameterCount() == args.length) return m.invoke(invoker, args);
            }
        }
        throw new NoSuchMethodException(holder + "." + event + "." + method);
    }

    static Boolean decision(Object actionResult) {
        if (!(actionResult instanceof Enum<?> result)) return null;
        return switch (result.name()) {
            case "PASS" -> null;
            case "FAIL" -> Boolean.FALSE;
            default -> Boolean.TRUE;
        };
    }

    static Object call(Object owner, String name, Object... args) throws ReflectiveOperationException {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                    m.setAccessible(true);
                    return m.invoke(owner, args);
                }
            }
        }
        for (Method m : owner.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) return m.invoke(owner, args);
        }
        throw new NoSuchMethodException(owner.getClass().getName() + "." + name);
    }
}
