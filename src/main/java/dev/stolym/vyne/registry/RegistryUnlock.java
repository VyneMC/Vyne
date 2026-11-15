/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.registry;

import dev.stolym.vyne.util.VyneLogger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

public final class RegistryUnlock {
    private static final String[] FROZEN = {"field_36463", "frozen"};
    private static final String[] INTRUSIVE_HOLDERS = {"field_40584", "unregisteredIntrusiveHolders"};
    private static final String[] FREEZE = {"method_40276", "freeze"};

    private RegistryUnlock() {}

    public record Token(Object registry, boolean wasFrozen) {}

    public static Token open(Object registry) {
        if (registry == null) return new Token(null, false);
        try {
            Field frozen = findField(registry.getClass(), FROZEN);
            Field intrusive = findField(registry.getClass(), INTRUSIVE_HOLDERS);
            if (frozen == null) return new Token(registry, false);
            boolean wasFrozen = frozen.getBoolean(registry);
            if (wasFrozen) {
                frozen.setBoolean(registry, false);
                if (intrusive != null && intrusive.get(registry) == null) {
                    intrusive.set(registry, new IdentityHashMap<>());
                }
            }
            return new Token(registry, wasFrozen);
        } catch (ReflectiveOperationException | RuntimeException e) {
            VyneLogger.warn("[RegistryUnlock] Could not open " + registry + ": " + e);
            return new Token(registry, false);
        }
    }

    public static void close(Token token) {
        if (token == null || token.registry() == null || !token.wasFrozen()) return;
        Object registry = token.registry();
        try {
            Method freeze = findMethod(registry.getClass(), FREEZE);
            if (freeze != null) {
                freeze.invoke(registry);
                return;
            }
            Field frozen = findField(registry.getClass(), FROZEN);
            if (frozen != null) frozen.setBoolean(registry, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
            VyneLogger.warn("[RegistryUnlock] Re-freezing " + registry + " reported: " + cause);
            dropUnboundIntrusiveHolders(registry);
        }
    }

    private static void dropUnboundIntrusiveHolders(Object registry) {
        try {
            Field intrusive = findField(registry.getClass(), INTRUSIVE_HOLDERS);
            if (intrusive != null && intrusive.get(registry) instanceof Map<?, ?> map) map.clear();
            Field frozen = findField(registry.getClass(), FROZEN);
            if (frozen != null) frozen.setBoolean(registry, true);
            if (intrusive != null) intrusive.set(registry, null);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Field findField(Class<?> type, String[] names) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (String name : names) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {
                }
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String[] names) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (String name : names) {
                try {
                    Method m = c.getDeclaredMethod(name);
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        return null;
    }
}
