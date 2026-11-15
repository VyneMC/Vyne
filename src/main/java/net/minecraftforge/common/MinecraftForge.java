/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.minecraftforge.common;

import net.minecraftforge.eventbus.api.IEventBus;
import net.neoforged.bus.api.VyneEventBus;

public final class MinecraftForge {
    public static final IEventBus EVENT_BUS = VyneEventBus.global();

    public static void registerConfigScreen(@SuppressWarnings("rawtypes") java.util.function.Function function) {
    }
}
