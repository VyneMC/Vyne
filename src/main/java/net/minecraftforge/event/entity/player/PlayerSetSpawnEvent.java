/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.event.entity.player;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancelableEvent;

public class PlayerSetSpawnEvent extends Event implements ICancelableEvent {
    private boolean canceled;
    public Object getEntity() { return null; }
    public Object getNewSpawn() { return null; }
    public boolean isForced() { return false; }
    public boolean isCanceled() { return canceled; }
    public void setCanceled(boolean flag) { this.canceled = flag; }
}
