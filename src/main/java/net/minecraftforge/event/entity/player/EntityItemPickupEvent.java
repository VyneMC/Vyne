/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.event.entity.player;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancelableEvent;

public class EntityItemPickupEvent extends PlayerEvent implements ICancelableEvent {
    private boolean canceled;
    private Object result;
    public Object getItem() { return null; }
    public boolean isCanceled() { return canceled; }
    public void setCanceled(boolean flag) { this.canceled = flag; }
    public void setResult(Object r) { this.result = r; }
    public Object getResult() { return result; }
}
