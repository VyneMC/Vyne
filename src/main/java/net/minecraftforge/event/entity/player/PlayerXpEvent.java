/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.event.entity.player;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancelableEvent;

public class PlayerXpEvent extends Event {
    public Object getEntity() { return null; }

    public static class PickupXp extends PlayerXpEvent implements ICancelableEvent {
        private boolean canceled;
        public Object getOrb() { return null; }
        public boolean isCanceled() { return canceled; }
        public void setCanceled(boolean flag) { this.canceled = flag; }
    }

    public static class XpChange extends PlayerXpEvent {
        private int amount;
        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }
    }

    public static class LevelChange extends PlayerXpEvent {
        private int levels;
        public int getLevels() { return levels; }
        public void setLevels(int levels) { this.levels = levels; }
    }
}
