/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class VyneFieldRegistry {
    private static Map<String, Object> REGISTRY;

    private static synchronized Map<String, Object> getRegistry() {
        if (REGISTRY == null) {
            REGISTRY = new java.util.concurrent.ConcurrentHashMap<>();
        }
        return REGISTRY;
    }

    public static void register(String key, Object value) {
        if (key == null || value == null) return;
        synchronized (VyneFieldRegistry.class) {
            getRegistry().put(key, value);
        }
    }

    public static Object resolve(String className, String fieldName) {
        String key = className + "#" + fieldName;
        return getRegistry().get(key);
    }

    private static final Set<String> POPULATING = Collections.synchronizedSet(new HashSet<>());

    public static void lazyPopulate(Class<?> cls, Field field) {
        String guard = cls.getName();
        if (!POPULATING.add(guard)) return;
        try {
        field.setAccessible(true);
        if (field.get(null) != null) return;
        Object value = resolve(cls.getName(), field.getName());
        if (value != null) {
            field.set(null, value);
        }
    } catch (Throwable ignored) {
    } finally {
        POPULATING.remove(guard);
    }
    }
}
