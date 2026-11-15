/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.event.entity.player;

import net.neoforged.bus.api.Event;

public class PlayerWakeUpEvent extends Event {
    public Object getEntity() { return null; }
    public boolean wakeImmediately() { return false; }
    public boolean updateLevel() { return false; }
}
