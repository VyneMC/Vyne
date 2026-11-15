/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */
package net.minecraftforge.fml.util;

import dev.stolym.vyne.mappings.MappingComposer;
import dev.stolym.vyne.mappings.MappingRegistry;
import dev.stolym.vyne.util.VyneLogger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ObfuscationReflectionHelper {
    private ObfuscationReflectionHelper() {}

    public static Field findField(final Class<?> clazz, final String fieldName) {
        try {
            Field f = resolveField(clazz, fieldName);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new UnableToFindFieldException(
                "Unable to find field " + fieldName + " on " + clazz.getName()
                    + " — tried mojmap + intermediary + inherited lookup", e);
        }
    }

    public static <T, E> T getPrivateValue(final Class<? super E> classToAccess,
                                           final E instance, final String fieldName) {
        try {
            @SuppressWarnings("unchecked")
            T v = (T) findField(classToAccess, fieldName).get(instance);
            return v;
        } catch (IllegalAccessException e) {
            throw new UnableToAccessFieldException(e);
        }
    }

    public static <T, E> void setPrivateValue(final Class<? super T> classToAccess,
                                              final T instance, final E value,
                                              final String fieldName) {
        try {
            findField(classToAccess, fieldName).set(instance, value);
        } catch (IllegalAccessException e) {
            throw new UnableToAccessFieldException(e);
        }
    }

    public static Method findMethod(final Class<?> clazz, final String methodName,
                                    final Class<?>... parameterTypes) {
        try {
            Method m = resolveMethod(clazz, methodName, parameterTypes);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            throw new UnableToFindMethodException(e);
        }
    }

    private static Field resolveField(Class<?> clazz, String fieldName)
            throws NoSuchFieldException {
        NoSuchFieldException first = null;

        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                if (first == null) first = e;
            }
        }

        try {
            MappingComposer.ComposedMappings m = MappingRegistry.get();
            if (m != null) {
                String mojClassName = toMojmapInternal(clazz);
                if (mojClassName != null) {
                    String intermediary = m.mapField(mojClassName, fieldName);
                    if (intermediary != null && !intermediary.equals(fieldName)) {
                        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
                            try {
                                return c.getDeclaredField(intermediary);
                            } catch (NoSuchFieldException ignored) {
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            VyneLogger.debug("[ORH] mapping-assisted findField failed: " + t);
        }
        throw first != null ? first : new NoSuchFieldException(fieldName);
    }

    private static Method resolveMethod(Class<?> clazz, String methodName,
                                        Class<?>... params) throws NoSuchMethodException {
        NoSuchMethodException first = null;
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(methodName, params);
            } catch (NoSuchMethodException e) {
                if (first == null) first = e;
            }
        }
        throw first != null ? first : new NoSuchMethodException(methodName);
    }

    private static String toMojmapInternal(Class<?> clazz) {
        String runtime = clazz.getName().replace('.', '/');
        if (!runtime.startsWith("net/minecraft/")) return runtime;
        MappingComposer.ComposedMappings m = MappingRegistry.get();
        if (m == null) return runtime;

        String mojmap = m.unmapClass(runtime);
        return mojmap != null ? mojmap : runtime;
    }

    public static class UnableToFindFieldException extends RuntimeException {
        public UnableToFindFieldException(String msg, Throwable cause) { super(msg, cause); }
    }

    public static class UnableToAccessFieldException extends RuntimeException {
        public UnableToAccessFieldException(Throwable cause) { super(cause); }
    }

    public static class UnableToFindMethodException extends RuntimeException {
        public UnableToFindMethodException(Throwable cause) { super(cause); }
    }
}
