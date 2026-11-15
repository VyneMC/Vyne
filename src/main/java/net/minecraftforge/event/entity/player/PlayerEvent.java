/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.event.entity.player;

import net.neoforged.bus.api.Event;

public class PlayerEvent extends Event {
    public Object getEntity() { return null; }
    public Object getPlayer() { return null; }

    public static class Clone extends PlayerEvent {
        public boolean isWasDeath() { return false; }
        public Object getOriginal() { return null; }
    }

    public static class PlayerLoggedInEvent extends PlayerEvent {}
    public static class PlayerLoggedOutEvent extends PlayerEvent {}
    public static class PlayerRespawnEvent extends PlayerEvent {
        public boolean isEndConquered() { return false; }
    }

    public static class PlayerChangedDimensionEvent extends PlayerEvent {
        public Object getFrom() { return null; }
        public Object getTo() { return null; }
    }

    public static class StartTracking extends PlayerEvent {
        public Object getTarget() { return null; }
    }

    public static class StopTracking extends PlayerEvent {
        public Object getTarget() { return null; }
    }

    public static class ItemPickupEvent extends PlayerEvent {
        public Object getStack() { return null; }
        public Object getOriginalEntity() { return null; }
    }

    public static class PlayerChangeGameModeEvent extends PlayerEvent {
        public Object getNewGameMode() { return null; }
        public Object getCurrentGameMode() { return null; }
    }
}
