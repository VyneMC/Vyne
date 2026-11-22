/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import dev.stolym.vyne.core.VyneMode;
import dev.stolym.vyne.util.VyneLogger;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class PatchLayerEvents {
    private static boolean registered;

    private PatchLayerEvents() {}

    public static synchronized void register() {
        if (registered || !VyneMode.patchedGame()) return;
        registered = true;
        FabricLoader fabric = FabricLoader.getInstance();
        try {
            Class.forName("dev.stolym.vyne.hooks.fabric.FabricApiHooks", true, PatchLayerEvents.class.getClassLoader()).getMethod("register").invoke(null);
            if (fabric.isModLoaded("architectury")) {
                listen("net.neoforged.neoforge.event.level.BlockEvent$BreakEvent", ArchitecturyBridge::onBlockBreak);
                listen("net.neoforged.neoforge.event.entity.EntityJoinLevelEvent", ArchitecturyBridge::onEntityJoin);
                listen("net.neoforged.neoforge.event.entity.living.MobSpawnEvent$PositionCheck", ArchitecturyBridge::onSpawnPositionCheck);
                if (fabric.getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT) {
                    listen("net.neoforged.neoforge.client.event.ScreenEvent$Render$Pre", ArchitecturyBridge::onScreenRenderPre);
                    listen("net.neoforged.neoforge.client.event.ScreenEvent$Render$Post", ArchitecturyBridge::onScreenRenderPost);
                    listen("net.neoforged.neoforge.client.event.ScreenEvent$CharacterTyped$Pre", ArchitecturyBridge::onCharTypedPre);
                    listen("net.neoforged.neoforge.client.event.ScreenEvent$CharacterTyped$Post", ArchitecturyBridge::onCharTypedPost);
                    listen("net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$RightClickEmpty", ArchitecturyBridge::onRightClickEmpty);
                }
                VyneLogger.info("[PatchLayer] Architectury's block break, entity add, spawn check, screen and right-click events answered through NeoForge's events");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            VyneLogger.warn("[PatchLayer] Could not register Fabric API bridges: " + e);
        }
    }

    private static void listen(String eventClass, Consumer<Object> listener) throws ReflectiveOperationException {
        ClassLoader loader = PatchLayerEvents.class.getClassLoader();
        Object bus = Class.forName("net.neoforged.neoforge.common.NeoForge", true, loader).getField("EVENT_BUS").get(null);
        Object normal = Class.forName("net.neoforged.bus.api.EventPriority", true, loader).getField("NORMAL").get(null);
        Class<?> type = Class.forName(eventClass, true, loader);
        for (Method m : bus.getClass().getMethods()) {
            if (m.getName().equals("addListener") && m.getParameterCount() == 4 && m.getParameterTypes()[2] == Class.class) {
                m.invoke(bus, normal, false, type, listener);
                return;
            }
        }
        throw new NoSuchMethodException("IEventBus.addListener(EventPriority, boolean, Class, Consumer)");
    }
}
