/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.event;

import net.neoforged.bus.api.Event;

public class RegisterCommandsEvent extends Event {
    public Object getDispatcher() { return null; }
    public Object getBuildContext() { return null; }
    public Object getCommandSelection() { return null; }
}
