/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.client;

import dev.stolym.vyne.util.VyneLogger;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class FabricKeyBindingBridge {
    private FabricKeyBindingBridge() {}

    private static final Set<Object> REGISTERED =
            java.util.Collections.synchronizedSet(
                    java.util.Collections.newSetFromMap(new IdentityHashMap<>()));

    private static final java.util.List<Object> PENDING =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    private static volatile Method REGISTER_METHOD;
    private static volatile boolean resolveAttempted;

    public static void enqueue(Object mapping) {
        if (mapping == null) return;
        PENDING.add(mapping);

        System.out.println("[VYNE-KEYBIND] enqueued mapping (" + mapping.getClass().getName()
                + ", total pending=" + PENDING.size() + ")");
    }

    public static int flushQueued() {
        java.util.List<Object> snapshot;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) return 0;
            snapshot = new java.util.ArrayList<>(PENDING);
            PENDING.clear();
        }
        return registerAll(snapshot);
    }

    public static int flushPending() {
        int size;
        java.util.List<Object> snapshot;
        synchronized (PENDING) {
            size = PENDING.size();
            if (PENDING.isEmpty()) {
                String msg = "[KeyBind] flushPending: nothing queued — "
                        + "RegisterKeyMappingsEvent either wasn't posted, the mod's "
                        + "@EventBusSubscriber handler wasn't wired, or the mod doesn't "
                        + "declare any keys. If you expected keybinds (e.g. Traveler's "
                        + "Backpack 'B' to open), the most common cause is a missing "
                        + "annotation shim — look for a `bytecode declared N` mismatch in "
                        + "[Vyne-Bus] logs above.";
                System.out.println("[VYNE-KEYBIND] " + msg);
                VyneLogger.warn(msg);
                return 0;
            }
            snapshot = new java.util.ArrayList<>(PENDING);
            PENDING.clear();
        }
        System.out.println("[VYNE-KEYBIND] flushPending: bridging " + size
                + " queued mapping(s) to Fabric's KeyBindingHelper.");
        VyneLogger.info("[KeyBind] Flushing " + size
                + " queued keybind(s) to Fabric's KeyBindingHelper.");
        return registerAll(snapshot);
    }

    public static int registerAll(List<Object> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return 0;
        }
        Method m = resolveRegister();
        int count = 0;
        java.util.List<String> bridgedNames = new java.util.ArrayList<>();
        java.util.List<Object> directInjectCandidates = new java.util.ArrayList<>();
        for (Object mapping : mappings) {
            if (mapping == null) continue;
            if (!REGISTERED.add(mapping)) continue;
            boolean ok = false;
            if (m != null) {
                try {
                    m.invoke(null, mapping);
                    ok = true;
                } catch (Throwable t) {
                    VyneLogger.warn("[KeyBind] KeyBindingHelper.registerKeyBinding("
                            + safeName(mapping) + ") threw: " + unwrap(t)
                            + " — falling back to direct injection.");
                }
            }
            if (!ok) {
                directInjectCandidates.add(mapping);
            } else {
                count++;
                bridgedNames.add(safeName(mapping));
            }
        }
        if (!directInjectCandidates.isEmpty()) {
            int direct = directInjectIntoVanilla(directInjectCandidates);
            if (direct > 0) {
                for (Object mapping : directInjectCandidates) {
                    bridgedNames.add(safeName(mapping) + " [direct]");
                }
                count += direct;
                VyneLogger.success("[KeyBind] Direct-injected " + direct
                        + " keybind(s) into KeyMapping.ALL/MAP + Options.keyMappings "
                        + "(Kilt-style fallback because KeyBindingHelper was unavailable "
                        + "or refused the binding).");
            }
        }
        if (count > 0) {
            VyneLogger.success("[KeyBind] Bridged " + count
                    + " Forge/NeoForge keybind(s) to Minecraft: "
                    + String.join(", ", bridgedNames));
        } else {
            VyneLogger.warn("[KeyBind] No keybinds bridged (neither Fabric "
                    + "KeyBindingHelper nor direct-injection path worked). "
                    + "KeyBindingHelper resolved=" + (m != null)
                    + ", candidates=" + mappings.size() + ".");
        }
        return count;
    }

    private static int directInjectIntoVanilla(List<Object> mappings) {
        if (mappings == null || mappings.isEmpty()) return 0;

        ClassLoader cl = resolveGameClassLoader();
        if (cl == null) {
            VyneLogger.warn("[KeyBind] direct-inject: no classloader could be resolved for "
                    + "net.minecraft.client.KeyMapping — aborting.");
            return 0;
        }
        Class<?> keyMappingCls = loadAny(cl,
                "net.minecraft.client.KeyMapping", "net.minecraft.class_304");
        if (keyMappingCls == null) {
            VyneLogger.warn("[KeyBind] direct-inject: KeyMapping class not found via "
                    + safeLoaderName(cl) + " — aborting.");
            return 0;
        }
        Class<?> optionsCls = loadAny(cl,
                "net.minecraft.client.Options", "net.minecraft.class_315");
        Class<?> minecraftCls = loadAny(cl,
                "net.minecraft.client.Minecraft", "net.minecraft.class_310");

        java.util.Map<String, Object> allMap = (java.util.Map<String, Object>) readStaticMap(keyMappingCls, "ALL", "field_1657");
        java.util.Map<Object, Object> keyMap = (java.util.Map<Object, Object>) readStaticMap(keyMappingCls, "MAP", "field_1658");
        java.util.Set<String> categories = (java.util.Set<String>) readStaticSet(keyMappingCls, "CATEGORIES", "field_1659");

        if (allMap == null) {
            VyneLogger.warn("[KeyBind] direct-inject: could not access KeyMapping.ALL — aborting.");
            return 0;
        }

        int injected = 0;
        for (Object mapping : mappings) {
            try {
                if (!keyMappingCls.isInstance(mapping)) continue;
                String name = invokeStringGetter(mapping, "getName", "method_1431");
                String category = invokeStringGetter(mapping, "getCategory", "method_1423");
                Object key = invokeGetter(mapping, "getKey", "method_1428");

                if (name != null && !allMap.containsKey(name)) {
                    allMap.put(name, mapping);
                }
                if (keyMap != null && key != null) {
                    keyMap.putIfAbsent(key, mapping);
                }
                if (categories != null && category != null) {
                    categories.add(category);
                }
                injected++;
            } catch (Throwable t) {
                VyneLogger.warn("[KeyBind] direct-inject: failed to wire "
                        + safeName(mapping) + ": " + unwrap(t));
            }
        }

        if (injected > 0 && optionsCls != null && minecraftCls != null) {
            try {
                Method getInstance = minecraftCls.getMethod("getInstance");
                Object mc = getInstance.invoke(null);
                if (mc != null) {
                    Method optionsGetter = findAnyMethod(minecraftCls,
                            new String[]{"options"}, new Class<?>[0]);
                    Object opts = (optionsGetter != null)
                            ? optionsGetter.invoke(mc)
                            : readField(mc, optionsCls, "options", "field_1690");
                    if (opts != null) {
                        java.lang.reflect.Field kmf = findField(optionsCls,
                                "keyMappings", "field_1839");
                        if (kmf != null) {
                            kmf.setAccessible(true);
                            Object curArr = kmf.get(opts);
                            if (curArr instanceof Object[] existing) {
                                java.util.List<Object> merged = new java.util.ArrayList<>(
                                        java.util.Arrays.asList(existing));
                                for (Object m0 : mappings) {
                                    if (m0 != null && !merged.contains(m0)) {
                                        merged.add(m0);
                                    }
                                }
                                Object newArr = java.lang.reflect.Array.newInstance(
                                        existing.getClass().getComponentType(), merged.size());
                                for (int i = 0; i < merged.size(); i++) {
                                    java.lang.reflect.Array.set(newArr, i, merged.get(i));
                                }
                                try {
                                    kmf.set(opts, newArr);
                                } catch (IllegalAccessException iae) {
                                    setFinalField(kmf, opts, newArr);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                VyneLogger.warn("[KeyBind] direct-inject: failed to extend Options.keyMappings: "
                        + unwrap(t));
            }
        }

        return injected;
    }

    private static ClassLoader resolveGameClassLoader() {
        for (ClassLoader cl : collectCandidateLoaders()) {
            if (cl == null) continue;
            try {
                Class.forName("net.minecraft.client.KeyMapping", false, cl);
                return cl;
            } catch (Throwable ignored) {}
            try {
                Class.forName("net.minecraft.class_304", false, cl);
                return cl;
            } catch (Throwable ignored) {}
        }
        return FabricKeyBindingBridge.class.getClassLoader();
    }

    private static java.util.LinkedHashSet<ClassLoader> collectCandidateLoaders() {
        java.util.LinkedHashSet<ClassLoader> loaders = new java.util.LinkedHashSet<>();
        try {
            ClassLoader knotCl = dev.stolym.vyne.vcl.VCLManager.getCanonicalKnotLoader();
            if (knotCl != null) loaders.add(knotCl);
        } catch (Throwable ignored) {}
        try {
            Class<?> mcKlass = Class.forName("net.minecraft.client.Minecraft",
                    false, ClassLoader.getSystemClassLoader());
            if (mcKlass != null && mcKlass.getClassLoader() != null) {
                loaders.add(mcKlass.getClassLoader());
            }
        } catch (Throwable ignored) {}
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft",
                    false, Thread.currentThread().getContextClassLoader());
            if (mc != null && mc.getClassLoader() != null) {
                loaders.add(mc.getClassLoader());
            }
        } catch (Throwable ignored) {}
        try {
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            if (tccl != null) loaders.add(tccl);
        } catch (Throwable ignored) {}
        ClassLoader selfCl = FabricKeyBindingBridge.class.getClassLoader();
        if (selfCl != null) loaders.add(selfCl);
        ClassLoader sys = ClassLoader.getSystemClassLoader();
        if (sys != null) loaders.add(sys);
        return loaders;
    }

    private static Class<?> loadAny(ClassLoader cl, String... names) {
        for (String n : names) {
            try {
                return Class.forName(n, true, cl);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object readStaticMap(Class<?> owner, String... fieldNames) {
        for (String n : fieldNames) {
            try {
                java.lang.reflect.Field f = owner.getDeclaredField(n);
                f.setAccessible(true);
                Object v = f.get(null);
                if (v instanceof java.util.Map) return v;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object readStaticSet(Class<?> owner, String... fieldNames) {
        for (String n : fieldNames) {
            try {
                java.lang.reflect.Field f = owner.getDeclaredField(n);
                f.setAccessible(true);
                Object v = f.get(null);
                if (v instanceof java.util.Set) return v;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String invokeStringGetter(Object target, String... names) {
        Object v = invokeGetter(target, names);
        return v == null ? null : v.toString();
    }

    private static Object invokeGetter(Object target, String... names) {
        for (String n : names) {
            try {
                Method mm = target.getClass().getMethod(n);
                mm.setAccessible(true);
                return mm.invoke(target);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Method findAnyMethod(Class<?> owner, String[] names, Class<?>[] params) {
        for (String n : names) {
            try {
                return owner.getMethod(n, params);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object readField(Object target, Class<?> declared,
                                    String yarn, String intermediary) {
        try {
            java.lang.reflect.Field f = findField(declared, yarn, intermediary);
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static java.lang.reflect.Field findField(Class<?> owner,
                                                     String yarn, String intermediary) {
        try {
            return owner.getDeclaredField(yarn);
        } catch (NoSuchFieldException e1) {
            try {
                return owner.getDeclaredField(intermediary);
            } catch (NoSuchFieldException e2) {
                return null;
            }
        }
    }

    private static void setFinalField(java.lang.reflect.Field f, Object target, Object value)
            throws Throwable {
        java.lang.invoke.VarHandle vh;
        try {
            java.lang.invoke.MethodHandles.Lookup lk =
                    java.lang.invoke.MethodHandles.privateLookupIn(
                            f.getDeclaringClass(), java.lang.invoke.MethodHandles.lookup());
            vh = lk.findVarHandle(f.getDeclaringClass(), f.getName(), f.getType());
        } catch (Throwable t) {
            throw t;
        }
        vh.set(target, value);
    }

    private static Method resolveRegister() {
        Method cached = REGISTER_METHOD;
        if (cached != null) return cached;
        if (resolveAttempted) return null;
        synchronized (FabricKeyBindingBridge.class) {
            if (REGISTER_METHOD != null) return REGISTER_METHOD;
            if (resolveAttempted) return null;
            resolveAttempted = true;

            java.util.LinkedHashSet<ClassLoader> loaders = collectCandidateLoaders();

            Class<?> helper = null;
            ClassLoader found = null;
            Throwable lastFailure = null;
            for (ClassLoader cl : loaders) {
                try {
                    helper = Class.forName(
                            "net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper",
                            true, cl);
                    found = cl;
                    break;
                } catch (ClassNotFoundException cnf) {
                    lastFailure = cnf;
                } catch (Throwable t) {
                    lastFailure = t;
                }
            }

            if (helper == null) {
                VyneLogger.warn("[KeyBind] fabric-key-binding-api-v1 not visible to any tried "
                        + "classloader (tried " + loaders.size() + " loader(s): "
                        + describeLoaders(loaders) + "). Forge keybinds will fall back to direct "
                        + "Options.keyMappings injection. Last failure: " + lastFailure);
                return null;
            }

            try {
                Class<?> keyMapping = null;
                for (String mcName : new String[]{
                        "net.minecraft.client.KeyMapping",
                        "net.minecraft.class_304"}) {
                    try {
                        keyMapping = Class.forName(mcName, true, found);
                        break;
                    } catch (ClassNotFoundException ignored) {}
                }
                for (Method m : helper.getMethods()) {
                    if (!"registerKeyBinding".equals(m.getName())) continue;
                    if (m.getParameterCount() != 1) continue;
                    Class<?> p0 = m.getParameterTypes()[0];
                    if (keyMapping != null && p0.isAssignableFrom(keyMapping)) {
                        REGISTER_METHOD = m;
                        VyneLogger.info("[KeyBind] Resolved Fabric KeyBindingHelper.registerKeyBinding("
                                + p0.getSimpleName() + ") via " + safeLoaderName(found));
                        return m;
                    }
                    if (keyMapping == null && p0.isAssignableFrom(Object.class)) {
                        REGISTER_METHOD = m;
                        return m;
                    }
                }
                VyneLogger.warn("[KeyBind] KeyBindingHelper found but no registerKeyBinding(KeyMapping) method.");
            } catch (Throwable t) {
                VyneLogger.warn("[KeyBind] Unexpected failure resolving KeyBindingHelper methods: "
                        + unwrap(t));
            }
            return null;
        }
    }

    private static String safeLoaderName(ClassLoader cl) {
        if (cl == null) return "null";
        String n = cl.getClass().getSimpleName();
        if (n == null || n.isEmpty()) n = cl.getClass().getName();
        return n + "@" + Integer.toHexString(System.identityHashCode(cl));
    }

    private static String describeLoaders(java.util.Collection<ClassLoader> loaders) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (ClassLoader cl : loaders) {
            if (!first) sb.append(", ");
            sb.append(safeLoaderName(cl));
            first = false;
        }
        return sb.toString();
    }

    private static String safeName(Object mapping) {
        if (mapping == null) return "<null>";
        try {
            Method getName = mapping.getClass().getMethod("getName");
            Object v = getName.invoke(mapping);
            return v == null ? "<unnamed>" : v.toString();
        } catch (Throwable t) {
            return mapping.getClass().getSimpleName();
        }
    }

    private static Throwable unwrap(Throwable t) {
        while (t instanceof java.lang.reflect.InvocationTargetException ite
                && ite.getCause() != null) {
            t = ite.getCause();
        }
        return t;
    }
}
