/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.fluids;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.common.SoundAction;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class FluidType {
    public static final int BUCKET_VOLUME = 1000;
    protected final Properties properties;

    public FluidType(Properties properties) {
        this.properties = properties;
    }

    public Object getDescription() {
        return buildComponent(getDescriptionId());
    }

    public net.minecraft.class_2561 getDescription(FluidStack stack) {
        return buildComponent(getDescriptionId(stack));
    }

    private static volatile java.lang.reflect.Method CACHED_TRANSLATABLE_FACTORY;

    private static net.minecraft.class_2561 buildComponent(String key) {
        try {
            java.lang.reflect.Method m = CACHED_TRANSLATABLE_FACTORY;
            if (m == null) {
                Class<?> cls = net.minecraft.class_2561.class;
                for (String name : new String[] {
                        "translatable", "literal",
                        "method_43471", "method_43470" }) {
                    try {
                        m = cls.getMethod(name, String.class);
                        CACHED_TRANSLATABLE_FACTORY = m;
                        break;
                    } catch (NoSuchMethodException ignored) {
                    }
                }

                if (m == null) {
                    for (java.lang.reflect.Method cand : cls.getMethods()) {
                        if (!java.lang.reflect.Modifier.isStatic(cand.getModifiers())) continue;
                        Class<?>[] pts = cand.getParameterTypes();
                        if (pts.length != 1 || pts[0] != String.class) continue;
                        if (!net.minecraft.class_2561.class.isAssignableFrom(cand.getReturnType())) continue;
                        m = cand;
                        CACHED_TRANSLATABLE_FACTORY = m;
                        break;
                    }
                }
            }
            if (m != null) {
                Object result = m.invoke(null, key == null ? "" : key);
                if (result instanceof net.minecraft.class_2561 c) return c;
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    public String getDescriptionId() {
        return properties.descriptionId;
    }

    public String getDescriptionId(FluidStack stack) {
        return properties.descriptionId;
    }

    public int getLightLevel() { return properties.lightLevel; }
    public int getDensity() { return properties.density; }
    public int getTemperature() { return properties.temperature; }
    public int getViscosity() { return properties.viscosity; }
    public Object getRarity() { return properties.rarity; }

    public Object getBlockPathType(Object state, Object level, Object pos, Object mob, boolean canFluidLog) {
        return null; 
    }

    public void initializeClient(Consumer<Object> consumer) {}

    public static class Properties {
        String descriptionId = "fluid_type.unregistered";
        double motionScale = 0.014D;
        boolean canPushEntity = true;
        boolean canSwim = true;
        boolean canDrown = true;
        float fallDistanceModifier = 0.5F;
        boolean canExtinguish = false;
        boolean canConvertToSource = false;
        boolean supportsBoating = false;
        Object pathType = null;
        Object adjacentPathType = null;
        final Map<Object, Object> sounds = new HashMap<>();
        boolean canHydrate = false;
        int lightLevel = 0;
        int density = 1000;
        int temperature = 300;
        int viscosity = 1000;
        Object rarity = null;

        protected Properties() {}
        public static Properties create() { return new Properties(); }

        public Properties descriptionId(String id) { this.descriptionId = id; return this; }
        public Properties motionScale(double v) { this.motionScale = v; return this; }
        public Properties canPushEntity(boolean v) { this.canPushEntity = v; return this; }
        public Properties canSwim(boolean v) { this.canSwim = v; return this; }
        public Properties canDrown(boolean v) { this.canDrown = v; return this; }
        public Properties fallDistanceModifier(float v) { this.fallDistanceModifier = v; return this; }
        public Properties canExtinguish(boolean v) { this.canExtinguish = v; return this; }
        public Properties canConvertToSource(boolean v) { this.canConvertToSource = v; return this; }
        public Properties supportsBoating(boolean v) { this.supportsBoating = v; return this; }
        public Properties pathType(Object v) { this.pathType = v; return this; }
        public Properties adjacentPathType(Object v) { this.adjacentPathType = v; return this; }
        public Properties sound(Object a, Object b) { this.sounds.put(a, b); return this; }
        public Properties sound(SoundAction a, Object b) { this.sounds.put(a, b); return this; }
        public Properties sound(SoundAction a, SoundEvent b) { this.sounds.put(a, b); return this; }
        public Properties canHydrate(boolean v) { this.canHydrate = v; return this; }
        public Properties lightLevel(int v) { this.lightLevel = v; return this; }
        public Properties density(int v) { this.density = v; return this; }
        public Properties temperature(int v) { this.temperature = v; return this; }
        public Properties viscosity(int v) { this.viscosity = v; return this; }
        public Properties rarity(Object v) { this.rarity = v; return this; }
    }
}
