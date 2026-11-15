/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.registries;

import dev.stolym.vyne.fluid.VyneFluidTypeRegistry;
import dev.stolym.vyne.fluid.VyneFluidRegistry;
import dev.stolym.vyne.registry.RegistryMirrorUtil;
import dev.stolym.vyne.registry.VyneGenericRegistry;
import dev.stolym.vyne.registry.VyneRegistryInjector;
import dev.stolym.vyne.vcl.VCLFieldPopulator;
import dev.stolym.vyne.util.VyneLogLevel;
import dev.stolym.vyne.util.VyneLogger;
import dev.stolym.vyne.util.ReflectionHelper;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

public class RegisterEvent {
    private final Object registryKey;
    private final Object liveRegistry;
    private final ResourceKey<?> exposedRegistryKey;

    public RegisterEvent(Object registryKey) {
        this.registryKey = registryKey;
        this.liveRegistry = null;
        this.exposedRegistryKey = adaptRegistryKey(registryKey);
    }

    public RegisterEvent(Object registryKey, Object liveRegistry) {
        this.registryKey = registryKey;
        this.liveRegistry = liveRegistry;
        this.exposedRegistryKey = adaptRegistryKey(registryKey);
    }

    @SuppressWarnings("unchecked")
    public ResourceKey<?> getRegistryKey() {
        return exposedRegistryKey;
    }

    private static ResourceKey<?> adaptRegistryKey(Object key) {
        if (key instanceof ResourceKey<?> rk) {
            return rk;
        }
        String loc = ReflectionHelper.extractRegistryKeyLocation(key);
        if (loc != null && !loc.isBlank() && !"null".equals(loc)) {
            try {
                return ResourceKey.createRegistryKey(toResourceLocation(loc));
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static ResourceLocation toResourceLocation(String id) {
        String normalized = id.replace('\\', '/');
        int colon = normalized.indexOf(':');
        if (colon > 0) {
            return new ResourceLocation(normalized.substring(0, colon), normalized.substring(colon + 1));
        }
        int slash = normalized.indexOf('/');
        if (slash > 0) {
            return new ResourceLocation(normalized.substring(0, slash), normalized.substring(slash + 1));
        }
        return new ResourceLocation("minecraft", normalized);
    }

    public Object getRegistry() {
        return liveRegistry;
    }

    @SuppressWarnings("unchecked")
    public Registry<?> method_14959() {
        return (Registry<?>) liveRegistry;
    }

    public void register(Object key, ResourceLocation name, Supplier<?> supplier) {
        if (key == null || name == null) return;

        String keyLoc = ReflectionHelper.extractRegistryKeyLocation(key);
        String myLoc = ReflectionHelper.extractRegistryKeyLocation(this.registryKey);

        if (keyLoc == null || myLoc == null || !ReflectionHelper.registryKeyLocationsMatch(keyLoc, myLoc)) {
            return;
        }

        Object value = supplier.get();
        if (value == null) return;

        new RegisterHelperImpl(registryKey).register(name, value);
    }

    public void register(Object key, Object name, Supplier<?> supplier) {
        if (name instanceof ResourceLocation rl) {
            register(key, rl, supplier);
            return;
        }
        ResourceLocation rl = toMojmapResourceLocation(name);
        if (rl != null) {
            register(key, rl, supplier);
        }
    }

    private static ResourceLocation toMojmapResourceLocation(Object name) {
        if (name instanceof ResourceLocation already) {
            return already;
        }
        String ns = tryRlStringGetter(name, "getNamespace", "method_12836");
        String path = tryRlStringGetter(name, "getPath", "method_12832");
        if (ns != null && path != null) {
            return new ResourceLocation(ns, path);
        }
        String asString = String.valueOf(name);
        int colon = asString.indexOf(':');
        if (colon > 0 || asString.indexOf('/') > 0) {
            return toResourceLocation(asString);
        }
        return null;
    }

    private static String tryRlStringGetter(Object o, String... methodNames) {
        for (String n : methodNames) {
            Object v = ReflectionHelper.call(o, n);
            if (v instanceof String s) {
                return s;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public void register(Object key, java.util.function.Consumer<RegisterHelper<Object>> consumer) {
        if (key == null) return;
        String keyLoc = ReflectionHelper.extractRegistryKeyLocation(key);
        String myLoc = ReflectionHelper.extractRegistryKeyLocation(this.registryKey);
        if (keyLoc == null || myLoc == null || !ReflectionHelper.registryKeyLocationsMatch(keyLoc, myLoc)) return;
        consumer.accept(new RegisterHelperImpl(registryKey));
    }

    public interface RegisterHelper<T> {
        default void register(ResourceKey<T> key, T value) {
            register(key.location(), value);
        }

        void register(ResourceLocation id, T value);
    }

    private final class RegisterHelperImpl implements RegisterHelper<Object> {
        private final Object registryKey;

        private RegisterHelperImpl(Object registryKey) {
            this.registryKey = registryKey;
        }

        @Override
        public void register(ResourceLocation id, Object value) {
            VyneLogger.debug("[RegisterHelper] register called: id=" + id + " value=" + value + " key=" + registryKey);
            if (id == null) return;

            if (isFluidTypeKey(registryKey)) {
                VyneFluidTypeRegistry.register(id, value);
                net.minecraft.resources.ResourceKey<net.neoforged.neoforge.fluids.FluidType> rk =
                    net.minecraft.resources.ResourceKey.create(
                        (net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<net.neoforged.neoforge.fluids.FluidType>>) (Object) net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.FLUID_TYPES, 
                        id);
                if (net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_TYPES instanceof net.neoforged.neoforge.registries.NeoForgeRegistries.VyneFluidTypeRegistryShim shim) {
                    shim.method_47984(null, rk, value);
                }
                VyneLogger.debug("[VCL-Shim] FluidType registered: " + id);
                return;
            }

            if (isFluidKey(registryKey)) {
                VyneFluidRegistry.record(id.toString(), value);
                VyneLogger.debug("[VCL-Shim] Fluid recorded: " + id);
                injectIntoLiveFluidRegistry(id, value);
                return;
            }

            String myLoc = ReflectionHelper.extractRegistryKeyLocation(registryKey);
            if (myLoc == null) {
                return;
            }
            String norm = ReflectionHelper.normalizeRegistryKeyLocationString(myLoc);
            try {
                ClassLoader knotCl = Thread.currentThread().getContextClassLoader();
                VyneGenericRegistry.register(norm, id.toString(), value);
                RegistryMirrorUtil.mirrorBlockItemToBlockRegistryIfNeeded(norm, id, value);
                VCLFieldPopulator.onLiveRegistryEntryInjected(norm, id.toString(), value, knotCl);
            } catch (Throwable ignored) {
            }

            if (RegisterEvent.this.liveRegistry != null) {
                try {
                    ClassLoader knotCl = Thread.currentThread().getContextClassLoader();
                    String rlClassName = dev.stolym.vyne.mappings.MappingRegistry.isReady()
                        ? dev.stolym.vyne.mappings.MappingRegistry.get()
                            .mapClass("net/minecraft/resources/ResourceLocation").replace('/', '.')
                        : "net.minecraft.class_2960";
                    Class<?> rlClass = Class.forName(rlClassName, true, knotCl);

                    Object liveRl = ReflectionHelper.newResourceLocationKnot(
                        rlClass, id.getNamespace(), id.getPath());
                    if (liveRl == null) {
                        liveRl = ReflectionHelper.newResourceLocationKnotWithStringFallback(
                            rlClass, id.toString());
                    }

                    if (liveRl != null) {
                        boolean injected = VyneRegistryInjector.injectIntoLiveRegistry(
                            RegisterEvent.this.liveRegistry, liveRl, value);
                        if (injected) {
                            VyneLogger.debug("[VCL-Shim] ✓ Registered " + id + " into live registry " + myLoc);
                        } else if ("minecraft:block".equals(myLoc) && VyneLogLevel.isVerbose()) {
                            VyneLogger.warn("[VCL-Shim] injectIntoLiveRegistry returned false for " + id
                                + " valueClass=" + value.getClass().getName());
                        }
                    } else if (VyneLogLevel.isVerbose()) {
                        VyneLogger.warn("[RegisterEvent] Could not build knot ResourceLocation for " + id);
                    }
                } catch (Throwable t) {
                    Throwable rc = t;
                    while (rc != null && rc.getCause() != null && rc.getCause() != rc) {
                        rc = rc.getCause();
                    }
                    String msg = rc != null ? String.valueOf(rc.getMessage()) : "";
                    if (rc instanceof AssertionError && msg != null && msg.contains("Missing intrusive holder")) {
                        VyneLogger.debug("[VCL-Shim] Skipped live-write for " + id
                            + " (no intrusive holder — already registered via VyneRegistryInjector)");
                    } else {
                        VyneLogger.error("[VCL-Shim] Live registry write failed for " + id + " in " + myLoc, t);
                    }
                }
            }
        }

        private static void injectIntoLiveFluidRegistry(ResourceLocation id, Object fluidValue) {
            try {
                ClassLoader knotCl = Thread.currentThread().getContextClassLoader();
                String builtInName = dev.stolym.vyne.mappings.MappingRegistry.isReady()
                    ? dev.stolym.vyne.mappings.MappingRegistry.get().mapClass("net/minecraft/core/registries/BuiltInRegistries").replace('/', '.')
                    : "net.minecraft.class_7923";
                Class<?> builtIn = Class.forName(builtInName, true, knotCl);

                Object fluidRegistry = null;
                for (java.lang.reflect.Field f : builtIn.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    Object candidate = null;
                    try { candidate = f.get(null); } catch (Throwable ignored) { continue; }
                    if (candidate == null) continue;
                    try {
                        java.lang.reflect.Method keyMethod = candidate.getClass().getMethod("key");
                        Object regKey = keyMethod.invoke(candidate);
                        if (regKey != null && regKey.toString().contains("minecraft:fluid")) {
                            fluidRegistry = candidate;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }

                if (fluidRegistry == null) return;

                String rlClassName = dev.stolym.vyne.mappings.MappingRegistry.isReady()
                    ? dev.stolym.vyne.mappings.MappingRegistry.get().mapClass("net/minecraft/resources/ResourceLocation").replace('/', '.')
                    : "net.minecraft.class_2960";
                Class<?> rlClass = Class.forName(rlClassName, true, knotCl);

                Object liveRl = ReflectionHelper.newResourceLocationKnot(
                    rlClass, id.getNamespace(), id.getPath());
                if (liveRl == null) {
                    liveRl = ReflectionHelper.newResourceLocationKnotWithStringFallback(
                        rlClass, id.toString());
                }

                if (liveRl != null && VyneRegistryInjector.injectIntoLiveRegistry(fluidRegistry, liveRl, fluidValue)) {
                    VyneLogger.debug("[VCL-Shim] ✓ Injected fluid " + id + " into live registry");
                }
            } catch (Throwable ignored) {}
        }

        private static boolean isFluidTypeKey(Object key) {
            return matchesLocation(key, "neoforge:fluid_type");
        }

        private static boolean isFluidKey(Object key) {
            return matchesLocation(key, "minecraft:fluid");
        }

        private static boolean matchesLocation(Object key, String expected) {
            if (key == null) {
                return false;
            }
            String loc = ReflectionHelper.extractRegistryKeyLocation(key);
            return expected.equals(loc);
        }
    }
}
