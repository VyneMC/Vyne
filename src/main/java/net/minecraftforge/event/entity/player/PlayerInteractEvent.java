/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.event.entity.player;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancelableEvent;

public class PlayerInteractEvent extends Event implements ICancelableEvent {
    private boolean canceled;

    public Object getEntity() { return null; }
    public Object getLevel() { return null; }
    public Object getPos() { return null; }
    public Object getHand() { return null; }
    public Object getItemStack() { return null; }
    public Object getHitVec() { return null; }

    public boolean isCanceled() { return canceled; }
    public void setCanceled(boolean flag) { this.canceled = flag; }

    public void setCancellationResult(Object result) {}
    public Object getCancellationResult() { return null; }

    public static class RightClickBlock extends PlayerInteractEvent {
        public Object getFace() { return null; }
        public Object getUseBlock() { return null; }
        public Object getUseItem() { return null; }
        public void setUseBlock(Object result) {}
        public void setUseItem(Object result) {}
    }

    public static class LeftClickBlock extends PlayerInteractEvent {
        public Object getAction() { return null; }
    }

    public static class RightClickItem extends PlayerInteractEvent {}
    public static class LeftClickEmpty extends PlayerInteractEvent {}
    public static class RightClickEmpty extends PlayerInteractEvent {}
    public static class EntityInteract extends PlayerInteractEvent {
        public Object getTarget() { return null; }
    }
    public static class EntityInteractSpecific extends EntityInteract {}
}
