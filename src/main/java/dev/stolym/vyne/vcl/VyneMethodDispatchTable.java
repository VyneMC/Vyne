/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl;

import dev.stolym.vyne.util.VyneLogger;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public final class VyneMethodDispatchTable {
    public record Dispatch(BiFunction<Object, Object[], Object> handler) {}

    private static final Map<String, Dispatch> BY_KEY = new ConcurrentHashMap<>();
    private static final Map<String, Dispatch> BY_SIGNATURE = new ConcurrentHashMap<>();

    static {
        registerFabricDispatches();
    }

    public static void register(String classAndMethod, Dispatch dispatch) {
        BY_KEY.put(classAndMethod, dispatch);
    }

    public static void registerBySignature(String methodNameAndDesc, Dispatch dispatch) {
        BY_SIGNATURE.put(methodNameAndDesc, dispatch);
    }

    public static Dispatch lookup(String key) {
        return BY_KEY.get(key);
    }

    public static Dispatch lookupBySignature(String name, String desc) {
        Dispatch d = BY_SIGNATURE.get(name + desc);
        if (d != null) return d;
        for (Map.Entry<String, Dispatch> entry : BY_SIGNATURE.entrySet()) {
            String regKey = entry.getKey();
            int paren = regKey.indexOf('(');
            if (paren < 0) continue;
            if (!regKey.substring(0, paren).equals(name)) continue;
            String regDesc = regKey.substring(paren);
            if (descriptorsCompatible(regDesc, desc)) return entry.getValue();
        }
        return null;
    }

    private static boolean descriptorsCompatible(String registered, String actual) {
        if (registered.equals(actual)) return true;
        String normRegistered = registered.replaceAll("Lnet/minecraft/[^;]+;", "LMC;");
        String normActual = actual.replaceAll("Lnet/minecraft/[^;]+;", "LMC;");
        return normRegistered.equals(normActual);
    }

    public static Object invoke(String key, Object self, Object[] args) {
        Dispatch d = BY_KEY.get(key);
        if (d != null) {
            VyneLogger.debug("[VyneDispatch] HIT (Key): " + key);
        }
        if (d == null) {
            int hash = key.indexOf('#');
            int paren = key.indexOf('(', hash);
            String name = hash >= 0 && paren >= 0 ? key.substring(hash + 1, paren) : "";
            String desc = paren >= 0 ? key.substring(paren) : "";
            d = lookupBySignature(name, desc);
            if (d != null) {
                VyneLogger.debug("[VyneDispatch] HIT (Signature): " + name + desc + " for key " + key);
            }
        }
        if (d == null) {
            System.out.println("[VyneDispatch] MISS: " + key + " — returning null");
            return null;
        }
        try {
            return d.handler().apply(self, args);
        } catch (Throwable t) {
            System.out.println("[VyneDispatch] Dispatch failed for: " + key + " — " + t);
            return null;
        }
    }

    private static Object rewrapObject(Object obj, Class<?> targetClass, ClassLoader knotCl) {
        if (targetClass.isInstance(obj)) return obj;
        try {
            String className = obj.getClass().getName();
            Class<?> reloaded = Class.forName(className, true, knotCl);
            if (targetClass.isAssignableFrom(reloaded)) {
                java.lang.reflect.Constructor<?> ctor = reloaded.getDeclaredConstructors()[0];
                ctor.setAccessible(true);
                java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
                Object[] ctorArgs = new Object[ctor.getParameterCount()];
                for (int i = 0; i < fields.length && i < ctorArgs.length; i++) {
                    fields[i].setAccessible(true);
                    ctorArgs[i] = fields[i].get(obj);
                }
                return ctor.newInstance(ctorArgs);
            }
        } catch (Throwable ignored) {}
        return obj;
    }

    private static Object rewrapViaSerialization(Object obj, Class<?> targetClass, ClassLoader knotCl) {
        if (targetClass.isInstance(obj)) return obj;
        try {
            Class<?> reloaded = Class.forName(obj.getClass().getName(), true, knotCl);
            if (targetClass.isAssignableFrom(reloaded)) return reloaded.cast(obj);
        } catch (Throwable ignored) {}
        return obj;
    }

    private static Object bridgeModelLayerLocation(Object mll, Class<?> targetClass, ClassLoader knotCl) {
        if (targetClass.isInstance(mll)) return mll;
        try {
            String mllName = mll.getClass().getName();
            Class<?> reloaded = Class.forName(mllName, true, knotCl);
            if (targetClass.isAssignableFrom(reloaded)) {
                java.lang.reflect.Field[] fields = mll.getClass().getDeclaredFields();
                if (fields.length == 0) {
                    java.lang.reflect.Constructor<?> ctor = reloaded.getDeclaredConstructors()[0];
                    ctor.setAccessible(true);
                    Object[] ctorArgs = new Object[ctor.getParameterCount()];
                    java.lang.reflect.Field[] srcFields = mll.getClass().getDeclaredFields();
                    for (int i = 0; i < srcFields.length && i < ctorArgs.length; i++) {
                        srcFields[i].setAccessible(true);
                        ctorArgs[i] = srcFields[i].get(mll);
                    }
                    return ctor.newInstance(ctorArgs);
                }
                for (java.lang.reflect.Constructor<?> ctor : reloaded.getDeclaredConstructors()) {
                    if (ctor.getParameterCount() != fields.length) continue;
                    ctor.setAccessible(true);
                    Object[] ctorArgs = new Object[fields.length];
                    boolean ok = true;
                    for (int i = 0; i < fields.length; i++) {
                        fields[i].setAccessible(true);
                        Object val = fields[i].get(mll);
                        Class<?> paramType = ctor.getParameterTypes()[i];
                        if (val != null && !paramType.isInstance(val)) {
                            Class<?> reloadedVal = Class.forName(val.getClass().getName(), true, knotCl);
                            if (paramType.isAssignableFrom(reloadedVal)) {
                                val = reloadedVal.cast(val);
                            } else {
                                ok = false; break;
                            }
                        }
                        ctorArgs[i] = val;
                    }
                    if (!ok) continue;
                    return ctor.newInstance(ctorArgs);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object resolveRenderLayer(Object neoRenderType, Class<?> fabricLayerClass, ClassLoader knotCl) {
        if (fabricLayerClass.isInstance(neoRenderType)) return neoRenderType;
        try {
            String name = neoRenderType.getClass().getSimpleName().toLowerCase();
            for (java.lang.reflect.Field f : fabricLayerClass.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())
                        && fabricLayerClass.isAssignableFrom(f.getType())
                        && f.getName().toLowerCase().contains(name)) {
                    f.setAccessible(true);
                    return f.get(null);
                }
            }
            for (java.lang.reflect.Method m : fabricLayerClass.getMethods()) {
                if (m.getName().equals("getCutout") || m.getName().equals("cutout")) {
                    return m.invoke(null);
                }
            }
        } catch (Throwable ignored) {}
        return neoRenderType;
    }

    private static void registerFabricDispatches() {
        registerBySignature("setRenderType(Lnet/minecraft/world/level/block/Block;Lnet/minecraft/client/renderer/RenderType;)V",
            new Dispatch((self, args) -> {
                try {
                    Class<?> ibrt = Class.forName("net.minecraft.client.renderer.ItemBlockRenderTypes");
                    Method m = ibrt.getMethod("setRenderLayer",
                        Class.forName("net.minecraft.world.level.block.Block"),
                        Class.forName("net.minecraft.client.renderer.RenderType"));
                    m.invoke(null, args[0], args[1]);
                } catch (Throwable t) {
                    try {
                        ClassLoader knotCl = Thread.currentThread().getContextClassLoader();
                        Class<?> fbrt = Class.forName("net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap", true, knotCl);
                        Object map = fbrt.getField("INSTANCE").get(null);
                        Method putBlock = null;
                        for (Method candidate : fbrt.getMethods()) {
                            if (candidate.getName().equals("putBlock") && candidate.getParameterCount() == 2) {
                                putBlock = candidate;
                                break;
                            }
                        }
                        if (putBlock == null) throw new NoSuchMethodException("putBlock");
                        Class<?> blockParamType = putBlock.getParameterTypes()[0];
                        Class<?> layerParamType = putBlock.getParameterTypes()[1];
                        if (args[0] == null) return null;
                        Object block = blockParamType.isInstance(args[0]) ? args[0] : rewrapViaSerialization(args[0], blockParamType, knotCl);
                        Object layer = layerParamType.isInstance(args[1]) ? args[1] : resolveRenderLayer(args[1], layerParamType, knotCl);
                        if (block == null || layer == null) return null;
                        putBlock.invoke(map, block, layer);
                    } catch (Throwable t2) {
                        Throwable cause2 = t2 instanceof java.lang.reflect.InvocationTargetException ite2 && ite2.getCause() != null ? ite2.getCause() : t2;
                        System.out.println("[VyneDispatch] setRenderType(Block) — all delegates failed: " + cause2);
                        cause2.printStackTrace();
                    }
                }
                return null;
            }));

        registerBySignature("setRenderType(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/client/renderer/RenderType;)V",
            new Dispatch((self, args) -> {
                try {
                    ClassLoader knotCl = Thread.currentThread().getContextClassLoader();
                    Class<?> fbrt = Class.forName("net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap", true, knotCl);
                    Object map = fbrt.getField("INSTANCE").get(null);
                    Method putFluid = null;
                    for (Method candidate : fbrt.getMethods()) {
                        if (candidate.getName().equals("putFluid") && candidate.getParameterCount() == 2) {
                            putFluid = candidate;
                            break;
                        }
                    }
                    if (putFluid == null) throw new NoSuchMethodException("putFluid");
                    Class<?> fluidParamType = putFluid.getParameterTypes()[0];
                    Class<?> layerParamType = putFluid.getParameterTypes()[1];
                    if (args[0] == null) return null;
                    Object fluid = fluidParamType.isInstance(args[0]) ? args[0] : rewrapViaSerialization(args[0], fluidParamType, knotCl);
                    Object layer = layerParamType.isInstance(args[1]) ? args[1] : resolveRenderLayer(args[1], layerParamType, knotCl);
                    if (fluid == null || layer == null) return null;
                    putFluid.invoke(map, fluid, layer);
                } catch (Throwable t) {
                    Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
                    System.out.println("[VyneDispatch] setRenderType(Fluid) — delegate failed: " + cause);
                    cause.printStackTrace();
                }
                return null;
            }));

        registerBySignature("registerBlockEntityRenderer(Lnet/minecraft/world/level/block/entity/BlockEntityType;Lnet/minecraft/client/renderer/blockentity/BlockEntityRendererProvider;)V",
            new Dispatch((self, args) -> {
                try {
                    ClassLoader knotCl = Thread.currentThread().getContextClassLoader();
                    Class<?> fabricBerReg = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry", true, knotCl);
                    Method m = null;
                    for (Method candidate : fabricBerReg.getMethods()) {
                        if (candidate.getName().equals("register") && candidate.getParameterCount() == 2) {
                            m = candidate;
                            break;
                        }
                    }
                    if (m == null) throw new NoSuchMethodException("register not found in BlockEntityRendererRegistry");
                    if (args[0] == null || args[1] == null) return null;
                    Object bet = m.getParameterTypes()[0].isInstance(args[0]) ? args[0] : rewrapViaSerialization(args[0], m.getParameterTypes()[0], knotCl);
                    if (bet == null) return null;
                    m.invoke(null, bet, args[1]);
                } catch (Throwable t) {
                    Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
                    System.out.println("[VyneDispatch] registerBlockEntityRenderer — failed: " + cause);
                    cause.printStackTrace();
                }
                return null;
            }));

        registerBySignature("registerEntityRenderer(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/client/renderer/entity/EntityRendererProvider;)V",
            new Dispatch((self, args) -> {
                try {
                    ClassLoader knotCl = Thread.currentThread().getContextClassLoader();
                    Class<?> reg = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry", true, knotCl);
                    Method m = null;
                    for (Method candidate : reg.getMethods()) {
                        if (candidate.getName().equals("register") && candidate.getParameterCount() == 2) {
                            m = candidate;
                            break;
                        }
                    }
                    if (m == null) throw new NoSuchMethodException("register not found in EntityRendererRegistry");
                    if (args[0] == null || args[1] == null) return null;
                    Object et = m.getParameterTypes()[0].isInstance(args[0]) ? args[0] : rewrapViaSerialization(args[0], m.getParameterTypes()[0], knotCl);
                    Object erp = m.getParameterTypes()[1].isInstance(args[1]) ? args[1] : rewrapViaSerialization(args[1], m.getParameterTypes()[1], knotCl);
                    if (et == null || erp == null) return null;
                    m.invoke(null, et, erp);
                } catch (Throwable t) {
                    Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
                    System.out.println("[VyneDispatch] registerEntityRenderer — failed: " + cause);
                    cause.printStackTrace();
                }
                return null;
            }));

        final java.util.Set<Object> registeredLayerKeys =
            java.util.Collections.synchronizedSet(
                java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));
        final java.util.Set<String> registeredLayerStrings =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

        registerBySignature("registerLayerDefinition(Lnet/minecraft/client/model/geom/ModelLayerLocation;Ljava/util/function/Supplier;)V",
            new Dispatch((self, args) -> {
                try {
                    if (args[0] == null || args[1] == null) return null;
                    String layerKey = args[0].toString();
                    if (!registeredLayerStrings.add(layerKey)) return null;
                    ClassLoader knotCl = Thread.currentThread().getContextClassLoader();
                    Class<?> reg = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry", true, knotCl);
                    Method m = null;
                    for (Method candidate : reg.getMethods()) {
                        if (candidate.getName().equals("registerModelLayer") && candidate.getParameterCount() == 2) {
                            m = candidate;
                            break;
                        }
                    }
                    if (m == null) throw new NoSuchMethodException("registerModelLayer not found");
                    Class<?> mllTargetClass = m.getParameterTypes()[0];
                    Class<?> supplierTargetClass = m.getParameterTypes()[1];
                    Object mll = bridgeModelLayerLocation(args[0], mllTargetClass, knotCl);
                    if (mll == null) return null;
                    final java.util.function.Supplier<?> originalSupplier = (java.util.function.Supplier<?>) args[1];
                    Object layerSupplier = java.lang.reflect.Proxy.newProxyInstance(
                        knotCl,
                        new Class<?>[]{supplierTargetClass},
                        (proxy, method, pargs) -> {
                            if (method.getName().equals("get") || method.getName().equals("createModelData") || method.getName().equals("method_32073")) {
                                Object result = originalSupplier.get();
                                if (result == null) throw new IllegalStateException("LayerSupplier returned null — deferred registration not yet resolved");
                                return result;
                            }
                            if (method.getName().equals("equals")) return proxy == pargs[0];
                            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                            if (method.getName().equals("toString")) return "VyneLayerSupplier";
                            return null;
                        }
                    );
                    m.invoke(null, mll, layerSupplier);
                } catch (Throwable t) {
                    Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
                    System.out.println("[VyneDispatch] registerLayerDefinition — failed: " + cause);
                    cause.printStackTrace();
                }
                return null;
            }));

                registerBySignature("register(Lnet/minecraft/core/Registry;Lnet/minecraft/resources/ResourceLocation;Ljava/lang/Object;)Ljava/lang/Object;",
                    new Dispatch((self, args) -> {
                        try {
                            Object id = args[1];
                            Object fluidType = args[2];
                            System.out.println("[VyneDispatch] Fluid Registration intercepted: id=" + id + ", fluidType=" + fluidType);
                            return dev.stolym.vyne.fluid.VyneFluidTypeRegistry.register(id, fluidType);
                        } catch (Throwable t) {
                            System.out.println("[VyneDispatch] fluidRegistry.register(Registry,RL,V) failed: " + t);
                            t.printStackTrace();
                            return null;
                        }
                    }));

                registerBySignature("register(Lnet/minecraft/core/Registry;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;",
                    new Dispatch((self, args) -> {
                        try {
                            Object id = args[1];
                            Object fluidType = args[2];
                            System.out.println("[VyneDispatch] Fluid Registration intercepted: id=" + id + ", fluidType=" + fluidType);
                            return dev.stolym.vyne.fluid.VyneFluidTypeRegistry.register(id, fluidType);
                        } catch (Throwable t) {
                            System.out.println("[VyneDispatch] fluidRegistry.register(Registry,String,V) failed: " + t);
                            t.printStackTrace();
                            return null;
                        }
                    }));

                registerBySignature("register(Lnet/minecraft/core/Registry;Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;)Ljava/lang/Object;",
                    new Dispatch((self, args) -> {
                        try {
                            Object id = args[1];
                            Object fluidType = args[2];
                            System.out.println("[VyneDispatch] Fluid Registration intercepted: id=" + id + ", fluidType=" + fluidType);
                            return dev.stolym.vyne.fluid.VyneFluidTypeRegistry.registerByKey(id, fluidType);
                        } catch (Throwable t) {
                            System.out.println("[VyneDispatch] fluidRegistry.register(Registry,RK,V) failed: " + t);
                            t.printStackTrace();
                            return null;
                        }
                    }));

        registerBySignature("get(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;",
            new Dispatch((self, args) -> {
                try {
                    return dev.stolym.vyne.fluid.VyneFluidTypeRegistry.get(
                        (net.minecraft.resources.ResourceLocation) args[0]);
                } catch (Throwable t) {
                    return null;
                }
            }));

        registerBySignature("get(Lnet/minecraft/resources/ResourceKey;)Ljava/lang/Object;",
            new Dispatch((self, args) -> {
                try {
                    return dev.stolym.vyne.fluid.VyneFluidTypeRegistry.getByKey(args[0]);
                } catch (Throwable t) {
                    return null;
                }
            }));

        registerBySignature("getOptional(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
            new Dispatch((self, args) -> {
                try {
                    return dev.stolym.vyne.fluid.VyneFluidTypeRegistry.getOptional(
                        (net.minecraft.resources.ResourceLocation) args[0]);
                } catch (Throwable t) {
                    return java.util.Optional.empty();
                }
            }));

        registerBySignature("getKey(Ljava/lang/Object;)Lnet/minecraft/resources/ResourceLocation;",
            new Dispatch((self, args) -> {
                try {
                    return dev.stolym.vyne.fluid.VyneFluidTypeRegistry.getKey(args[0]);
                } catch (Throwable t) {
                    return null;
                }
            }));

        registerBySignature("getResourceKey(Ljava/lang/Object;)Ljava/util/Optional;",
            new Dispatch((self, args) -> {
                try {
                    return dev.stolym.vyne.fluid.VyneFluidTypeRegistry.getResourceKey(args[0]);
                } catch (Throwable t) {
                    return java.util.Optional.empty();
                }
            }));

        registerBySignature("containsKey(Lnet/minecraft/resources/ResourceLocation;)Z",
            new Dispatch((self, args) -> {
                try {
                    return dev.stolym.vyne.fluid.VyneFluidTypeRegistry.containsKey(
                        (net.minecraft.resources.ResourceLocation) args[0]);
                } catch (Throwable t) {
                    return false;
                }
            }));

        registerBySignature("keySet()Ljava/util/Set;",
            new Dispatch((self, args) -> {
                try {
                    return dev.stolym.vyne.fluid.VyneFluidTypeRegistry.keySet();
                } catch (Throwable t) {
                    return java.util.Collections.emptySet();
                }
            }));

        registerBySignature("stream()Ljava/util/stream/Stream;",
            new Dispatch((self, args) -> {
                try {
                    return dev.stolym.vyne.fluid.VyneFluidTypeRegistry.stream();
                } catch (Throwable t) {
                    return java.util.stream.Stream.empty();
                }
            }));

        registerBySignature("size()I",
            new Dispatch((self, args) -> {
                try {
                    return dev.stolym.vyne.fluid.VyneFluidTypeRegistry.size();
                } catch (Throwable t) {
                    return 0;
                }
            }));

        registerBySignature("freeze()Lnet/minecraft/core/Registry;",
            new Dispatch((self, args) -> {
                dev.stolym.vyne.fluid.VyneFluidTypeRegistry.freeze();
                return self;
            }));

        registerBySignature("holders()Ljava/util/stream/Stream;",
            new Dispatch((self, args) -> {
                try {
                    return dev.stolym.vyne.fluid.VyneFluidTypeRegistry.stream();
                } catch (Throwable t) {
                    return java.util.stream.Stream.empty();
                }
            }));

        registerBySignature("getHolder(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
            new Dispatch((self, args) -> {
                try {
                    return dev.stolym.vyne.fluid.VyneFluidTypeRegistry.getHolder(
                        (net.minecraft.resources.ResourceLocation) args[0]);
                } catch (Throwable t) {
                    return java.util.Optional.empty();
                }
            }));

        registerBySignature("wrapAsHolder(Ljava/lang/Object;)Lnet/minecraft/core/Holder;",
            new Dispatch((self, args) -> {
                try {
                    return dev.stolym.vyne.fluid.VyneFluidTypeRegistry.wrapAsHolder(args[0]);
                } catch (Throwable t) {
                    return args[0];
                }
            }));

        registerBySignature("asLookup()Lnet/minecraft/core/HolderLookup$RegistryLookup;",
            new Dispatch((self, args) -> {
                try {
                    java.lang.reflect.Method asLookupMethod = self.getClass().getMethod("asLookupHolder");
                    return asLookupMethod.invoke(self);
                } catch (Throwable t) {
                    System.out.println("[VyneDispatch] asLookup failed: " + t);
                    return null;
                }
            }));

        registerBySignature("asHolderIdMap()Ljava/util/Map;",
            new Dispatch((self, args) -> {
                return java.util.Collections.emptyMap();
            }));

        registerBySignature("registryLifecycle()Lcom/mojang/serialization/Lifecycle;",
            new Dispatch((self, args) -> {
                try {
                    Class<?> lifecycleClass = Class.forName("com.mojang.serialization.Lifecycle");
                    java.lang.reflect.Field stableField = lifecycleClass.getField("stable");
                    return stableField.get(null);
                } catch (Throwable t) {
                    return null;
                }
            }));

        registerBySignature("getOrCreateTag(Lnet/minecraft/tags/TagKey;)Lnet/minecraft/core/HolderSet$Named;",
            new Dispatch((self, args) -> {
                return null;
            }));

        registerBySignature("holderOwner()Lnet/minecraft/core/Holder$Owner;",
            new Dispatch((self, args) -> {
                return null;
            }));

        registerBySignature("get(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;",
            new Dispatch((self, args) -> {
                try {
                    return dev.stolym.vyne.fluid.VyneFluidTypeRegistry.get(
                        (net.minecraft.resources.ResourceLocation) args[0]);
                } catch (Throwable t) {
                    return null;
                }
            }));

        registerBySignature("getRandom(Lnet/minecraft/util/RandomSource;)Ljava/util/Optional;",
            new Dispatch((self, args) -> {
                return java.util.Optional.empty();
            }));

        registerBySignature("createIntrusiveHolder(Ljava/lang/Object;)Lnet/minecraft/core/Holder$Reference;",
            new Dispatch((self, args) -> {
                try {
                    return args[0];
                } catch (Throwable t) {
                    return null;
                }
            }));

        registerBySignature("getTag(Lnet/minecraft/tags/TagKey;)Ljava/util/Optional;",
            new Dispatch((self, args) -> {
                return java.util.Optional.empty();
            }));

        registerBySignature("getFluidType()Lnet/neoforged/neoforge/fluids/FluidType;",
            new Dispatch((self, args) -> {
                try {
                    if (self instanceof dev.stolym.vyne.fluid.VyneFluidWorkaround) {
                        return ((dev.stolym.vyne.fluid.VyneFluidWorkaround) self).getFluidType();
                    }
                    return dev.stolym.vyne.fluid.VyneFluidFallback.getVanillaFluidType(self);
                } catch (Throwable t) {
                    System.out.println("[VyneDispatch] getFluidType failed: " + t);
                    return null;
                }
            }));

        registerBySignature("method_47984(Lnet/minecraft/class_2378;Lnet/minecraft/class_5321;Ljava/lang/Object;)Lnet/minecraft/class_6880$class_6883;",
            new Dispatch((self, args) -> {
                try {
                    System.out.println("[VyneDispatch] method_47984 called: " + args[1] + " -> " + args[2]);
                    Object registry = args[0];
                    Object key = args[1];
                    Object entry = args[2];

                    java.lang.reflect.Method locMethod = key.getClass().getMethod("location");
                    net.minecraft.resources.ResourceLocation loc = (net.minecraft.resources.ResourceLocation) locMethod.invoke(key);

                    dev.stolym.vyne.fluid.VyneFluidTypeRegistry.register(loc, entry);

                    return entry;
                } catch (Throwable t) {
                    System.out.println("[VyneDispatch] method_47984 failed: " + t);
                    t.printStackTrace();
                    return null;
                }
            }));

        registerBySignature("method_47985(Lnet/minecraft/class_2378;Lnet/minecraft/class_2960;Ljava/lang/Object;)Lnet/minecraft/class_6880$class_6883;",
            new Dispatch((self, args) -> {
                try {
                    System.out.println("[VyneDispatch] method_47985 called: " + args[1] + " -> " + args[2]);
                    net.minecraft.resources.ResourceLocation id = (net.minecraft.resources.ResourceLocation) args[1];
                    Object entry = args[2];

                    dev.stolym.vyne.fluid.VyneFluidTypeRegistry.register(id, entry);

                    return entry;
                } catch (Throwable t) {
                    System.out.println("[VyneDispatch] method_47985 failed: " + t);
                    t.printStackTrace();
                    return null;
                }
            }));
    }
}
