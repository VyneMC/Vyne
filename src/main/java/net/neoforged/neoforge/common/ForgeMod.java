/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.common;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.VyneEventBus;

public final class ForgeMod {
    public static final IEventBus EVENT_BUS = new VyneEventBus();

    public static void registerConfigScreen(@SuppressWarnings("rawtypes") java.util.function.Function function) {
    }

    public static void enableMilkFluid() {
    }
}
