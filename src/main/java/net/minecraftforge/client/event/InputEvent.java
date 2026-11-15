/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.client.event;

import net.neoforged.bus.api.Event;

public class InputEvent extends Event {
    public static class Key extends InputEvent {
        public int getKey() { return 0; }
        public int getScanCode() { return 0; }
        public int getAction() { return 0; }
        public int getModifiers() { return 0; }
    }

    public static class MouseButton extends InputEvent {
        public int getButton() { return 0; }
        public int getAction() { return 0; }
        public int getModifiers() { return 0; }
        public static class Pre extends MouseButton {}
        public static class Post extends MouseButton {}
    }

    public static class MouseScrollingEvent extends InputEvent
            implements net.neoforged.bus.api.ICancelableEvent {
        private double deltaY;
        private double deltaX;
        private boolean canceled;

        public MouseScrollingEvent() {}
        public MouseScrollingEvent(double deltaY) { this.deltaY = deltaY; }

        public double getScrollDeltaX() { return deltaX; }
        public double getScrollDeltaY() { return deltaY; }
        public double getDeltaY() { return deltaY; }
        public double getDeltaX() { return deltaX; }

        public boolean isCanceled() { return canceled; }
        public void setCanceled(boolean flag) { this.canceled = flag; }
    }

    public static class InteractionKeyMappingTriggered extends InputEvent {
        public int getKeyMappingType() { return 0; }
        public Object getKeyMapping() { return null; }
        public Object getHand() { return null; }
    }
}
