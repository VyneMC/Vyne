/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.fml.event.config;

import net.neoforged.bus.api.Event;

public class ModConfigEvent extends Event {
    private final Object config;

    public ModConfigEvent() { this(null); }
    public ModConfigEvent(Object config) { this.config = config; }

    public Object getConfig() { return config; }

    public static class Loading extends ModConfigEvent {
        public Loading() { super(null); }
        public Loading(Object config) { super(config); }
    }

    public static class Reloading extends ModConfigEvent {
        public Reloading() { super(null); }
        public Reloading(Object config) { super(config); }
    }

    public static class Unloading extends ModConfigEvent {
        public Unloading() { super(null); }
        public Unloading(Object config) { super(config); }
    }
}
