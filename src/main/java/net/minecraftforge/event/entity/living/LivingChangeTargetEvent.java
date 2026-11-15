/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.event.entity.living;

import net.neoforged.bus.api.ICancelableEvent;

public class LivingChangeTargetEvent extends LivingEvent implements ICancelableEvent {
    private boolean canceled;
    public Object getOriginalTarget() { return null; }
    public Object getNewTarget() { return null; }
    public void setNewTarget(Object target) {}
    public boolean isCanceled() { return canceled; }
    public void setCanceled(boolean flag) { this.canceled = flag; }
}
