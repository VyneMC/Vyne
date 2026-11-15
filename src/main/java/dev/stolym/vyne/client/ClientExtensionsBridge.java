/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.client;

import dev.stolym.vyne.util.VyneLogger;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientExtensionsBridge {
    private static final java.util.Set<String> BEWLR_RESOLVE_WARNED = ConcurrentHashMap.newKeySet();

    private static final String REGISTRY_CLASS =
            "net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry";
    private static final String DYNAMIC_RENDERER_CLASS =
            "net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry$DynamicItemRenderer";

    private ClientExtensionsBridge() {}

    public static void apply(RegisterClientExtensionsEvent event) {
        int installed = 0;
        int skipped = 0;
        int groups = 0;

        Bridge bridge = Bridge.resolveOrNull();
        if (bridge == null) {
            VyneLogger.warn("[ClientExtensions] Fabric BuiltinItemRendererRegistry not available — "
                    + "3D item renderers will not be wired this launch.");
            return;
        }

        if (event != null) {
            Map<IClientItemExtensions, List<Object>> registrations = event.snapshotItemRegistrations();
            groups = registrations.size();
            VyneLogger.info("[ClientExtensions] RegisterClientExtensionsEvent dispatched — "
                    + groups + " extension group(s) registered by mods (NeoForge-style).");
            for (Map.Entry<IClientItemExtensions, List<Object>> entry : registrations.entrySet()) {
                int[] result = wireExtensionGroup(bridge, entry.getKey(), entry.getValue());
                installed += result[0];
                skipped += result[1];
            }
        }

        int forgeItemsScanned = 0;
        int forgeExtensionsFound = 0;
        int forgeInstalled = 0;
        try {
            java.util.List<Object[]> forgeExtensions = collectForgeInitializeClientExtensions();
            forgeItemsScanned = forgeExtensionsScanned;
            forgeExtensionsFound = forgeExtensions.size();
            for (Object[] pair : forgeExtensions) {
                Object item = pair[0];
                Object extension = pair[1];
                int[] result = wireExtensionGroup(bridge, extension, java.util.Collections.singletonList(item));
                forgeInstalled += result[0];
                skipped += result[1];
            }
            if (forgeExtensionsFound > 0) {
                VyneLogger.info("[ClientExtensions] Item.initializeClient() walk: scanned "
                        + forgeItemsScanned + " item(s), captured " + forgeExtensionsFound
                        + " extension(s) (Forge-style).");
            } else if (VyneLogLevel_isVerbose()) {
                VyneLogger.debug("[ClientExtensions] Item.initializeClient() walk: "
                        + forgeItemsScanned + " item(s) scanned, none override initializeClient().");
            }
        } catch (Throwable t) {
            VyneLogger.warn("[ClientExtensions] Item.initializeClient() walk failed: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        installed += forgeInstalled;

        if (groups == 0 && forgeExtensionsFound == 0) {
            VyneLogger.warn("[ClientExtensions] No mods registered IClientItemExtensions via either "
                    + "RegisterClientExtensionsEvent (NeoForge) or Item.initializeClient (Forge). "
                    + "3D item renderers will fall back to vanilla sprites. Enable "
                    + "-Dvyne.verbose=true for more.");
        } else {
            VyneLogger.info("[ClientExtensions] 3D item renderers wired: "
                    + installed + " installed, " + skipped + " skipped "
                    + "(NeoForge groups=" + groups + ", Forge extensions=" + forgeExtensionsFound + ").");
        }
    }

    private static boolean VyneLogLevel_isVerbose() {
        try { return dev.stolym.vyne.util.VyneLogLevel.isVerbose(); }
        catch (Throwable ignored) { return false; }
    }

    private static int[] wireExtensionGroup(Bridge bridge, Object extensions, List<Object> rawItems) {
        if (extensions == null || rawItems == null || rawItems.isEmpty()) {
            return new int[]{0, rawItems == null ? 0 : rawItems.size()};
        }
        Object bewlr;
        try {
            bewlr = invokeNoArg(extensions, "getCustomRenderer");
        } catch (Throwable t) {
            VyneLogger.warn("[ClientExtensions] Extension.getCustomRenderer() threw "
                    + t.getClass().getSimpleName() + " — skipping group");
            return new int[]{0, rawItems.size()};
        }
        if (bewlr == null) {
            return new int[]{0, rawItems.size()};
        }

        Method renderMethod = resolveRenderByItem(bewlr.getClass());
        if (renderMethod == null) {
            String key = bewlr.getClass().getName();
            if (BEWLR_RESOLVE_WARNED.add(key)) {
                VyneLogger.warn("[ClientExtensions] No item render method (6-param BEWLR shape) on "
                    + key + " — skipping (log once per class).");
            }
            return new int[]{0, rawItems.size()};
        }

        Object proxy;
        try {
            proxy = Proxy.newProxyInstance(
                    bridge.rendererCls.getClassLoader(),
                    new Class<?>[]{bridge.rendererCls},
                    (p, method, args) -> {
                        if (!"render".equals(method.getName())) {
                            return defaultReturnFor(method.getReturnType());
                        }
                        try { renderMethod.setAccessible(true); } catch (Throwable ignored) {}
                        return renderMethod.invoke(bewlr, args);
                    });
        } catch (Throwable t) {
            VyneLogger.warn("[ClientExtensions] Proxy creation failed: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
            return new int[]{0, rawItems.size()};
        }

        int installed = 0;
        int skipped = 0;
        for (Object rawItem : rawItems) {
            Object item = unwrapItemLike(rawItem);
            if (item == null) {
                skipped++;
                continue;
            }
            try {
                bridge.registerMethod.invoke(bridge.registryInstance, item, proxy);
                installed++;
            } catch (Throwable t) {
                VyneLogger.warn("[ClientExtensions] register() threw for "
                        + item.getClass().getName() + ": " + t.getClass().getSimpleName());
                skipped++;
            }
        }
        return new int[]{installed, skipped};
    }

    private static volatile int forgeExtensionsScanned = 0;

    private static java.util.List<Object[]> collectForgeInitializeClientExtensions() {
        java.util.List<Object[]> out = new java.util.ArrayList<>();
        forgeExtensionsScanned = 0;

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Class<?> regClass;
        try {
            regClass = Class.forName("net.minecraft.class_7923", false, cl);
        } catch (Throwable e1) {
            try {
                regClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries", false, cl);
            } catch (Throwable e2) {
                return out;
            }
        }
        Object itemRegistry = null;
        for (Field f : regClass.getFields()) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            String fname = f.getName();
            if (!fname.equals("ITEM") && !fname.equals("field_41178")) continue;
            try {
                itemRegistry = f.get(null);
                if (itemRegistry != null) break;
            } catch (Throwable ignored) {}
        }
        if (itemRegistry == null) return out;

        Iterable<?> iterable = asIterable(itemRegistry);
        if (iterable == null) return out;

        for (Object item : iterable) {
            if (item == null) continue;
            forgeExtensionsScanned++;
            Method initializeClient = findInitializeClient(item.getClass());
            if (initializeClient == null) continue;

            java.util.List<Object> captured = new java.util.ArrayList<>();
            final Object itemRef = item;
            java.util.function.Consumer<Object> consumer = ext -> {
                if (ext != null) captured.add(ext);
            };
            try {
                initializeClient.setAccessible(true);
                initializeClient.invoke(item, consumer);
            } catch (Throwable t) {
                if (VyneLogLevel_isVerbose()) {
                    VyneLogger.debug("[ClientExtensions] initializeClient() threw for "
                            + item.getClass().getName() + ": " + t);
                }
                continue;
            }
            for (Object ext : captured) {
                out.add(new Object[]{itemRef, ext});
            }
        }
        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Iterable<?> asIterable(Object registry) {
        if (registry instanceof Iterable<?> it) return it;
        for (String m : new String[]{"stream", "holders", "entrySet"}) {
            Object r = invokeNoArg(registry, m);
            if (r instanceof Iterable<?> it) return it;
            if (r instanceof java.util.stream.Stream) {
                final java.util.stream.Stream s = (java.util.stream.Stream) r;
                return (Iterable) () -> s.iterator();
            }
        }
        return null;
    }

    private static Method findInitializeClient(Class<?> itemCls) {
        Class<?> c = itemCls;
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                if (!"initializeClient".equals(m.getName())) continue;
                if (m.getParameterCount() != 1) continue;
                if (m.getParameterTypes()[0] != java.util.function.Consumer.class) continue;
                return m;
            }
            try { c = c.getSuperclass(); } catch (Throwable t) { return null; }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null) return null;
        Class<?> c = target.getClass();
        while (c != null && c != Object.class) {
            try {
                Method m = c.getDeclaredMethod(name);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable t) {
                return null;
            }
            c = c.getSuperclass();
        }
        try {
            Method m = target.getClass().getMethod(name);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class Bridge {
        final Class<?> registryCls;
        final Class<?> rendererCls;
        final Object registryInstance;
        final Method registerMethod;

        Bridge(Class<?> registryCls, Class<?> rendererCls,
               Object registryInstance, Method registerMethod) {
            this.registryCls = registryCls;
            this.rendererCls = rendererCls;
            this.registryInstance = registryInstance;
            this.registerMethod = registerMethod;
        }

        static Bridge resolveOrNull() {
            Class<?> registryCls;
            Class<?> rendererCls;
            try {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                registryCls = Class.forName(REGISTRY_CLASS, false, cl);
                rendererCls = Class.forName(DYNAMIC_RENDERER_CLASS, false, cl);
            } catch (Throwable t) {
                return null;
            }
            try {
                Field instanceField = registryCls.getField("INSTANCE");
                Object registryInstance = instanceField.get(null);
                Method registerMethod = registryCls.getMethod("register",
                        Class.forName("net.minecraft.class_1935", false, registryCls.getClassLoader()),
                        rendererCls);
                return new Bridge(registryCls, rendererCls, registryInstance, registerMethod);
            } catch (Throwable t) {
                return null;
            }
        }
    }

    private static Method resolveRenderByItem(Class<?> rendererClass) {
        Method named = findBewlrItemRenderMethod(rendererClass, true);
        if (named != null) {
            return named;
        }
        return findBewlrItemRenderMethod(rendererClass, false);
    }

    private static Method findBewlrItemRenderMethod(Class<?> rendererClass, boolean requireKnownName) {
        Class<?> c = rendererClass;
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() != 6) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if (!isIntLike(p[4]) || !isIntLike(p[5])) {
                    continue;
                }
                if (!looksLikeItemStackParam(p[0])) {
                    continue;
                }
                String n = m.getName();
                boolean known = n.equals("renderByItem") || n.equals("render") || n.startsWith("method_");
                if (requireKnownName && !known) {
                    continue;
                }
                return m;
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static boolean isIntLike(Class<?> t) {
        return t == int.class || t == Integer.class;
    }

    private static boolean looksLikeItemStackParam(Class<?> t) {
        if (t == null) {
            return false;
        }
        String n = t.getName();
        return n.contains("ItemStack") || n.contains("class_1799");
    }

    private static Object unwrapItemLike(Object raw) {
        Object o = raw;
        for (int i = 0; i < 4 && o != null; i++) {
            Class<?> cls = o.getClass();
            String name = cls.getName();
            if (name.startsWith("net.minecraft.")) return o;
            Object next = tryInvoke(o, "asItem");
            if (next != null && next != o) { o = next; continue; }
            next = tryInvoke(o, "get");
            if (next != null && next != o) { o = next; continue; }
            next = tryInvoke(o, "value");
            if (next != null && next != o) { o = next; continue; }
            break;
        }
        return o;
    }

    private static Object tryInvoke(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object defaultReturnFor(Class<?> t) {
        if (!t.isPrimitive()) return null;
        if (t == boolean.class) return Boolean.FALSE;
        if (t == void.class) return null;
        return 0;
    }
}
