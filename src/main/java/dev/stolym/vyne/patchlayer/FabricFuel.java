/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import dev.stolym.vyne.util.VyneLogger;

import java.util.Map;

public final class FabricFuel {
    private static volatile boolean failed;

    private FabricFuel() {}

    static void onBurnTime(Object event) {
        if (failed) return;
        try {
            if ((int) event.getClass().getMethod("getBurnTime").invoke(event) > 0) return;
            Object stack = event.getClass().getMethod("getItemStack").invoke(event);
            Object item = stack.getClass().getMethod("method_7909").invoke(stack);
            Class<?> registry = Class.forName("net.fabricmc.fabric.api.registry.FuelRegistry", true, FabricFuel.class.getClassLoader());
            Object instance = registry.getField("INSTANCE").get(null);
            Map<?, ?> times = (Map<?, ?>) instance.getClass().getMethod("getFuelTimes").invoke(instance);
            Object time = times.get(item);
            if (time instanceof Integer ticks && ticks > 0) {
                event.getClass().getMethod("setBurnTime", int.class).invoke(event, ticks);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            VyneLogger.warn("[PatchLayer] Fabric furnace fuels are off: " + e);
        }
    }
}
