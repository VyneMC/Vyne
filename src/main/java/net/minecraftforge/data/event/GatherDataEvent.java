/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.data.event;

import net.neoforged.bus.api.Event;

public class GatherDataEvent extends Event {
    public Object getGenerator() { return null; }
    public Object getLookupProvider() { return null; }
    public Object getExistingFileHelper() { return null; }
    public Object getModContainer() { return null; }
    public boolean includeServer() { return false; }
    public boolean includeClient() { return false; }
    public boolean includeDev() { return false; }
    public boolean includeReports() { return false; }
    public String[] getMods() { return new String[0]; }
}
