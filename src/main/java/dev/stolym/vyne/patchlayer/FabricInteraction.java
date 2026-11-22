/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import dev.stolym.vyne.util.VyneLogger;

public final class FabricInteraction {
    private FabricInteraction() {}

    static void onEntityInteractSpecific(Object event) {
        try {
            Object player = FabricEvents.call(event, "getEntity");
            Object level = FabricEvents.call(event, "getLevel");
            if (!(boolean) FabricEvents.call(level, "method_8608")) return;
            Object target = FabricEvents.call(event, "getTarget");
            Object local = FabricEvents.call(event, "getLocalPos");
            Object position = FabricEvents.call(local, "method_1019", FabricEvents.call(target, "method_19538"));
            Object hit = Class.forName("net.minecraft.class_3966", true, event.getClass().getClassLoader())
                    .getConstructor(Class.forName("net.minecraft.class_1297", true, event.getClass().getClassLoader()),
                            Class.forName("net.minecraft.class_243", true, event.getClass().getClassLoader()))
                    .newInstance(target, position);
            Object result = FabricEvents.fire("net.fabricmc.fabric.api.event.player.UseEntityCallback", "EVENT", "interact",
                    player, level, FabricEvents.call(event, "getHand"), target, hit);
            if (result == null || ((Enum<?>) result).name().equals("PASS")) return;
            FabricEvents.call(event, "setCancellationResult", result);
            FabricEvents.call(event, "setCanceled", true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            VyneLogger.warn("[PatchLayer] Fabric UseEntityCallback could not be answered on the client: " + e);
        }
    }

    public static void blockEntityUnloaded(Object chunk, Object removed) {
        if (removed == null || !assignable(removed.getClass(), "net.minecraft.class_2586")) return;
        try {
            Object level = FabricEvents.call(chunk, "method_12200");
            if (level == null || !assignable(level.getClass(), "net.minecraft.class_3218")) return;
            FabricEvents.fire("net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents", "BLOCK_ENTITY_UNLOAD", "onUnload", removed, level);
        } catch (ReflectiveOperationException | RuntimeException e) {
            VyneLogger.warn("[PatchLayer] Fabric BLOCK_ENTITY_UNLOAD could not be fired: " + e);
        }
    }

    private static boolean assignable(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (c.getName().equals(name)) return true;
        }
        return false;
    }

    public static boolean allowBlockDustTint(Object state, Object level, Object pos) {
        try {
            Object allowed = FabricEvents.fire("net.fabricmc.fabric.api.client.particle.v1.ParticleRenderEvents", "ALLOW_BLOCK_DUST_TINT", "allowBlockDustTint", state, level, pos);
            return !Boolean.FALSE.equals(allowed);
        } catch (ReflectiveOperationException | RuntimeException e) {
            VyneLogger.warn("[PatchLayer] Fabric ALLOW_BLOCK_DUST_TINT could not be answered: " + e);
            return true;
        }
    }
}
