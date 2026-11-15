/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.event.entity.living;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancelableEvent;

public class LivingEvent extends Event {
    public Object getEntity() { return null; }
    public Object getLivingEntity() { return null; }

    public static class LivingJumpEvent extends LivingEvent {}

    public static class LivingVisibilityEvent extends LivingEvent {
        public double getVisibilityModifier() { return 1.0; }
        public void modifyVisibility(double multiplier) {}
    }

    public static class LivingTickEvent extends LivingEvent {}

    public static class LivingChangeTargetEvent extends LivingEvent implements ICancelableEvent {
        private boolean canceled;
        public Object getOriginalTarget() { return null; }
        public Object getNewTarget() { return null; }
        public void setNewTarget(Object target) {}
        public boolean isCanceled() { return canceled; }
        public void setCanceled(boolean flag) { this.canceled = flag; }
    }
}
