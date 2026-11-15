/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.registries;

import dev.stolym.vyne.mappings.MappingRegistry;
import dev.stolym.vyne.registry.RegistrationDiagnostics;
import dev.stolym.vyne.util.ReflectionHelper;
import dev.stolym.vyne.util.VyneLogger;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.class_5321;
import java.util.function.Supplier;
import java.util.function.Predicate;

public class DeferredHolder<T> implements Supplier<T>, Holder<T> {
    private final ResourceKey<T> elementKey;
    private final ResourceLocation loc;
    private final Supplier<T> supplier;
    private T value;
    private static final java.util.Set<String> REPORTED_FAILURE_KEYS =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private static String failureThrottleKey(String reg, Throwable root) {
        String msg = root.getMessage();
        if (msg != null) {
            msg = msg.replaceAll("[a-z0-9_.-]+:[a-z0-9_/._-]+", "<id>");
        } else {
            msg = "";
        }
        return reg + "|" + root.getClass().getName() + "|" + msg;
    }

    @SuppressWarnings("unchecked")
    public static <T> DeferredHolder<T> create(ResourceKey<?> elementKey) {
        return new DeferredHolder<>((ResourceKey<T>) elementKey);
    }

    @SuppressWarnings("unchecked")
    public static <T> DeferredHolder<T> create(ResourceKey<?> registryKey, ResourceLocation id) {
        if (id == null) {
            return new DeferredHolder<>((ResourceKey<T>) registryKey);
        }
        return new DeferredHolder<>(registryKey, id.getNamespace(), id.getPath(), null);
    }

    public DeferredHolder(Object registryOrElementKey, String namespace, String name, Supplier<T> supplier) {
        this.loc = ResourceLocation.fromNamespaceAndPath(namespace, name);
        ResourceKey<? extends Registry<T>> regKey = resolveRegistryKey(registryOrElementKey);
        if (regKey == null && this.loc != null) {
            regKey = inferRegistryKeyWhenHolder(registryOrElementKey, this.loc);
        }
        this.elementKey = regKey != null && this.loc != null
            ? ResourceKey.create(regKey, this.loc)
            : null;
        this.supplier = supplier;
    }

    @SuppressWarnings("unchecked")
    public DeferredHolder(ResourceKey<?> elementKeyIn) {
        this.elementKey = (ResourceKey<T>) elementKeyIn;
        ResourceLocation derived = null;
        if (elementKeyIn != null) {
            Object v = ReflectionHelper.call(elementKeyIn, "location", "method_29177");
            if (v instanceof ResourceLocation rl) {
                derived = rl;
            } else if (v != null) {
                Object adapted = ReflectionHelper.adaptResourceLocation(
                    v, ResourceLocation.class);
                if (adapted instanceof ResourceLocation rl2) {
                    derived = rl2;
                }
            }
        }
        this.loc = derived;
        this.supplier = null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get() {
        if (value != null) return value;

        T result = null;
        if (supplier != null) {
            ClassLoader supplierCl = supplier.getClass().getClassLoader();
            ClassLoader prev = Thread.currentThread().getContextClassLoader();
            try {
                if (supplierCl != null) {
                    Thread.currentThread().setContextClassLoader(supplierCl);
                    prelinkMappedGameFacades(supplierCl);
                }
                result = supplier.get();
            } catch (Throwable t) {
                handleSupplierError(t, supplierCl);
            } finally {
                Thread.currentThread().setContextClassLoader(prev);
            }
        }

        if (result == null && elementKey != null) {
            result = (T) tryLookupInRegistry();
        }

        if (result == null) {
            result = itemAirFallback();
        }

        if (result != null) {
            this.value = result;
        }
        return result;
    }

    protected final void bind(boolean throwOnMissingRegistry) {
        get();
        if (value == null && throwOnMissingRegistry) {
            throw new IllegalStateException("Registry not present for " + this + ": " + (elementKey != null ? elementKey.registry() : "unknown"));
        }
    }

    private void handleSupplierError(Throwable t, ClassLoader supplierCl) {
        RegistrationDiagnostics.recordDeferredSupplierFailure(elementKey, loc, t);
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String reg = registryPathForDiag();
        String failKey = failureThrottleKey(reg, root);
        if (REPORTED_FAILURE_KEYS.add(failKey)) {
            VyneLogger.warn("[DeferredHolder] supplier.get() failed for "
                + loc + " registry=" + reg
                + " root=" + root.getClass().getSimpleName()
                + " msg=" + (root.getMessage() == null ? "(no message)" : root.getMessage()));
            VyneLogger.warn("[DeferredHolder] supplierClass=" + (supplier == null ? "null" : supplier.getClass().getName())
                + " loader=" + (supplierCl == null ? "null" : supplierCl.toString()));

            Throwable walker = t;
            int depth = 0;
            java.util.Set<Throwable> seen = new java.util.HashSet<>();
            while (walker != null && seen.add(walker) && depth < 8) {
                VyneLogger.warn("[DeferredHolder]   cause[" + depth + "]: "
                    + walker.getClass().getName()
                    + ": " + (walker.getMessage() == null ? "(no message)" : walker.getMessage()));
                walker = walker.getCause();
                depth++;
            }
        }
    }

    private Object tryLookupInRegistry() {
        if (elementKey == null) return null;
        try {
            ClassLoader cl = knotTargetClassLoader();
            if (cl == null) cl = DeferredHolder.class.getClassLoader();

            String path = registryPathForDiag();
            Object reg = dev.stolym.vyne.registry.VyneRegistryInjector.resolveRegistryForPath(cl, path);
            if (reg == null) {
                VyneLogger.debug("[DeferredHolder] Registry not found for " + path + " while resolving " + loc);
                return null;
            }

            Object rl = dev.stolym.vyne.registry.VyneRegistryInjector.toResourceLocation(cl, loc.toString());
            if (rl == null) return null;

            java.lang.reflect.Method getMethod = null;
            for (java.lang.reflect.Method m : reg.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && (m.getName().equals("get") || m.getName().equals("method_10223"))) {
                    getMethod = m;
                    break;
                }
            }
            if (getMethod != null) {
                Object res = getMethod.invoke(reg, rl);
                if (res != null) {
                    if (path.contains("block") && 
                        dev.stolym.vyne.registry.RegistryMirrorUtil.isVanillaAirBlock(res, cl)) {
                        return null;
                    }
                    return res;
                }
            }
            VyneLogger.debug("[DeferredHolder] Entry " + loc + " not found in live registry " + path);
        } catch (Throwable t) {
            VyneLogger.debug("[DeferredHolder] Exception resolving " + loc + " in " + registryPathForDiag() + ": " + t.getMessage());
        }
        return null;
    }

    private static volatile Object CACHED_AIR_ITEM;

    @SuppressWarnings("unchecked")
    private T itemAirFallback() {
        String reg = registryPathForDiag();
        if (reg == null || !reg.endsWith("minecraft:item")) {
            return null;
        }
        if (loc == null || !"minecraft".equals(loc.getNamespace()) || !"air".equals(loc.getPath())) {
            return null;
        }
        Object cached = CACHED_AIR_ITEM;
        if (cached != null) {
            return (T) cached;
        }
        ClassLoader cl = knotTargetClassLoader();
        if (cl == null) {
            cl = DeferredHolder.class.getClassLoader();
        }
        try {
            Class<?> itemsClass = Class.forName("net.minecraft.class_1802", true, cl);
            java.lang.reflect.Field airField = itemsClass.getDeclaredField("field_8162");
            airField.setAccessible(true);
            Object air = airField.get(null);
            if (air != null) {
                CACHED_AIR_ITEM = air;
                return (T) air;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String registryPathForDiag() {
        if (elementKey != null) {
            try {
                return elementKey.registry().getNamespace() + ":" + elementKey.registry().getPath();
            } catch (Throwable ignored) {
            }
        }
        return ReflectionHelper.extractRegistryKeyLocation(elementKey);
    }

    @SuppressWarnings("unchecked")
    private static <T> ResourceKey<? extends Registry<T>> resolveRegistryKey(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof ResourceKey<?> rk) {
            return (ResourceKey<? extends Registry<T>>) rk;
        }
        if (o instanceof Registry<?> reg) {
            return (ResourceKey<? extends Registry<T>>) (Object) reg.key();
        }
        if (o instanceof ResourceLocation rl) {
            return (ResourceKey<? extends Registry<T>>) (Object) ResourceKey.createRegistryKey(rl);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> ResourceKey<? extends Registry<T>> inferRegistryKeyWhenHolder(
            Object raw, ResourceLocation loc) {
        if (loc == null) {
            return null;
        }
        if (raw == null && "neoforge".equals(loc.getNamespace()) && "empty".equals(loc.getPath())) {
            return (ResourceKey<? extends Registry<T>>) (Object) ResourceKey.createRegistryKey(
                ResourceLocation.fromNamespaceAndPath("neoforge", "fluid_type"));
        }
        return null;
    }

    private static final String[] FORCED_FACADES_INTER = {
        "net.minecraft.class_1761", 
        "net.minecraft.class_3917", 
        "net.minecraft.class_9139", 
        "net.minecraft.class_9143", 
        "net.minecraft.class_9141", 
        "net.minecraft.class_2960", 
    };

    private static void prelinkMappedGameFacades(ClassLoader cl) {
        if (cl == null) {
            return;
        }
        if (MappingRegistry.isReady()) {
            String[] mojSlash = {
                "net/minecraft/world/item/CreativeModeTab",
                "net/minecraft/world/inventory/MenuType",
                "net/minecraft/network/codec/PacketCodec",
            };
            for (String path : mojSlash) {
                try {
                    String inter = MappingRegistry.get().mapClass(path);
                    if (inter != null && !inter.isEmpty()) {
                        linkFacadeOnMcLoaders(inter.replace('/', '.'), cl);
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        for (String dotName : FORCED_FACADES_INTER) {
            linkFacadeOnMcLoaders(dotName, cl);
        }
    }

    private static final java.util.Set<String> LOGGED_PRELINK_MISSES =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private static void linkFacadeOnMcLoaders(String dotName, ClassLoader primary) {
        java.util.LinkedHashSet<ClassLoader> loaders = new java.util.LinkedHashSet<>();
        ClassLoader knotTarget = knotTargetClassLoader();
        if (knotTarget != null) {
            loaders.add(knotTarget);
        }
        if (primary != null) {
            for (ClassLoader w = primary; w != null; w = w.getParent()) {
                loaders.add(w);
            }
        }
        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
        if (ctx != null) {
            loaders.add(ctx);
        }
        loaders.add(DeferredHolder.class.getClassLoader());
        Throwable last = null;
        for (ClassLoader c : loaders) {
            if (c == null) {
                continue;
            }
            try {
                Class.forName(dotName, false, c);
                return;
            } catch (Throwable t) {
                last = t;
            }
        }
        if (last != null && LOGGED_PRELINK_MISSES.add(dotName)) {
            VyneLogger.debug("[DeferredHolder] prelink missed " + dotName + " on all loaders → "
                + last.getClass().getSimpleName() + ": " + last.getMessage());
        }
    }

    private static ClassLoader knotTargetClassLoader() {
        ClassLoader t = fabricLauncherTargetClassLoader();
        if (t != null) {
            return t;
        }
        try {
            Object game = FabricLoader.getInstance().getGameInstance();
            if (game != null) {
                ClassLoader gl = game.getClass().getClassLoader();
                if (gl != null) {
                    return gl;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static ClassLoader fabricLauncherTargetClassLoader() {
        try {
            Class<?> flb = Class.forName("net.fabricmc.loader.impl.launch.FabricLauncherBase");
            Object launcher = flb.getMethod("getLauncher").invoke(null);
            if (launcher == null) {
                return null;
            }
            Object tcl = launcher.getClass().getMethod("getTargetClassLoader").invoke(launcher);
            return tcl instanceof ClassLoader cl ? cl : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public T value() {
        bind(true);
        if (value == null) {
            throw new NullPointerException("Trying to access unbound value: " + (elementKey != null ? elementKey : "unknown"));
        }
        return value;
    }

    public T value_() { return get(); }

    @Override
    public boolean is(Predicate<T> predicate) { return predicate.test(value()); }

    @Override
    public boolean is(ResourceKey<T> key) { return false; }

    public net.minecraft.resources.ResourceKey<T> getKey() {
        return elementKey;
    }

    @Override
    @SuppressWarnings("unchecked")
    public java.util.Optional<class_5321<T>> method_40230() {
        if (elementKey == null) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of((class_5321<T>) elementKey);
        } catch (ClassCastException e) {
            return java.util.Optional.empty();
        }
    }

    public ResourceLocation getId() { return loc; }

    public boolean isBound() {
        return value != null;
    }
}
