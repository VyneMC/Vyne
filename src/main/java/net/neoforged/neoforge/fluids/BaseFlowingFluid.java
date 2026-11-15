/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.fluids;

public abstract class BaseFlowingFluid extends net.minecraft.class_3609 {
    private final Properties properties;

    protected BaseFlowingFluid(Properties properties) {
        super();
        this.properties = properties;
    }

    public FluidType getFluidType() {
        if (properties == null || properties.fluidType == null) {
            return null;
        }
        return properties.fluidType.get();
    }

    public static class Properties {
        private final java.util.function.Supplier<? extends FluidType> fluidType;
        private final java.util.function.Supplier<? extends net.minecraft.class_3611> still;
        private final java.util.function.Supplier<? extends net.minecraft.class_3611> flowing;

        public Properties(java.util.function.Supplier<? extends FluidType> fluidType,
                          java.util.function.Supplier<? extends net.minecraft.class_3611> still,
                          java.util.function.Supplier<? extends net.minecraft.class_3611> flowing) {
            this.fluidType = fluidType;
            this.still = still;
            this.flowing = flowing;
        }

        public Properties bucket(java.util.function.Supplier<? extends net.minecraft.class_1792> bucket) { return this; }
        public Properties block(java.util.function.Supplier<? extends net.minecraft.class_2404> block) { return this; }
        public Properties slopeFindDistance(int slopeFindDistance) { return this; }
        public Properties levelDecreasePerBlock(int levelDecreasePerBlock) { return this; }
        public Properties explosionResistance(float explosionResistance) { return this; }
        public Properties tickRate(int tickRate) { return this; }
    }

    public static class Source extends BaseFlowingFluid {
        public Source(Properties properties) {
            super(properties);
        }
    }

    public static class Flowing extends BaseFlowingFluid {
        public Flowing(Properties properties) {
            super(properties);
        }
    }
}
