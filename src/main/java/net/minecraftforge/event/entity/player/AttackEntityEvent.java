/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.event.entity.player;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancelableEvent;

public class AttackEntityEvent extends Event implements ICancelableEvent {
    private boolean canceled;
    public Object getEntity() { return null; }
    public Object getTarget() { return null; }
    public boolean isCanceled() { return canceled; }
    public void setCanceled(boolean flag) { this.canceled = flag; }
}
