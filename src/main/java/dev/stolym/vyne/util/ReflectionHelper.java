/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

public class ReflectionHelper {
    public static Object call(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (String name : names) {
            Class<?> c = target.getClass();
            while (c != null && c != Object.class) {
                Method[] declared;
                try {
                    declared = c.getDeclaredMethods();
                } catch (Throwable ignored) {
                    declared = null;
                }
                if (declared != null) {
                    for (Method m : declared) {
                        if (m.getName().equals(name) && m.getParameterCount() == 0) {
                            try {
                                m.setAccessible(true);
                                return m.invoke(target);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
                c = c.getSuperclass();
            }
        }
        return null;
    }

    public static Object callStatic(String className, String methodName, Object... args) {
        try {
            Class<?> clazz = Class.forName(className);
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                    m.setAccessible(true);
                    return m.invoke(null, args);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static String extractRegistryKeyLocation(Object key) {
        if (key == null) {
            return null;
        }
        return extractRegistryKeyLocationWithRegistryUnwrap(key, 0);
    }

    public static String normalizeRegistryKeyLocationString(String s) {
        if (s == null) {
            return null;
        }
        int colon = s.indexOf(':');
        if (colon < 0) {
            return s;
        }
        String ns = s.substring(0, colon);
        String path = s.substring(colon + 1).replace('\\', '/');
        return ns + ":" + path;
    }

    public static boolean registryKeyLocationsMatch(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        return Objects.equals(normalizeRegistryKeyLocationString(a), normalizeRegistryKeyLocationString(b));
    }

    private static String extractRegistryKeyLocationWithRegistryUnwrap(Object key, int depth) {
        if (key == null || depth > 8) {
            return null;
        }
        Object unwrapped = tryUnwrapRegistryLikeKey(key);
        if (unwrapped != null && unwrapped != key) {
            String nested = extractRegistryKeyLocationWithRegistryUnwrap(unwrapped, depth + 1);
            if (nested != null && !nested.isEmpty() && !"null".equals(nested)) {
                return nested;
            }
        }
        return extractRegistryKeyLocationCore(key);
    }

    private static Object tryUnwrapRegistryLikeKey(Object key) {
        if (key == null) {
            return null;
        }
        for (String n : UNWRAP_METHOD_NAMES) {
            Method m = publicNoArgMethod(key.getClass(), n);
            if (m == null) {
                continue;
            }
            try {
                m.setAccessible(true);
                return m.invoke(key);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static final String[] UNWRAP_METHOD_NAMES = {"key", "method_30517", "getRegistryKey"};

    private static final ClassValue<java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<Method>>> NO_ARG_METHODS =
            new ClassValue<>() {
                @Override
                protected java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<Method>> computeValue(Class<?> type) {
                    return new java.util.concurrent.ConcurrentHashMap<>();
                }
            };

    private static Method publicNoArgMethod(Class<?> type, String name) {
        return NO_ARG_METHODS.get(type).computeIfAbsent(name, n -> {
            try {
                return java.util.Optional.of(type.getMethod(n));
            } catch (Throwable t) {
                return java.util.Optional.empty();
            }
        }).orElse(null);
    }

    private static String extractRegistryKeyLocationCore(Object key) {
        String rlNsPath = namespacePathFromRlLike(key);
        if (rlNsPath != null) {
            return rlNsPath;
        }

        try {
            Method m = publicNoArgMethod(key.getClass(), "getRegistryPath");
            if (m != null && m.getReturnType() == String.class) {
                m.setAccessible(true);
                Object v = m.invoke(key);
                if (v instanceof String s && !s.isEmpty()) {
                    return s;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> kc = key.getClass();
            if (kc.getName().endsWith("ForgeRegistries$SimpleForgeRegistry")) {
                Field f = kc.getDeclaredField("registryPath");
                f.setAccessible(true);
                Object v = f.get(key);
                if (v instanceof String s && !s.isEmpty()) {
                    return s;
                }
            }
        } catch (Throwable ignored) {
        }
        String str = key.toString();

        if (str.startsWith("ResourceKey[")) {
            int sep = str.indexOf(" / ");
            if (sep > 0 && str.endsWith("]")) {
                return str.substring(sep + 3, str.length() - 1).trim();
            }
        }
        try {
            Method m = publicNoArgMethod(key.getClass(), "location");
            if (m != null) {
                Object rl = m.invoke(key);
                if (rl != null) {
                    return rl.toString();
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Method m = publicNoArgMethod(key.getClass(), "method_29177");
            if (m != null) {
                Object rl = m.invoke(key);
                if (rl != null) {
                    return rl.toString();
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Method m = publicNoArgMethod(key.getClass(), "method_29184");
            if (m != null) {
                Object rl = m.invoke(key);
                if (rl != null) {
                    return rl.toString();
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            for (Method method : key.getClass().getMethods()) {
                if (method.getParameterCount() != 0) {
                    continue;
                }
                String n = method.getName();
                if ("registry".equals(n) || "method_29177".equals(n)) {
                    continue;
                }
                Class<?> ret = method.getReturnType();
                String retName = ret.getName();
                if (!retName.equals("net.minecraft.resources.ResourceLocation")
                        && !retName.contains("class_2960")) {
                    continue;
                }
                Object result = method.invoke(key);
                if (result != null) {
                    return result.toString();
                }
            }
        } catch (Throwable ignored) {
        }
        int slash = str.lastIndexOf('/');
        if (slash >= 0 && str.endsWith("]")) {
            return str.substring(slash + 1, str.length() - 1).trim();
        }
        return str;
    }

    private static String namespacePathFromRlLike(Object key) {
        String cn = key.getClass().getName();
        if (!"net.minecraft.resources.ResourceLocation".equals(cn) && !"net.minecraft.class_2960".equals(cn)) {
            return null;
        }
        try {
            Method ns = key.getClass().getMethod("getNamespace");
            Method ps = key.getClass().getMethod("getPath");
            Object a = ns.invoke(key);
            Object b = ps.invoke(key);
            if (a != null && b != null) {
                return a + ":" + b;
            }
        } catch (Throwable ignored) {
        }
        try {
            Method ns = key.getClass().getMethod("method_12836");
            Method ps = key.getClass().getMethod("method_12832");
            Object a = ns.invoke(key);
            Object b = ps.invoke(key);
            if (a != null && b != null) {
                return a + ":" + b;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static Object newResourceLocationKnot(Class<?> rlClass, String namespace, String path) {
        if (rlClass == null || namespace == null || path == null) {
            return null;
        }
        try {
            for (Constructor<?> c : rlClass.getConstructors()) {
                if (c.getParameterCount() == 2
                        && c.getParameterTypes()[0] == String.class
                        && c.getParameterTypes()[1] == String.class) {
                    return c.newInstance(namespace, path);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static Object newResourceLocationKnot(Class<?> rlClass, String combinedId) {
        if (combinedId == null || combinedId.isEmpty()) {
            return null;
        }
        int colon = combinedId.indexOf(':');
        if (colon <= 0) {
            return newResourceLocationKnot(rlClass, "minecraft", combinedId);
        }
        return newResourceLocationKnot(rlClass,
            combinedId.substring(0, colon),
            combinedId.substring(colon + 1));
    }

    public static Object newResourceLocationKnotWithStringFallback(Class<?> rlClass, String combinedId) {
        Object direct = newResourceLocationKnot(rlClass, combinedId);
        if (direct != null) {
            return direct;
        }
        if (combinedId == null) {
            return null;
        }
        for (String n : new String[] {"parse", "of", "tryParse"}) {
            try {
                Method m = rlClass.getMethod(n, String.class);
                if (Modifier.isStatic(m.getModifiers()) && rlClass.isAssignableFrom(m.getReturnType())) {
                    return m.invoke(null, combinedId);
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static Object adaptResourceLocation(Object src, Class<?> expected) {
        if (src == null || expected == null) return null;
        if (expected.isInstance(src)) return src;
        String namespace = null;
        String path = null;
        for (String accessor : new String[] {"getNamespace", "method_12836", "method_12836$"}) {
            Object v = call(src, accessor);
            if (v instanceof String s) { namespace = s; break; }
        }
        for (String accessor : new String[] {"getPath", "method_12832"}) {
            Object v = call(src, accessor);
            if (v instanceof String s) { path = s; break; }
        }
        if (namespace == null || path == null) {
            String asString = String.valueOf(src);
            int colon = asString.indexOf(':');
            if (colon > 0) {
                namespace = asString.substring(0, colon);
                path = asString.substring(colon + 1);
            }
        }
        if (namespace == null || path == null) return null;
        Object made = newResourceLocationKnot(expected, namespace, path);
        if (made != null) return made;
        return newResourceLocationKnotWithStringFallback(expected, namespace + ":" + path);
    }

    public static void unfreezeRegistry(Object registry) {
        if (registry == null) return;
        Class<?> c = registry.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() == boolean.class && !Modifier.isStatic(f.getModifiers())) {
                    String n = f.getName();
                    if (n.equals("frozen") || n.equals("field_25103") || n.contains("frozen")) {
                        try {
                            f.setAccessible(true);
                            f.setBoolean(registry, false);
                            dev.stolym.vyne.util.VyneLogger.debug("[ReflectionHelper] Unfroze registry " + registry + " via field " + n);
                        } catch (Throwable ignored) {}
                    }
                }
            }
            c = c.getSuperclass();
        }
    }
}
