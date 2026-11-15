/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.client.event;

import net.neoforged.bus.api.Event;

public class ModelEvent extends Event {
    public static class RegisterAdditional extends ModelEvent {
        public void register(Object modelLocation) {
        }
    }

    public static class BakingCompleted extends ModelEvent {}

    public static class ModifyBakingResult extends ModelEvent {}

    public static class RegisterGeometryLoaders extends ModelEvent {
        public void register(Object id, Object loader) {}
    }
}
