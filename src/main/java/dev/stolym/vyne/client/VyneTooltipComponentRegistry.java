/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.client;

import dev.stolym.vyne.util.VyneLogger;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class VyneTooltipComponentRegistry {
    private VyneTooltipComponentRegistry() {}

    private static final Map<String, Object> FACTORIES = new ConcurrentHashMap<>();

    private static volatile Class<?> CACHED_CLIENT_TOOLTIP_COMPONENT;

    private static final java.util.Set<String> LOGGED_HIT =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private static final java.util.Set<String> LOGGED_MISS =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private static final java.util.Set<String> LOGGED_FACTORY_THREW =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private static final java.util.Set<String> LOGGED_FALLBACK_STUB =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private static volatile boolean LOGGED_CONSULT_FAILURE;

    public static void register(Class<?> markerType, Object factoryOrFunction) {
        if (markerType == null || factoryOrFunction == null) {
            try {
                VyneLogger.warn("[TooltipBridge] register called with null"
                    + " (markerType=" + markerType + ", factory=" + factoryOrFunction + ")");
            } catch (Throwable ignored) {}
            return;
        }
        String key = markerType.getName();
        Object previous = FACTORIES.put(key, factoryOrFunction);
        String msg = previous == null
            ? "[TooltipBridge] Registered factory for " + key
                + " (factory=" + factoryOrFunction.getClass().getName() + ")"
            : "[TooltipBridge] Replaced factory for " + key;
        try { VyneLogger.info(msg); } catch (Throwable ignored) {}
    }

    public static Object tryCreateOrFallback(Object visual) {
        if (visual == null) return null;
        Object bridged = tryCreate(visual);
        if (bridged != null) return bridged;
        String cls = visual.getClass().getName();
        if (cls.startsWith("net.minecraft.") || cls.startsWith("com.mojang.")) {
            return null;
        }
        Object stub = emptyFallback(visual);
        if (stub != null && LOGGED_FALLBACK_STUB.add(cls)) {
            try {
                VyneLogger.warn("[TooltipBridge] No factory for mod visual "
                    + cls + " — returning empty no-op stub to preserve frame."
                    + " Registered keys: " + FACTORIES.keySet());
            } catch (Throwable ignored) {}
        }
        return stub;
    }

    private static Object emptyFallback(Object visual) {
        try {
            Class<?> ctt = resolveClientTooltipComponent(visual);
            if (ctt == null) return null;
            return Proxy.newProxyInstance(
                ctt.getClassLoader(),
                new Class<?>[] { ctt },
                (p, m, args) -> {
                    Class<?> rt = m.getReturnType();
                    if (rt == int.class) return 0;
                    if (rt == boolean.class) return Boolean.FALSE;
                    if (rt == long.class) return 0L;
                    if (rt == double.class) return 0.0d;
                    if (rt == float.class) return 0.0f;
                    if (rt == short.class) return (short) 0;
                    if (rt == byte.class) return (byte) 0;
                    if (rt == char.class) return (char) 0;
                    return null;
                });
        } catch (Throwable t) {
            return null;
        }
    }

    private static Class<?> resolveClientTooltipComponent(Object visual) {
        Class<?> cached = CACHED_CLIENT_TOOLTIP_COMPONENT;
        if (cached != null) return cached;
        ClassLoader[] candidates = {
            visual != null ? visual.getClass().getClassLoader() : null,
            Thread.currentThread().getContextClassLoader(),
            VyneTooltipComponentRegistry.class.getClassLoader()
        };
        for (ClassLoader cl : candidates) {
            if (cl == null) continue;
            try {
                Class<?> c = Class.forName("net.minecraft.class_5684", false, cl);
                CACHED_CLIENT_TOOLTIP_COMPONENT = c;
                return c;
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    public static Object tryCreate(Object visual) {
        if (visual == null) return null;
        String fqn = visual.getClass().getName();
        Object factory = lookup(visual.getClass());
        if (factory == null) {
            if (LOGGED_MISS.add(fqn)) {
                try {
                    VyneLogger.warn("[TooltipBridge] lookup miss for " + fqn
                        + " — current factory keys: " + FACTORIES.keySet());
                } catch (Throwable ignored) {}
            }
            return null;
        }
        try {
            if (factory instanceof Function) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Function fn = (Function) factory;
                @SuppressWarnings("unchecked")
                Object out = fn.apply(visual);
                if (LOGGED_HIT.add(fqn)) {
                    try {
                        VyneLogger.info("[TooltipBridge] tryCreate(" + fqn
                            + ") -> " + (out == null ? "null" : out.getClass().getName())
                            + " [Function]");
                    } catch (Throwable ignored) {}
                }
                return out;
            }

            Method apply = findApply(factory.getClass());
            if (apply != null) {
                apply.setAccessible(true);
                Object out = apply.invoke(factory, visual);
                if (LOGGED_HIT.add(fqn)) {
                    try {
                        VyneLogger.info("[TooltipBridge] tryCreate(" + fqn
                            + ") -> " + (out == null ? "null" : out.getClass().getName())
                            + " [reflect:" + factory.getClass().getName() + "]");
                    } catch (Throwable ignored) {}
                }
                return out;
            } else if (LOGGED_MISS.add(fqn)) {
                try {
                    VyneLogger.warn("[TooltipBridge] tryCreate: no apply(..) on "
                        + factory.getClass().getName());
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            if (LOGGED_FACTORY_THREW.add(fqn)) {
                try {
                    VyneLogger.warn("[TooltipBridge] Factory threw for "
                        + fqn + ": " + t);
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    public static void reportConsultFailure(Throwable t) {
        if (LOGGED_CONSULT_FAILURE) return;
        LOGGED_CONSULT_FAILURE = true;
        try {
            VyneLogger.warn("[TooltipBridge] consultBridgeRegistry threw: " + t);
        } catch (Throwable ignored) {}
    }

    private static Object lookup(Class<?> visualClass) {
        for (Class<?> c = visualClass; c != null && c != Object.class; c = c.getSuperclass()) {
            Object f = FACTORIES.get(c.getName());
            if (f != null) return f;
            for (Class<?> itf : c.getInterfaces()) {
                f = FACTORIES.get(itf.getName());
                if (f != null) return f;
            }
        }
        return null;
    }

    private static Method findApply(Class<?> cls) {
        for (Method m : cls.getMethods()) {
            if (!"apply".equals(m.getName())) continue;
            if (m.getParameterCount() != 1) continue;
            if (m.isBridge() || m.isSynthetic()) continue;
            return m;
        }
        for (Method m : cls.getMethods()) {
            if ("apply".equals(m.getName()) && m.getParameterCount() == 1) {
                return m;
            }
        }
        return null;
    }

    public static int size() {
        return FACTORIES.size();
    }
}
