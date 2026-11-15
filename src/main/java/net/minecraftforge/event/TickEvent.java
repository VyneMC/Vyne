/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.event;

import net.neoforged.bus.api.Event;

public class TickEvent extends Event {
    public final Phase phase;
    public final Type type;

    protected TickEvent(Type type, Phase phase) {
        this.type = type;
        this.phase = phase;
    }

    public Phase getPhase() { return phase; }
    public Type getType() { return type; }

    public enum Phase { START, END }

    public enum Type { WORLD, PLAYER, CLIENT, SERVER, RENDER, LEVEL }

    public static class ClientTickEvent extends TickEvent {
        public ClientTickEvent(Phase phase) { super(Type.CLIENT, phase); }
        public static class Pre extends ClientTickEvent { public Pre() { super(Phase.START); } }
        public static class Post extends ClientTickEvent { public Post() { super(Phase.END); } }
    }

    public static class ServerTickEvent extends TickEvent {
        public ServerTickEvent() { super(Type.SERVER, Phase.END); }
        public ServerTickEvent(Phase phase) { super(Type.SERVER, phase); }
        public Object getServer() { return null; }
        public static class Pre extends ServerTickEvent { public Pre() { super(Phase.START); } }
        public static class Post extends ServerTickEvent { public Post() { super(Phase.END); } }
    }

    public static class PlayerTickEvent extends TickEvent {
        public final Object player;
        public PlayerTickEvent(Phase phase, Object player) {
            super(Type.PLAYER, phase);
            this.player = player;
        }
        public static class Pre extends PlayerTickEvent { public Pre(Object player) { super(Phase.START, player); } }
        public static class Post extends PlayerTickEvent { public Post(Object player) { super(Phase.END, player); } }
    }

    public static class WorldTickEvent extends TickEvent {
        public final Object world;
        public WorldTickEvent(Phase phase, Object world) {
            super(Type.WORLD, phase);
            this.world = world;
        }
    }

    public static class LevelTickEvent extends TickEvent {
        public final Object level;
        public LevelTickEvent(Phase phase, Object level) {
            super(Type.LEVEL, phase);
            this.level = level;
        }
        public static class Pre extends LevelTickEvent { public Pre(Object level) { super(Phase.START, level); } }
        public static class Post extends LevelTickEvent { public Post(Object level) { super(Phase.END, level); } }
    }

    public static class RenderTickEvent extends TickEvent {
        public final float renderTickTime;
        public RenderTickEvent(Phase phase, float renderTickTime) {
            super(Type.RENDER, phase);
            this.renderTickTime = renderTickTime;
        }
    }
}
