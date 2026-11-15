/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.fluid;

import dev.stolym.vyne.util.VyneLogger;

public final class VyneFluidFallback {
    public static Object getVanillaFluidType(Object fluid) {
        try {
            Class<?> commonHooksClass = Class.forName("net.neoforged.neoforge.common.CommonHooks");
            java.lang.reflect.Method getVanillaFluidType = commonHooksClass.getMethod("getVanillaFluidType", Object.class);
            return getVanillaFluidType.invoke(null, fluid);
        } catch (Throwable t) {
            VyneLogger.warn("[VyneFluidFallback] CommonHooks.getVanillaFluidType failed: " + t.getMessage());
            return getFallbackFluidType(fluid);
        }
    }

    private static Object getFallbackFluidType(Object fluid) {
        try {
            String className = fluid.getClass().getName();
            if (className.contains("Water") || className.contains("Lava") || className.contains("Empty")) {
                Class<?> neoForgeModClass = Class.forName("net.neoforged.neoforge.common.NeoForgeMod");
                java.lang.reflect.Field waterTypeField = neoForgeModClass.getField("WATER_TYPE");
                java.lang.reflect.Field lavaTypeField = neoForgeModClass.getField("LAVA_TYPE");
                java.lang.reflect.Field emptyTypeField = neoForgeModClass.getField("EMPTY_TYPE");

                Object waterType = waterTypeField.get(null);
                Object lavaType = lavaTypeField.get(null);
                Object emptyType = emptyTypeField.get(null);

                java.lang.reflect.Method valueMethod = waterType.getClass().getMethod("value");

                if (className.contains("Water")) {
                    return valueMethod.invoke(waterType);
                }
                if (className.contains("Lava")) {
                    return valueMethod.invoke(lavaType);
                }
                if (className.contains("Empty")) {
                    return valueMethod.invoke(emptyType);
                }
            }
            return null;
        } catch (Throwable t) {
            VyneLogger.error("[VyneFluidFallback] Fallback fluid type detection failed: " + t.getMessage());
            return null;
        }
    }

    public static boolean isVanillaFluid(Object fluid) {
        try {
            String className = fluid.getClass().getName();
            return className.contains("Water") || className.contains("Lava") || className.contains("Empty");
        } catch (Throwable t) {
            return false;
        }
    }
}
