/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.event.village;

import net.neoforged.bus.api.Event;

public class VillagerTradesEvent extends Event {
    private final java.util.Map<Integer, java.util.List<Object>> trades = new java.util.HashMap<>();

    public VillagerTradesEvent() {
        for (int i = 1; i <= 5; i++) trades.put(i, new java.util.ArrayList<>());
    }

    public Object getType() { return null; }
    public java.util.Map<Integer, java.util.List<Object>> getTrades() { return trades; }
}
