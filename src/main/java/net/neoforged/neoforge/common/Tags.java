/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.neoforged.neoforge.common;

import dev.stolym.vyne.util.ReflectionHelper;
import dev.stolym.vyne.vcl.FabricLoaderProbes;
import dev.stolym.vyne.vcl.VCLManager;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class Tags {
    private Tags() {}

    private static ClassLoader resolveGameClassLoaderForTags() {
        ClassLoader k = VCLManager.getCanonicalKnotLoader();
        if (k != null && canResolveCoreMinecraft(k)) {
            return k;
        }
        for (ClassLoader probe : FabricLoaderProbes.reflectionProbesForFabricLauncherBase()) {
            if (probe == null) {
                continue;
            }
            try {
                Class<?> flb = Class.forName(
                    "net.fabricmc.loader.impl.launch.FabricLauncherBase", false, probe);
                Object launcher = flb.getMethod("getLauncher").invoke(null);
                if (launcher == null) {
                    continue;
                }
                Object tcl = launcher.getClass().getMethod("getTargetClassLoader").invoke(launcher);
                if (tcl instanceof ClassLoader fabric && canResolveCoreMinecraft(fabric)) {
                    return fabric;
                }
            } catch (Throwable ignored) {
            }
        }
        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
        if (ctx != null && canResolveCoreMinecraft(ctx)) {
            return ctx;
        }
        ClassLoader own = Tags.class.getClassLoader();
        if (own != null && canResolveCoreMinecraft(own)) {
            return own;
        }
        throw new IllegalStateException(
            "Vyne: Tags could not resolve a class loader for Minecraft types "
                + "(no loader can load net.minecraft.class_2960 and class_6862)");
    }

    private static boolean canResolveCoreMinecraft(ClassLoader cl) {
        try {
            Class.forName("net.minecraft.class_2960", false, cl);
            Class.forName("net.minecraft.class_6862", false, cl);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings("rawtypes")
    private static net.minecraft.class_6862 tagKeyForCommonRegistry(String mcRegistryPathSegment,
            String pathUnderCommon) {
        ClassLoader knot = resolveGameClassLoaderForTags();
        try {
            Class<?> c2960 = Class.forName("net.minecraft.class_2960", false, knot);
            Class<?> c5321 = Class.forName("net.minecraft.class_5321", false, knot);
            Class<?> c6862 = Class.forName("net.minecraft.class_6862", false, knot);
            Object registryRl = ReflectionHelper.newResourceLocationKnot(
                c2960, "minecraft", mcRegistryPathSegment);
            if (registryRl == null) {
                registryRl = c2960.getConstructor(String.class, String.class)
                    .newInstance("minecraft", mcRegistryPathSegment);
            }
            Object registryKey = invokeRegistryKeyFactory(c5321, registryRl);
            Object tagRl = ReflectionHelper.newResourceLocationKnot(c2960, "c", pathUnderCommon);
            if (tagRl == null) {
                tagRl = c2960.getConstructor(String.class, String.class)
                    .newInstance("c", pathUnderCommon);
            }
            Object holder = invokeTagKeyFactory(c6862, c5321, registryKey, tagRl);
            return (net.minecraft.class_6862) holder;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Object invokeRegistryKeyFactory(Class<?> resourceKeyClass, Object locationRl)
            throws ReflectiveOperationException {
        for (String name : new String[] {"createRegistryKey", "method_29177"}) {
            try {
                Method m = resourceKeyClass.getMethod(name, locationRl.getClass());
                if (Modifier.isStatic(m.getModifiers()) && resourceKeyClass.isAssignableFrom(m.getReturnType())) {
                    Object out = m.invoke(null, locationRl);
                    if (out != null) {
                        return out;
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (Method m : resourceKeyClass.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 1) {
                continue;
            }
            Class<?> p0 = m.getParameterTypes()[0];
            if (!p0.isInstance(locationRl)) {
                continue;
            }
            if (!resourceKeyClass.isAssignableFrom(m.getReturnType())) {
                continue;
            }
            Object out = m.invoke(null, locationRl);
            if (out != null) {
                return out;
            }
        }
        throw new NoSuchMethodException("No static 1-arg registry ResourceKey factory on "
            + resourceKeyClass.getName());
    }

    private static Object invokeTagKeyFactory(Class<?> tagKeyClass, Class<?> resourceKeyClass,
            Object registryKey, Object tagLocation) throws ReflectiveOperationException {
        for (String name : new String[] {"create", "method_40092"}) {
            try {
                Method m = tagKeyClass.getMethod(name, resourceKeyClass, tagLocation.getClass());
                if (Modifier.isStatic(m.getModifiers()) && tagKeyClass.isAssignableFrom(m.getReturnType())) {
                    Object out = m.invoke(null, registryKey, tagLocation);
                    if (out != null) {
                        return out;
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (Method m : tagKeyClass.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (!p[0].isInstance(registryKey) || !p[1].isInstance(tagLocation)) {
                continue;
            }
            if (!tagKeyClass.isAssignableFrom(m.getReturnType())) {
                continue;
            }
            Object out = m.invoke(null, registryKey, tagLocation);
            if (out != null) {
                return out;
            }
        }
        throw new NoSuchMethodException("No static 2-arg TagKey factory on " + tagKeyClass.getName());
    }

    public static final class Blocks {
        private Blocks() {}

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 ORES = blockTagCommon("ores");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 ORES_IN_GROUND_STONE =
            blockTagCommon("ores_in_ground/stone");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 ORES_IN_GROUND_DEEPSLATE =
            blockTagCommon("ores_in_ground/deepslate");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 STORAGE_BLOCKS = blockTagCommon("storage_blocks");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 RELOCATION_NOT_SUPPORTED =
            blockTagCommon("relocation_not_supported");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 GLASS_BLOCKS_COLORLESS =
            blockTagCommon("glass_blocks/colorless");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 GLASS_PANES = blockTagCommon("glass_panes");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 GLASS_PANES_COLORLESS =
            blockTagCommon("glass_panes/colorless");

        @SuppressWarnings("rawtypes")
        private static net.minecraft.class_6862 blockTagCommon(String pathUnderCommon) {
            return Tags.tagKeyForCommonRegistry("block", pathUnderCommon);
        }
    }

    public static final class Items {
        private Items() {}

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 ORES_IRON = itemTagCommon("ores/iron");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 ORES_GOLD = itemTagCommon("ores/gold");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 ORES_COPPER = itemTagCommon("ores/copper");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 ORES = itemTagCommon("ores");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 ORES_IN_GROUND_STONE =
            itemTagCommon("ores_in_ground/stone");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 ORES_IN_GROUND_DEEPSLATE =
            itemTagCommon("ores_in_ground/deepslate");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 STORAGE_BLOCKS = itemTagCommon("storage_blocks");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 NUGGETS = itemTagCommon("nuggets");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 BUCKETS = itemTagCommon("buckets");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 RAW_MATERIALS = itemTagCommon("raw_materials");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 ENCHANTABLES = itemTagCommon("enchantables");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 GLASS_BLOCKS_COLORLESS =
            itemTagCommon("glass_blocks/colorless");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 GLASS_PANES = itemTagCommon("glass_panes");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 GLASS_PANES_COLORLESS =
            itemTagCommon("glass_panes/colorless");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 DRINKS = itemTagCommon("drinks");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 TOOLS_WRENCH = itemTagCommon("tools/wrench");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 NUGGETS_IRON = itemTagCommon("nuggets/iron");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 FOODS = itemTagCommon("foods");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 FOODS_FRUIT = itemTagCommon("foods/fruit");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 FOODS_VEGETABLE = itemTagCommon("foods/vegetable");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 FOODS_BERRY = itemTagCommon("foods/berry");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 FOODS_BREAD = itemTagCommon("foods/bread");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 FOODS_COOKIE = itemTagCommon("foods/cookie");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 FOODS_RAW_MEAT = itemTagCommon("foods/raw_meat");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 FOODS_COOKED_MEAT = itemTagCommon("foods/cooked_meat");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 FOODS_RAW_FISH = itemTagCommon("foods/raw_fish");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 FOODS_COOKED_FISH = itemTagCommon("foods/cooked_fish");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 FOODS_SOUP = itemTagCommon("foods/soup");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 FOODS_CANDY = itemTagCommon("foods/candy");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 FOODS_GOLDEN = itemTagCommon("foods/golden");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 FOODS_EDIBLE_WHEN_PLACED =
            itemTagCommon("foods/edible_when_placed");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 FOODS_FOOD_POISONING =
            itemTagCommon("foods/food_poisoning");

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.class_6862 FOODS_DOUGH = itemTagCommon("foods/dough");

        @SuppressWarnings("rawtypes")
        private static net.minecraft.class_6862 itemTagCommon(String pathUnderCommon) {
            return Tags.tagKeyForCommonRegistry("item", pathUnderCommon);
        }
    }
}
