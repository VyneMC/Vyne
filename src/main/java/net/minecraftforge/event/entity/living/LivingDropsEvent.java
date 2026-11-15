/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.event.entity.living;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancelableEvent;

public class LivingDropsEvent extends LivingEvent implements ICancelableEvent {
    private final java.util.Collection<Object> drops = new java.util.ArrayList<>();
    private boolean canceled;

    public java.util.Collection<Object> getDrops() { return drops; }
    public Object getSource() { return null; }
    public int getLootingLevel() { return 0; }
    public boolean isRecentlyHit() { return false; }

    public boolean isCanceled() { return canceled; }
    public void setCanceled(boolean flag) { this.canceled = flag; }
}
