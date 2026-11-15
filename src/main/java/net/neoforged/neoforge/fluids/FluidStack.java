/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.fluids;

public class FluidStack {
    public static final FluidStack EMPTY = empty();

    private static FluidStack empty() {
        Object fluid = null;
        return new FluidStack(fluid, 0);
    }
    public static final com.mojang.serialization.Codec<FluidStack> CODEC =
        com.mojang.serialization.Codec.unit(EMPTY);

    public static final com.mojang.serialization.Codec<FluidStack> FLUID_NON_EMPTY_CODEC = CODEC;

    public static final com.mojang.serialization.Codec<java.util.Optional<FluidStack>> OPTIONAL_CODEC =
        buildOptionalCodec();

    @SuppressWarnings("unchecked")
    private static com.mojang.serialization.Codec<java.util.Optional<FluidStack>> buildOptionalCodec() {
        try {
            Class<?> extra = Class.forName("net.minecraft.class_5699");
            java.lang.reflect.Method m = extra.getMethod("method_57155", com.mojang.serialization.Codec.class);
            Object out = m.invoke(null, CODEC);
            return (com.mojang.serialization.Codec<java.util.Optional<FluidStack>>) out;
        } catch (Throwable t) {
            return com.mojang.serialization.Codec.unit(java.util.Optional.empty());
        }
    }

    public static final net.minecraft.class_9139<?, ?> STREAM_CODEC = buildStreamCodec();

    public static final net.minecraft.class_9139<?, ?> OPTIONAL_STREAM_CODEC = buildOptionalStreamCodec();

    @SuppressWarnings("unchecked")
    private static net.minecraft.class_9139<?, ?> buildStreamCodec() {
        try {
            Class<?> byteBufCodecs = Class.forName("net.minecraft.class_9135");
            java.lang.reflect.Method fromCodec =
                byteBufCodecs.getMethod("method_57987", com.mojang.serialization.Codec.class);
            Object sc = fromCodec.invoke(null, CODEC);
            return (net.minecraft.class_9139<?, ?>) sc;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static net.minecraft.class_9139<?, ?> buildOptionalStreamCodec() {
        try {
            net.minecraft.class_9139<?, ?> sc = STREAM_CODEC;
            if (sc == null) return null;
            Class<?> byteBufCodecs = Class.forName("net.minecraft.class_9135");
            Class<?> streamCodec = Class.forName("net.minecraft.class_9139");
            java.lang.reflect.Method optional =
                byteBufCodecs.getMethod("method_56382", streamCodec);
            return (net.minecraft.class_9139<?, ?>) optional.invoke(null, sc);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private final Object fluid;

    private int amount;

    public FluidStack(Object fluid, int amount) {
        this.fluid = fluid;
        this.amount = amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public FluidStack(net.minecraft.class_3611 fluid, int amount) {
        this.fluid = fluid;
        this.amount = amount;
    }

    public Object getFluid() {
        return fluid;
    }

    public int getAmount() {
        return amount;
    }

    public boolean isEmpty() {
        return amount <= 0 || fluid == null;
    }

    public FluidStack copy() {
        return new FluidStack(fluid, amount);
    }

    public boolean isFluidEqual(FluidStack other) {
        if (other == null) return false;
        return java.util.Objects.equals(this.fluid, other.fluid);
    }

    public net.minecraft.class_2561 getHoverName() {
        try {
            Object type = FluidTypeBridge.getFluidType(fluid);
            if (type instanceof FluidType ft) {
                return ft.getDescription(this);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public net.minecraft.class_2561 getDisplayName() {
        return getHoverName();
    }

    public String getDescriptionId() {
        try {
            Object type = FluidTypeBridge.getFluidType(fluid);
            if (type instanceof FluidType ft) {
                return ft.getDescriptionId(this);
            }
        } catch (Throwable ignored) {}
        return "fluid.unknown";
    }

    public String getTranslationKey() {
        return getDescriptionId();
    }

    public net.minecraft.class_2487 writeToNBT(net.minecraft.class_2487 tag) {
        if (tag == null) return null;
        try {
            java.lang.reflect.Method putInt = tag.getClass().getMethod("method_10569", String.class, int.class);
            putInt.invoke(tag, "Amount", amount);
        } catch (Throwable ignored) {}
        try {
            String fluidId = resolveFluidId(fluid);
            if (fluidId != null) {
                java.lang.reflect.Method putString = tag.getClass().getMethod("method_10582", String.class, String.class);
                putString.invoke(tag, "FluidName", fluidId);
            }
        } catch (Throwable ignored) {}
        return tag;
    }

    public static FluidStack loadFluidStackFromNBT(net.minecraft.class_2487 tag) {
        return EMPTY;
    }

    private static String resolveFluidId(Object fluid) {
        if (fluid == null) return "minecraft:empty";
        try {
            Class<?> registries = Class.forName("net.minecraft.class_7923", false, fluid.getClass().getClassLoader());
            java.lang.reflect.Field fluidReg = registries.getField("field_41173");
            Object reg = fluidReg.get(null);
            for (String name : new String[]{"method_10221", "getKey"}) {
                try {
                    java.lang.reflect.Method m = reg.getClass().getMethod(name, Object.class);
                    Object rl = m.invoke(reg, fluid);
                    if (rl != null) return rl.toString();
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
