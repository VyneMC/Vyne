/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.event;

import dev.stolym.vyne.util.VyneLogger;
import net.neoforged.bus.api.VyneEventBus;

public final class VyneGameplayDispatcher {
    private VyneGameplayDispatcher() {}

    public static void firePlayerTickPre(Object player) {
        if (player == null) return;
        try {
            VyneEventBus.global().post(new net.minecraftforge.event.TickEvent.PlayerTickEvent.Pre(player));
        } catch (Throwable t) {
            VyneLogger.warn("[Vyne-Tick] player-pre failure: " + t);
        }
    }

    public static void firePlayerTickPost(Object player) {
        if (player == null) return;
        try {
            VyneEventBus.global().post(new net.minecraftforge.event.TickEvent.PlayerTickEvent.Post(player));
        } catch (Throwable t) {
            VyneLogger.warn("[Vyne-Tick] player-post failure: " + t);
        }
    }

    public static void fireServerTickPost(Object server) {
        try {
            net.minecraftforge.event.TickEvent.ServerTickEvent e =
                    new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END) {
                        @Override public Object getServer() { return server; }
                    };
            VyneEventBus.global().post(e);
        } catch (Throwable t) {
            VyneLogger.warn("[Vyne-Tick] server-post failure: " + t);
        }
    }

    public static void fireLevelTickPost(Object level) {
        if (level == null) return;
        try {
            VyneEventBus.global().post(new net.minecraftforge.event.TickEvent.LevelTickEvent.Post(level));
        } catch (Throwable t) {
            VyneLogger.warn("[Vyne-Tick] level-post failure: " + t);
        }
    }

    public static void fireEntityJoinLevel(Object entity, Object level) {
        if (entity == null || level == null) return;
        try {
            net.minecraftforge.event.entity.EntityJoinLevelEvent e =
                    new net.minecraftforge.event.entity.EntityJoinLevelEvent(
                            (net.minecraft.class_1297) entity,
                            (net.minecraft.class_1937) level);
            VyneEventBus.global().post(e);
        } catch (Throwable t) {
            VyneLogger.warn("[Vyne-Gameplay] EntityJoinLevelEvent failure: " + t);
        }
    }

    public static void firePlayerLoggedIn(Object player) {
        if (player == null) return;
        try {
            Class<?> cls = Class.forName("net.minecraftforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent",
                    true, VyneGameplayDispatcher.class.getClassLoader());
            Object evt = cls.getConstructors()[0].newInstance(player);
            VyneEventBus.global().post(evt);
        } catch (Throwable t) {
            VyneLogger.warn("[Vyne-Gameplay] PlayerLoggedInEvent failure: " + t);
        }
    }

    public static void firePlayerRespawn(Object player, boolean endConquered) {
        if (player == null) return;
        try {
            Class<?> cls = Class.forName("net.minecraftforge.event.entity.player.PlayerEvent$PlayerRespawnEvent",
                    true, VyneGameplayDispatcher.class.getClassLoader());
            Object evt;
            try {
                evt = cls.getConstructor(Object.class, boolean.class).newInstance(player, endConquered);
            } catch (NoSuchMethodException nsme) {
                evt = cls.getConstructor(Object.class).newInstance(player);
            }
            VyneEventBus.global().post(evt);
        } catch (Throwable t) {
            VyneLogger.warn("[Vyne-Gameplay] PlayerRespawnEvent failure: " + t);
        }
    }
}
