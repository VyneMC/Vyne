/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.compat;

import net.neoforged.neoforge.fluids.FluidType;

public final class FluidTypeBridge {
    private FluidTypeBridge() {}

    private static final FluidType EMPTY =
        new FluidType(FluidType.Properties.create());

    public static FluidType getFluidType(Object receiver) {
        return EMPTY;
    }
}
