/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.fluid;

import net.minecraft.resources.ResourceLocation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VyneFluidRegistry {
    private static final Map<String, Object> FLUID_BY_ID = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<String, String> FLUID_TO_FLUID_TYPE = Collections.synchronizedMap(new LinkedHashMap<>());

    public static void record(String fluidId, Object fluidObj) {
        dev.stolym.vyne.util.VyneLogger.info("[VyneFluidRegistry] Recording fluid: " + fluidId + " object=" + fluidObj);
        FLUID_BY_ID.put(fluidId, fluidObj);
        ResourceLocation fluidTypeId = resolveFluidTypeId(fluidObj);
        if (fluidTypeId == null) {
            fluidTypeId = inferFluidTypeIdFromFluidId(fluidId);
        }
        if (fluidTypeId != null) {
            FLUID_TO_FLUID_TYPE.put(fluidTypeId.toString(), fluidId);
            dev.stolym.vyne.util.VyneLogger.info("[VyneFluidRegistry] Mapped FluidType " + fluidTypeId + " -> Fluid " + fluidId);
        } else {
            dev.stolym.vyne.util.VyneLogger.warn("[VyneFluidRegistry] Could not resolve FluidType for fluid: " + fluidId);
        }
    }

    public static Object getFluidByFluidTypeId(Object fluidTypeId) {
        String fluidId = FLUID_TO_FLUID_TYPE.get(fluidTypeId != null ? fluidTypeId.toString() : "null");
        if (fluidId == null) return null;
        return FLUID_BY_ID.get(fluidId);
    }

    public static String getFluidIdByFluidTypeId(Object fluidTypeId) {
        return FLUID_TO_FLUID_TYPE.get(fluidTypeId != null ? fluidTypeId.toString() : "null");
    }

    public static Map<String, Object> allFluids() {
        return Collections.unmodifiableMap(FLUID_BY_ID);
    }

    private static ResourceLocation resolveFluidTypeId(Object fluidObj) {
        try {
            java.lang.reflect.Method method = fluidObj.getClass().getMethod("getFluidType");
            Object fluidType = method.invoke(fluidObj);
            if (fluidType != null) {
                String keyStr = VyneFluidTypeRegistry.getKey(fluidType);
                if (keyStr != null) {
                    return toResourceLocation(keyStr);
                }
            }
        } catch (Throwable t) {
            dev.stolym.vyne.util.VyneLogger.debug("[VyneFluidRegistry] Standard getFluidType resolution failed: " + t.getMessage());
        }

        try {
            Object fallbackFluidType = VyneFluidFallback.getVanillaFluidType(fluidObj);
            if (fallbackFluidType != null) {
                String keyStr = VyneFluidTypeRegistry.getKey(fallbackFluidType);
                if (keyStr != null) {
                    return toResourceLocation(keyStr);
                }
            }
        } catch (Throwable t) {
            dev.stolym.vyne.util.VyneLogger.warn("[VyneFluidRegistry] Fallback resolution failed: " + t.getMessage());
        }

        dev.stolym.vyne.util.VyneLogger.warn("[VyneFluidRegistry] Could not resolve FluidType for " + fluidObj.getClass().getName());
        return null;
    }

    private static ResourceLocation inferFluidTypeIdFromFluidId(String fluidId) {
        if (fluidId == null || fluidId.isBlank()) {
            return null;
        }
        String typeId = fluidId;
        int colon = typeId.indexOf(':');
        if (colon >= 0) {
            String namespace = typeId.substring(0, colon);
            String path = typeId.substring(colon + 1);
            if (path.startsWith("flowing_")) {
                typeId = namespace + ":" + path.substring("flowing_".length());
            }
        }
        if (VyneFluidTypeRegistry.containsKey(typeId)) {
            return toResourceLocation(typeId);
        }
        return null;
    }

    private static ResourceLocation toResourceLocation(String id) {
        int colon = id.indexOf(':');
        if (colon > 0) {
            return new ResourceLocation(id.substring(0, colon), id.substring(colon + 1));
        }
        return new ResourceLocation("minecraft", id);
    }
}
