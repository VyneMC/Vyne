/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.fluid;

import dev.stolym.vyne.util.VyneLogger;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.IdentityHashMap;

public final class VyneFluidBridge {
    private static final Set<Object> REGISTERED =
        Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    public static void apply(ClassLoader cl) {
        apply();
    }

    public static void apply() {
        ClassLoader knotCl = Thread.currentThread().getContextClassLoader();

        captureFromNeoForgeRegistry(knotCl);

        Map<String, Object> fluidTypes = VyneFluidTypeRegistry.all();
        if (dev.stolym.vyne.util.VyneLogLevel.isVerbose()) {
            VyneLogger.info("[FluidBridge] Bridging " + fluidTypes.size() + " FluidType(s).");
        }

        int bridged = 0;
        for (Map.Entry<String, Object> entry : fluidTypes.entrySet()) {
            String id = entry.getKey();
            Object fluidType = entry.getValue();
            try {
                Object fabricFluid = resolveFluid(id, knotCl);
                if (fabricFluid != null) {
                    registerFabricAttributes(fabricFluid, fluidType, id, knotCl);
                    bridged++;
                } else {
                    VyneLogger.debug("[FluidBridge] No Fabric fluid matched: " + id
                        + " — skipping attribute registration.");
                }
            } catch (Throwable t) {
                VyneLogger.warn("[FluidBridge] Failed to bridge " + id + ": " + t.getMessage());
            }
        }

        if (dev.stolym.vyne.util.VyneLogLevel.isVerbose() || (!fluidTypes.isEmpty() && bridged > 0)) {
            VyneLogger.info("[FluidBridge] " + bridged + "/" + fluidTypes.size()
                + " fluid type(s) bridged to Fabric.");
        }
    }

    private static void captureFromNeoForgeRegistry(ClassLoader knotCl) {
        if (!VyneFluidTypeRegistry.isEmpty()) return;
        try {
            String builtInName = mappedClass("net/minecraft/core/registries/BuiltInRegistries", knotCl);
            Class<?> builtIn = Class.forName(builtInName, true, knotCl);
            for (java.lang.reflect.Field f : builtIn.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object candidate = f.get(null);
                if (candidate == null) continue;
                String cs = candidate.toString().toLowerCase();
                if (!(cs.contains("fluid_type") || cs.contains("fluidtype"))) continue;
                for (Method m : candidate.getClass().getMethods()) {
                    if (m.getName().equals("forEach") && m.getParameterCount() == 1) {
                        m.invoke(candidate, (java.util.function.Consumer<Object>) entry -> {
                            try {
                                Method keyM = entry.getClass().getMethod("getKey");
                                Method valM = entry.getClass().getMethod("getValue");
                                Object rl = keyM.invoke(entry);
                                Object ft = valM.invoke(entry);
                                if (rl != null && ft != null)
                                    VyneFluidTypeRegistry.register(rl, ft);
                            } catch (Throwable ignored) {}
                        });
                        break;
                    }
                    if ((m.getName().equals("values") || m.getName().equals("entrySet"))
                            && m.getParameterCount() == 0) {
                        try {
                            Object col = m.invoke(candidate);
                            if (col instanceof Iterable<?> it) {
                                for (Object entry : it) {
                                    tryCapturePair(entry);
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                return;
            }
        } catch (Throwable t) {
            VyneLogger.debug("[FluidBridge] captureFromNeoForgeRegistry failed: " + t);
        }
    }

    private static void tryCapturePair(Object entry) {
        try {
            Method keyM = null, valM = null;
            for (Method m : entry.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                if (m.getName().equals("getKey") || m.getName().equals("key")) keyM = m;
                if (m.getName().equals("getValue") || m.getName().equals("value")) valM = m;
            }
            if (keyM == null || valM == null) return;
            Object rl = keyM.invoke(entry);
            Object ft = valM.invoke(entry);
            if (rl != null && ft != null) VyneFluidTypeRegistry.register(rl, ft);
        } catch (Throwable ignored) {}
    }

    private static Object resolveFluid(String fluidId, ClassLoader knotCl) {
        try {
            Object rl = createRL(fluidId, knotCl);
            if (rl == null) return null;

            String builtInName = mappedClass("net/minecraft/core/registries/BuiltInRegistries", knotCl);
            Class<?> builtIn = Class.forName(builtInName, true, knotCl);

            for (java.lang.reflect.Field f : builtIn.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object candidate = f.get(null);
                if (candidate == null) continue;
                String cs = candidate.toString().toLowerCase();
                if (!cs.contains("fluid") || cs.contains("fluid_type")) continue;

                for (Method m : candidate.getClass().getMethods()) {
                    if (m.getParameterCount() != 1) continue;
                    if (!m.getParameterTypes()[0].isAssignableFrom(rl.getClass())) continue;
                    if (m.getName().equals("get") || m.getName().equals("getValue")
                            || m.getName().contains("method_")) {
                        try {
                            Object result = m.invoke(candidate, rl);
                            if (result != null) return result;
                        } catch (Throwable ignored) {}
                    }
                }
            }

            String flowingId = fluidId.contains(":")
                ? fluidId.split(":")[0] + ":flowing_" + fluidId.split(":")[1]
                : "flowing_" + fluidId;
            if (!flowingId.equals(fluidId)) return resolveFluid(flowingId, knotCl);
        } catch (Throwable t) {
            VyneLogger.debug("[FluidBridge] resolveFluid failed for " + fluidId + ": " + t);
        }
        return null;
    }

    private static void registerFabricAttributes(Object fluid, Object fluidType,
                                                  String fluidTypeId, ClassLoader knotCl) {
        if (!REGISTERED.add(fluid)) return;
        try {
            Class<?> attributesClass = Class.forName(
                "net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes", true, knotCl);
            String fluidClassName = mappedClass("net/minecraft/world/level/material/Fluid", knotCl);
            Class<?> fluidClass = Class.forName(fluidClassName, true, knotCl);

            boolean registered = false;
            for (Method m : attributesClass.getMethods()) {
                if (!m.getName().equals("register")) continue;
                try {
                    Class<?> handlerParam = findHandlerParam(m);
                    if (handlerParam == null) continue;
                    Object handler = createHandlerProxy(handlerParam, fluidType, knotCl);
                    if (handler == null) continue;
                    if (tryInvokeRegister(m, fluid, handler, handlerParam, fluidTypeId, knotCl)) {
                        registered = true;
                        break;
                    }
                } catch (java.lang.reflect.InvocationTargetException ite) {
                    Throwable cause = ite.getCause();
                    if (cause instanceof IllegalArgumentException
                            && cause.getMessage() != null
                            && cause.getMessage().contains("Duplicate")) {
                        VyneLogger.debug("[FluidBridge] Already registered: " + fluidTypeId);
                        registered = true;
                        break;
                    }
                    throw ite;
                }
            }
            if (!registered) {
                Class<?> fallbackHandlerClass = Class.forName(
                    "net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributeHandler", true, knotCl);
                Method reg = attributesClass.getMethod("register", fluidClass, fallbackHandlerClass);
                Object fallbackHandler = createHandlerProxy(fallbackHandlerClass, fluidType, knotCl);
                reg.invoke(null, fluid, fallbackHandler);
                registered = fallbackHandler != null;
            }
            if (registered && dev.stolym.vyne.util.VyneLogLevel.isVerbose()) {
                VyneLogger.info("[FluidBridge] ✓ Registered attributes for: " + fluidTypeId);
            }
        } catch (Throwable t) {
            String msg = t.getMessage() != null ? t.getMessage() : t.toString();
            if (msg.contains("argument type mismatch")) {
                VyneLogger.debug("[FluidBridge] Skipping attribute bind for "
                    + fluidTypeId + " due to runtime signature mismatch.");
            } else {
                VyneLogger.warn("[FluidBridge] Attribute registration failed for "
                    + fluidTypeId + ": " + msg);
            }
        }
    }

    private static boolean tryInvokeRegister(Method m, Object fluid, Object handler,
                                             Class<?> handlerClass, String fluidId,
                                             ClassLoader knotCl) throws Throwable {
        Class<?>[] params = m.getParameterTypes();
        if (params.length == 2) {
            Object fluid0 = coerceFluidArg(params[0], fluid, fluidId, knotCl);
            Object fluid1 = coerceFluidArg(params[1], fluid, fluidId, knotCl);
            if (fluid0 != null && params[1].isInstance(handler)) {
                m.invoke(null, fluid0, handler);
                return true;
            }
            if (params[0].isInstance(handler) && fluid1 != null) {
                m.invoke(null, handler, fluid1);
                return true;
            }
            if (fluid0 != null && params[1] == handlerClass) {
                m.invoke(null, fluid0, handler);
                return true;
            }
            if (params[0] == handlerClass && fluid1 != null) {
                m.invoke(null, handler, fluid1);
                return true;
            }
            if (params[0] == handlerClass && params[1].isArray()) {
                Object arr = coerceFluidArg(params[1], fluid, fluidId, knotCl);
                if (arr != null) { m.invoke(null, handler, arr); return true; }
            }
            if (params[1] == handlerClass && params[0].isArray()) {
                Object arr = coerceFluidArg(params[0], fluid, fluidId, knotCl);
                if (arr != null) { m.invoke(null, arr, handler); return true; }
            }
        }
        if (params.length == 1 && m.isVarArgs() && params[0].isArray()) {
            Object arr = coerceFluidArg(params[0], fluid, fluidId, knotCl);
            if (arr != null) { m.invoke(null, arr); return true; }
        }
        if (params.length == 3) {
            int handlerIdx = -1;
            for (int i = 0; i < params.length; i++) {
                if (params[i] == handlerClass || params[i].isInstance(handler)) {
                    handlerIdx = i;
                    break;
                }
            }
            if (handlerIdx >= 0) {
                Object[] args = new Object[3];
                args[handlerIdx] = handler;
                for (int i = 0; i < params.length; i++) {
                    if (i == handlerIdx) continue;
                    args[i] = coerceFluidArg(params[i], fluid, fluidId, knotCl);
                }
                boolean complete = true;
                for (Object arg : args) {
                    if (arg == null) { complete = false; break; }
                }
                if (complete) {
                    m.invoke(null, args);
                    return true;
                }
            }
        }
        return false;
    }

    private static Object coerceFluidArg(Class<?> target, Object fluid, String fluidId,
                                         ClassLoader knotCl) {
        if (target == null) return null;
        if (target.isInstance(fluid)) return fluid;
        if (target.isArray()) {
            Class<?> component = target.getComponentType();
            if (component == null) return null;
            Object one = coerceFluidArg(component, fluid, fluidId, knotCl);
            if (one == null) return null;
            Object arr = java.lang.reflect.Array.newInstance(component, 1);
            java.lang.reflect.Array.set(arr, 0, one);
            return arr;
        }
        if (target.getName().contains("Fluid") || target.getName().contains("class_3611")) {
            try {
                ClassLoader cl = target.getClassLoader() != null ? target.getClassLoader() : knotCl;
                Object resolved = resolveFluid(fluidId, cl);
                if (resolved != null && target.isInstance(resolved)) return resolved;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Class<?> findHandlerParam(Method method) {
        for (Class<?> pt : method.getParameterTypes()) {
            if (pt.getName().equals("net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributeHandler")
                    || pt.getSimpleName().contains("FluidVariantAttributeHandler")) {
                return pt;
            }
        }
        return null;
    }

    private static Object createHandlerProxy(Class<?> handlerInterface, Object fluidType, ClassLoader knotCl) {
        if (handlerInterface == null || !handlerInterface.isInterface()) return null;
        ClassLoader proxyCl = handlerInterface.getClassLoader() != null
            ? handlerInterface.getClassLoader() : knotCl;
        return java.lang.reflect.Proxy.newProxyInstance(
            proxyCl,
            new Class[]{handlerInterface},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getViscosity" -> {
                        try { return (int) readProp(fluidType, "properties", "viscosity"); }
                        catch (Throwable e) { return 1000; }
                    }
                    case "isLighterThanAir" -> {
                        try {
                            int density = (int) readProp(fluidType, "properties", "density");
                            return density < 0;
                        } catch (Throwable e) { return false; }
                    }
                    case "getLuminance" -> {
                        try { return (int) readProp(fluidType, "properties", "lightLevel"); }
                        catch (Throwable e) { return 0; }
                    }
                    case "getName" -> {
                        try {
                            Object props = field(fluidType, "properties");
                            if (props == null) return null;
                            String descId = (String) field(props, "descriptionId");
                            if (descId == null || descId.isBlank()) return null;
                            String compName = mappedClass(
                                "net/minecraft/network/chat/Component", knotCl);
                            Class<?> compClass = Class.forName(compName, true, knotCl);
                            return compClass.getMethod("translatable", String.class)
                                .invoke(null, descId);
                        } catch (Throwable e) { return null; }
                    }
                    default -> {
                        if (method.isDefault()) {
                            return java.lang.reflect.InvocationHandler.invokeDefault(
                                proxy, method, args);
                        }
                        return defaultForReturn(method.getReturnType());
                    }
                }
            });
    }

    private static Object readProp(Object obj, String field1, String field2) throws Exception {
        Object inner = field(obj, field1);
        if (inner == null) throw new Exception("null: " + field1);
        return field(inner, field2);
    }

    private static Object field(Object obj, String name) throws Exception {
        Class<?> cls = obj.getClass();
        while (cls != null) {
            try {
                java.lang.reflect.Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " not found in " + obj.getClass());
    }

    private static Object createRL(String id, ClassLoader cl) {
        try {
            String rlName = mappedClass("net/minecraft/resources/ResourceLocation", cl);
            Class<?> rlClass = Class.forName(rlName, true, cl);
            for (Method m : rlClass.getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class
                        && java.lang.reflect.Modifier.isStatic(m.getModifiers())
                        && (m.getName().equals("parse") || m.getName().equals("of")
                            || m.getName().contains("method_"))) {
                    try { return m.invoke(null, id); } catch (Throwable ignored) {}
                }
            }
            for (java.lang.reflect.Constructor<?> c : rlClass.getConstructors()) {
                if (c.getParameterCount() == 2
                        && c.getParameterTypes()[0] == String.class) {
                    String ns = id.contains(":") ? id.split(":")[0] : "minecraft";
                    String path = id.contains(":") ? id.split(":")[1] : id;
                    return c.newInstance(ns, path);
                }
                if (c.getParameterCount() == 1 && c.getParameterTypes()[0] == String.class) {
                    return c.newInstance(id);
                }
            }
        } catch (Throwable t) {
            VyneLogger.debug("[FluidBridge] createRL failed for " + id + ": " + t);
        }
        return null;
    }

    private static String mappedClass(String mojInternal, ClassLoader cl) {
        if (dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
            return dev.stolym.vyne.mappings.MappingRegistry.get()
                .mapClass(mojInternal).replace('/', '.');
        }
        return switch (mojInternal) {
            case "net/minecraft/resources/ResourceLocation"          -> "net.minecraft.class_2960";
            case "net/minecraft/core/registries/BuiltInRegistries"  -> "net.minecraft.class_7923";
            case "net/minecraft/world/level/material/Fluid"         -> "net.minecraft.class_3611";
            case "net/minecraft/network/chat/Component"             -> "net.minecraft.class_2561";
            default -> mojInternal.replace('/', '.');
        };
    }

    private static Object defaultForReturn(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class || type == short.class
                || type == byte.class || type == char.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }
}
