/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.fluids;

import dev.stolym.vyne.fluid.VyneFluidTypeRegistry;

import java.util.concurrent.ConcurrentHashMap;

public final class FluidTypeBridge {
    private static final ConcurrentHashMap<Object, FluidType> CACHE = new ConcurrentHashMap<>();
    private static volatile FluidType defaultInstance;

    private FluidTypeBridge() {}

    public static FluidType getFluidType(Object fluid) {
        if (fluid == null) {
            return defaultInstance();
        }
        FluidType cached = CACHE.get(fluid);
        if (cached != null) return cached;

        FluidType resolved = resolveFromRegistries(fluid);
        if (resolved == null) {
            resolved = defaultInstance();
        }
        CACHE.putIfAbsent(fluid, resolved);
        return resolved;
    }

    private static FluidType resolveFromRegistries(Object fluid) {
        try {
            String id = fluidId(fluid);
            if (id != null) {
                Object v = VyneFluidTypeRegistry.get(id);
                if (v instanceof FluidType ft) return ft;
            }
        } catch (Throwable ignored) {}

        try {
            for (java.lang.reflect.Method m : fluid.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                if (!FluidType.class.isAssignableFrom(m.getReturnType())) continue;
                m.setAccessible(true);
                Object v = m.invoke(fluid);
                if (v instanceof FluidType ft) return ft;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String fluidId(Object fluid) {
        try {
            Class<?> registries = Class.forName("net.minecraft.class_7923", false, fluid.getClass().getClassLoader());
            java.lang.reflect.Field fluidReg = registries.getField("field_41173");
            Object reg = fluidReg.get(null);
            for (String name : new String[]{"method_10221", "getKey"}) {
                try {
                    java.lang.reflect.Method m = reg.getClass().getMethod(name, Object.class);
                    Object rl = m.invoke(reg, fluid);
                    if (rl != null) return rl.toString();
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static FluidType defaultInstance() {
        FluidType d = defaultInstance;
        if (d != null) return d;
        synchronized (FluidTypeBridge.class) {
            if (defaultInstance == null) {
                defaultInstance = new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid_type.vyne_fallback"));
            }
            return defaultInstance;
        }
    }
}
