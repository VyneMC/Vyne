/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.event.entity.living;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancelableEvent;

public class MobSpawnEvent extends LivingEvent {
    public Object getLevel() { return null; }
    public Object getEntity() { return null; }
    public double getX() { return 0; }
    public double getY() { return 0; }
    public double getZ() { return 0; }

    public static class FinalizeSpawn extends MobSpawnEvent implements ICancelableEvent {
        private boolean canceled;
        public Object getSpawnType() { return null; }
        public Object getSpawnData() { return null; }
        public boolean isCanceled() { return canceled; }
        public void setCanceled(boolean flag) { this.canceled = flag; }
    }

    public static class PositionCheck extends MobSpawnEvent {
        public Object getResult() { return null; }
    }

    public static class SpawnPlacementCheck extends MobSpawnEvent {}
}
