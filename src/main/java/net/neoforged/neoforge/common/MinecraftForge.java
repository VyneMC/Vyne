/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.common;

public final class MinecraftForge {
    public static final net.neoforged.neoforge.eventbus.api.IEventBus EVENT_BUS = (net.neoforged.neoforge.eventbus.api.IEventBus) net.neoforged.bus.api.VyneEventBus.global();

    private MinecraftForge() {}
}
