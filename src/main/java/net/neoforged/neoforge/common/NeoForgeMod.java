/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.common;

import net.minecraft.core.Holder;
import net.neoforged.neoforge.fluids.FluidType;

public class NeoForgeMod {
    private static final FluidType EMPTY_FLUID_TYPE_INSTANCE = new FluidType(FluidType.Properties.create().descriptionId("fluid_type.neoforge.empty"));
    public static final net.minecraft.core.Holder<FluidType> EMPTY_TYPE =
        new net.neoforged.neoforge.registries.DeferredHolder<FluidType>(null, "neoforge", "empty", () -> EMPTY_FLUID_TYPE_INSTANCE);

    private static FluidType fluidTypeFor(String descriptionId) {
        return new FluidType(FluidType.Properties.create().descriptionId(descriptionId));
    }

    public static final Holder<FluidType> WATER_TYPE =
        Holder.direct(fluidTypeFor("fluid_type.minecraft.water"));
    public static final Holder<FluidType> LAVA_TYPE =
        Holder.direct(fluidTypeFor("fluid_type.minecraft.lava"));

    public static void enableMilkFluid() {
    }
}
