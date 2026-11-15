/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.fluid;

import dev.stolym.vyne.util.VyneLogLevel;
import dev.stolym.vyne.util.VyneLogger;

import java.util.*;
import java.util.stream.Stream;

public final class VyneFluidTypeRegistry {
    private static final Map<String, Object> BY_RL =
        Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<Object, String> REVERSE =
        Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    private static boolean frozen = false;

    public static Object register(Object id, Object fluidType) {
        String key = id != null ? id.toString() : "null";
        if (id == null || fluidType == null) {
            if (VyneLogLevel.isVerbose()) {
                VyneLogger.warn("[VyneFluidTypeRegistry] Registration failed: id or fluidType is null (key=" + key + ").");
            }
            return null;
        }
        BY_RL.put(key, fluidType);
        REVERSE.put(fluidType, key);
        if (VyneLogLevel.isVerbose()) {
            VyneLogger.info("[VyneFluidTypeRegistry] Registered fluid type: " + key);
        }
        return fluidType;
    }

    public static Object register(Object registryArg, Object id, Object fluidType) {
        return register(id, fluidType);
    }

    public static Object get(Object id) {
        return BY_RL.get(id != null ? id.toString() : "null");
    }

    public static Object getByKey(Object resourceKey) {
        try {
            java.lang.reflect.Method locMethod = resourceKey.getClass().getMethod("location");
            Object rl = locMethod.invoke(resourceKey);
            return BY_RL.get(rl != null ? rl.toString() : "null");
        } catch (Throwable ignored) {}
        return BY_RL.get(resourceKey.toString());
    }

    public static Optional<Object> getOptional(Object id) {
        return Optional.ofNullable(BY_RL.get(id != null ? id.toString() : "null"));
    }

    public static Optional<Object> getOptionalByKey(Object resourceKey) {
        return Optional.ofNullable(getByKey(resourceKey));
    }

    public static String getKey(Object fluidType) {
        return REVERSE.get(fluidType);
    }

    public static boolean containsKey(Object id) {
        return BY_RL.containsKey(id != null ? id.toString() : "null");
    }

    public static Map<String, Object> all() {
    if (BY_RL.isEmpty()) {
        try {
            Class<?> shimClass = Class.forName(
                "net.neoforged.neoforge.registries.NeoForgeRegistries",
                false,
                Thread.currentThread().getContextClassLoader());
            java.lang.reflect.Method getter = shimClass.getMethod("getFluidTypeRegistry");
            Object shim = getter.invoke(null);
            java.lang.reflect.Method valuesMethod = shim.getClass().getMethod("values");
            java.util.Collection<?> vals = (java.util.Collection<?>) valuesMethod.invoke(shim);
            java.lang.reflect.Method keyMethod = shim.getClass().getMethod("getKey", Object.class);
            for (Object v : vals) {
                Object rl = keyMethod.invoke(shim, v);
                if (rl != null) BY_RL.put(rl.toString(), v);
            }
        } catch (Throwable ignored) {}
    }
    if (VyneLogLevel.isVerbose()) {
        VyneLogger.debug("[VyneFluidTypeRegistry] all() snapshot size=" + BY_RL.size());
    }
    return Collections.unmodifiableMap(BY_RL);
    }

    public static Set<String> keySet() {
        return Collections.unmodifiableSet(BY_RL.keySet());
    }

    public static Collection<Object> values() {
        return Collections.unmodifiableCollection(BY_RL.values());
    }

    public static Stream<Object> stream() {
        return new ArrayList<>(BY_RL.values()).stream();
    }

    public static int size() {
        return BY_RL.size();
    }

    public static boolean isEmpty() {
        return BY_RL.isEmpty();
    }

    public static void freeze() {
        frozen = true;
    }

    public static boolean isFrozen() {
        return frozen;
    }

    public static Optional<Object> getHolder(Object id) {
        Object val = BY_RL.get(id != null ? id.toString() : "null");
        return val != null ? Optional.of(val) : Optional.empty();
    }

    public static Optional<Object> getHolderByKey(Object resourceKey) {
        Object val = getByKey(resourceKey);
        return val != null ? Optional.of(val) : Optional.empty();
    }

    public static Optional<Object> getHolderById(int numericId) {
        int idx = 0;
        for (Object v : BY_RL.values()) {
            if (idx++ == numericId) return Optional.of(v);
        }
        return Optional.empty();
    }

    public static Object registerByKey(Object resourceKey, Object fluidType) {
        try {
            java.lang.reflect.Method locMethod = resourceKey.getClass().getMethod("location");
            Object rl = locMethod.invoke(resourceKey);
            if (rl == null || fluidType == null) {
                if (VyneLogLevel.isVerbose()) {
                    VyneLogger.warn("[VyneFluidTypeRegistry] registerByKey got null rl/fluidType (rk=" + resourceKey + ")");
                }
                return null;
            }
            return register(rl, fluidType);
        } catch (Throwable ignored) {
            if (VyneLogLevel.isVerbose()) {
                VyneLogger.warn("[VyneFluidTypeRegistry] registerByKey: couldn't resolve location for " + resourceKey + " — falling back (" + ignored.getClass().getSimpleName() + ")");
            }
        }
        Object fallbackId = resourceKey != null ? resourceKey.toString() : "null";
        return register(fallbackId, fluidType);
    }

    public static java.util.Optional<Object> getResourceKey(Object fluidType) {
        String rl = REVERSE.get(fluidType);
        return rl != null ? java.util.Optional.of(rl) : java.util.Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public static <K> Map<K, Object> allTyped() {
        return (Map<K, Object>) (Map<?, ?>) BY_RL;
    }

    public static Object wrapAsHolder(Object fluidType) {
        return fluidType;
    }

    public static void dump() {
        VyneLogger.info("[VyneFluidTypeRegistry] Registered fluid types (" + BY_RL.size() + "):");
        if (VyneLogLevel.isVerbose()) {
            BY_RL.forEach((k, v) -> VyneLogger.info("  " + k + " -> " + v));
        }
    }
}
