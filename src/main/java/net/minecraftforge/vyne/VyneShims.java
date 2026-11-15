/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.minecraftforge.vyne;

import java.util.*;

public final class VyneShims {
    private static final Map<Object, List<PendingPropertySetting>> PENDING_SETTINGS = new WeakHashMap<>();

    public static Object setValueLazy(Object blockState, Object property, Object value) {
        Object block = dev.stolym.vyne.util.ReflectionHelper.call(blockState, "getBlock", "method_9590");

        Object def = dev.stolym.vyne.util.ReflectionHelper.call(block, "getStateDefinition", "method_9562");
        Collection<?> props = (Collection<?>) dev.stolym.vyne.util.ReflectionHelper.call(def, "getProperties", "method_9557");

        if (props == null || props.isEmpty()) {
            synchronized (PENDING_SETTINGS) {
                PENDING_SETTINGS.computeIfAbsent(block, k -> new ArrayList<>())
                                .add(new PendingPropertySetting(property, (Comparable<?>) value));
            }
            return blockState;
        }

        try {
            Object applied = tryInvokeSetValue(blockState, property, value);
            return applied != null ? applied : blockState;
        } catch (Throwable ignored) {
            return blockState;
        }
    }

    public static void applyPendingSettings(Object block) {
        List<PendingPropertySetting> settings;
        synchronized (PENDING_SETTINGS) {
            settings = PENDING_SETTINGS.remove(block);
        }

        if (settings != null) {
            Object state = dev.stolym.vyne.util.ReflectionHelper.call(block, "defaultBlockState", "method_9576");
            for (PendingPropertySetting setting : settings) {
                try {
                    Object applied = tryInvokeSetValue(state, setting.property, setting.value);
                    if (applied != null) {
                        state = applied;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            try {
                java.lang.reflect.Field field = findDeclaredStateDefinitionField(block.getClass());
                if (field == null) {
                    throw new NoSuchFieldException("stateDefinition/field_10647 on Block hierarchy");
                }
                field.setAccessible(true);
                Object def = field.get(block);

                java.lang.reflect.Field defaultStateField = def.getClass().getDeclaredField("defaultState");
                defaultStateField.setAccessible(true);
                defaultStateField.set(def, state);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static net.minecraft.class_2960 resourceLocationOf(String namespace, String path) {
        return new net.minecraft.class_2960(namespace, path);
    }

    public static net.minecraft.class_2960 resourceLocationOf(String full) {
        if (full.contains(":")) {
            String[] parts = full.split(":", 2);
            return new net.minecraft.class_2960(parts[0], parts[1]);
        }
        return new net.minecraft.class_2960("minecraft", full);
    }

    public static Object createCreativeTabBuilderCompat() {
        ClassLoader cl = minecraftCapableLoader();
        try {
            Class<?> tabClass = Class.forName("net.minecraft.class_1761", false, cl);
            Class<?> rowEnum = Class.forName("net.minecraft.class_1761$class_7915", false, cl);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object row = java.lang.Enum.valueOf((Class<? extends java.lang.Enum>) rowEnum, "TOP");
            java.lang.reflect.Method factory =
                tabClass.getMethod("method_47307", rowEnum, int.class);
            return factory.invoke(null, row, 0);
        } catch (Throwable t) {
            dev.stolym.vyne.util.VyneLogger.warn(
                "[VyneShims] createCreativeTabBuilderCompat failed on loader="
                    + (cl == null ? "null" : cl.getClass().getName())
                    + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        return null;
    }

    public static Object creativeTabBuilderWithTabsBefore(Object builder, Object resourceKeyArray) {
        return builder;
    }

    private static ClassLoader minecraftCapableLoader() {
        ClassLoader canonical = dev.stolym.vyne.vcl.VCLManager.getCanonicalKnotLoader();
        if (canonical != null) return canonical;
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != null) return tccl;
        return VyneShims.class.getClassLoader();
    }

    public static void registerItemPropertyCompat(Object item, Object resourceLocation, Object predicate) {
        if (item == null || resourceLocation == null) {
            return;
        }
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Class<?> holder = Class.forName("net.minecraft.class_5272", false, cl);
            for (java.lang.reflect.Method m : holder.getDeclaredMethods()) {
                if (!"method_27879".equals(m.getName()) || m.getParameterCount() != 3) {
                    continue;
                }
                Class<?>[] pt = m.getParameterTypes();
                Object third = adaptItemPropertyThirdArg(pt[2], predicate, cl);
                try {
                    m.setAccessible(true);
                    m.invoke(null, item, resourceLocation, third);
                    return;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static Object adaptItemPropertyThirdArg(Class<?> expected, Object predicate, ClassLoader cl) {
        if (predicate == null) {
            return null;
        }
        if (expected.isInstance(predicate)) {
            return predicate;
        }
        try {
            Class<?> legacy = Class.forName("net.minecraft.class_1800", false, cl);
            if (expected.isAssignableFrom(legacy)) {
                return expected.cast(predicate);
            }
        } catch (Throwable ignored) {
        }
        return predicate;
    }

    public static void registerDefaultStateCompat(Object block, Object state) {
        if (block == null || state == null) {
            return;
        }
        for (String methodName : new String[]{"registerDefaultState", "method_9599"}) {
            for (java.lang.reflect.Method m : block.getClass().getMethods()) {
                if (!m.getName().equals(methodName) || m.getParameterCount() != 1) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    m.invoke(block, state);
                    return;
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static boolean isActiveAndMatches(Object keyMapping, Object key) {
        if (keyMapping == null || key == null) return false;
        try {
            Object bound = dev.stolym.vyne.util.ReflectionHelper.call(
                keyMapping, "getKey", "method_1418");
            if (bound == null) return false;
            return bound.equals(key);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Object unwrapDeferredSoundTypeArg(Object o) {
        if (o == null) {
            return null;
        }
        try {
            ClassLoader cl = o.getClass().getClassLoader();
            Class<?> soundType = Class.forName("net.minecraft.class_2498", false,
                cl != null ? cl : VyneShims.class.getClassLoader());
            if (soundType.isInstance(o)) {
                return o;
            }
        } catch (Throwable ignored) {
        }
        if (o instanceof java.util.function.Supplier<?> s) {
            try {
                Object v = s.get();
                if (v != null) {
                    return v;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            java.lang.reflect.Method m = o.getClass().getMethod("get");
            if (m.getParameterCount() == 0) {
                Object v = m.invoke(o);
                if (v != null) {
                    return v;
                }
            }
        } catch (Throwable ignored) {
        }
        return o;
    }

    public static String bridgeClass3542Method15434(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof Enum<?> e) {
            return e.name();
        }
        try {
            java.lang.reflect.Method m = o.getClass().getMethod("getSerializedName");
            Object r = m.invoke(o);
            if (r instanceof String s) {
                return s;
            }
        } catch (Throwable ignored) {
        }
        return String.valueOf(o);
    }

    private static class PendingPropertySetting {
        final Object property;
        final Comparable<?> value;

        PendingPropertySetting(Object property, Comparable<?> value) {
            this.property = property;
            this.value = value;
        }
    }

    private static java.lang.reflect.Field findDeclaredStateDefinitionField(Class<?> start) {
        for (Class<?> c = start; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField("field_10647");
            } catch (NoSuchFieldException ignored) {
            }
            try {
                return c.getDeclaredField("stateDefinition");
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    public static void invokeSetCraftingSizeIfPresent(Object menu, int width, int height) {
        if (menu == null) {
            return;
        }
        try {
            java.lang.reflect.Method m =
                menu.getClass().getMethod("setCraftingSize", int.class, int.class);
            m.invoke(menu, width, height);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    public static Object recipeTypeSimpleCompat(net.minecraft.class_2960 id) {
        if (id == null) {
            return null;
        }
        ClassLoader cl = minecraftCapableLoader();
        try {
            Class<?> rt = Class.forName("net.minecraft.class_3956", true, cl);
            java.lang.reflect.Method m = rt.getMethod("method_17726", String.class);
            return m.invoke(null, id.toString());
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void invokeShapedRecipePatternSetCraftingSizeIfPresent(int width, int height) {
        if (!dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
            return;
        }
        String internal = dev.stolym.vyne.mappings.MappingRegistry.get()
            .mapClass("net/minecraft/world/item/crafting/ShapedRecipePattern");
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = VyneShims.class.getClassLoader();
        }
        try {
            Class<?> owner = Class.forName(internal.replace('/', '.'), false, cl);
            java.lang.reflect.Method m =
                owner.getMethod("setCraftingSize", int.class, int.class);
            if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                return;
            }
            m.invoke(null, width, height);
        } catch (ReflectiveOperationException ignored) {
        } catch (Throwable ignored) {
        }
    }

    private static Object tryInvokeSetValue(Object blockState, Object property, Object value) {
        if (blockState == null || property == null) {
            return null;
        }
        for (String name : new String[]{"method_11657", "setValue"}) {
            for (java.lang.reflect.Method m : blockState.getClass().getMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != 2) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    return m.invoke(blockState, property, value);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    public static Object getRegistrateEntry(Object entry) {
        if (entry == null) {
            System.err.println("[VyneShims-DEBUG] getRegistrateEntry received NULL entry.");
            return null;
        }
        try {
            String entryClassName = entry.getClass().getName();
            boolean isFluidEntry = entryClassName.contains("FluidEntry");
            java.lang.reflect.Method getMethod = entry.getClass().getMethod("get");
            Object val = getMethod.invoke(entry);
            if (isFluidEntry) {
                Object lookedUp = tryLookupRegistrateEntry(entry);
                if (lookedUp != null) {
                    return lookedUp;
                }
            }
            if (val != null) {
                if (dev.stolym.vyne.registry.RegistryMirrorUtil.isVanillaAirBlock(val, entry.getClass().getClassLoader())) {
                     System.err.println("[VyneShims-DEBUG] getRegistrateEntry: detected Air block, trying lookup for " + entry);
                     Object res = tryLookupRegistrateEntry(entry);
                     if (res != null) return res;
                }
                return val;
            }
            System.err.println("[VyneShims-DEBUG] getRegistrateEntry: entry.get() returned NULL, trying lookup for " + entry);
            return tryLookupRegistrateEntry(entry);
        } catch (Throwable t) {
            System.err.println("[VyneShims-DEBUG] getRegistrateEntry failed for " + entry.getClass().getName() + ": " + t);
            return null;
        }
    }

    private static Object tryLookupRegistrateEntry(Object entry) {
         try {
             ClassLoader cl = entry.getClass().getClassLoader();
             Object idObj = dev.stolym.vyne.util.ReflectionHelper.call(entry, "getId");
             if (idObj == null) {
                 System.err.println("[VyneShims-DEBUG] tryLookupRegistrateEntry: entry.getId() returned NULL for " + entry);
                 return null;
             }
             String idStr = idObj.toString();

             Object key = dev.stolym.vyne.util.ReflectionHelper.call(entry, "getEntryKey");
             String regPath = null;
             if (key != null) {
                 regPath = dev.stolym.vyne.util.ReflectionHelper.extractRegistryKeyLocation(key);
             } else {
                 String className = entry.getClass().getName();
                 if (className.contains("BlockEntity")) regPath = "minecraft:block_entity_type";
                 else if (className.contains("Block")) regPath = "minecraft:block";
                 else if (className.contains("Item")) regPath = "minecraft:item";
                 else if (className.contains("Fluid")) regPath = "minecraft:fluid";
                 else if (className.contains("Entity")) regPath = "minecraft:entity_type";
                 else if (className.contains("Sound")) regPath = "minecraft:sound_event";
                 else if (className.contains("Recipe")) regPath = "minecraft:recipe_serializer";
             }

             if (regPath == null) {
                 System.err.println("[VyneShims-DEBUG] tryLookupRegistrateEntry: could not determine regPath for " + entry);
                 return null;
             }
             Object result = lookupInRegistry(cl, regPath, idStr);
             if (result == null) {
                 System.err.println("[VyneShims-DEBUG] tryLookupRegistrateEntry: lookup failed for "
                     + regPath + " / " + idStr
                     + " on entryLoader=" + (cl == null ? "null" : cl.getClass().getName())
                     + ", tccl=" + (Thread.currentThread().getContextClassLoader() == null
                        ? "null"
                        : Thread.currentThread().getContextClassLoader().getClass().getName())
                     + " - attempting AIR fallback.");
                 if ("minecraft:item".equals(regPath)) {
                     result = lookupAirInRegistry(cl, "minecraft:item", "minecraft:air");
                 } else if ("minecraft:block".equals(regPath)) {
                     result = lookupAirInRegistry(cl, "minecraft:block", "minecraft:air");
                 } else if ("minecraft:fluid".equals(regPath)) {
                     result = lookupAirInRegistry(cl, "minecraft:fluid", "minecraft:empty");
                 }
                 if (result == null) {
                     System.err.println("[VyneShims-DEBUG] tryLookupRegistrateEntry: AIR fallback ALSO failed!");
                 }
             }
             return result;
         } catch (Throwable t) {
             System.err.println("[VyneShims-DEBUG] tryLookupRegistrateEntry crash for " + entry + ": " + t);
             return null;
         }
    }

    private static Object lookupAirInRegistry(ClassLoader cl, String regPath, String idStr) {
        Object reg = dev.stolym.vyne.registry.VyneRegistryInjector.resolveRegistryForPath(cl, regPath);
        if (reg == null) return null;
        Object rl = dev.stolym.vyne.registry.VyneRegistryInjector.toResourceLocation(cl, idStr);
        if (rl == null) return null;

        try {
            java.lang.reflect.Method getMethod = null;
            for (java.lang.reflect.Method m : reg.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && (m.getName().equals("get") || m.getName().equals("method_10223"))) {
                    getMethod = m;
                    break;
                }
            }
            if (getMethod != null) {
                return getMethod.invoke(reg, rl);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Object lookupFluidFromVyneCache(ClassLoader loader, String idStr) {
        if (loader == null || idStr == null || idStr.isBlank()) {
            return null;
        }
        try {
            Class<?> registryClass = Class.forName(
                "dev.stolym.vyne.fluid.VyneFluidRegistry",
                false,
                loader);
            java.lang.reflect.Method allFluids = registryClass.getMethod("allFluids");
            Object mapObj = allFluids.invoke(null);
            if (mapObj instanceof Map<?, ?> map) {
                Object fluid = map.get(idStr);
                if (fluid != null) {
                    return fluid;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object lookupFluidAcrossKnownLoaders(ClassLoader preferredLoader, String idStr) {
        LinkedHashSet<ClassLoader> loaders = new LinkedHashSet<>();
        if (preferredLoader != null) {
            loaders.add(preferredLoader);
        }
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != null) {
            loaders.add(tccl);
        }
        ClassLoader canonical = dev.stolym.vyne.vcl.VCLManager.getCanonicalKnotLoader();
        if (canonical != null) {
            loaders.add(canonical);
        }
        ClassLoader own = VyneShims.class.getClassLoader();
        if (own != null) {
            loaders.add(own);
        }

        for (ClassLoader loader : loaders) {
            Object fluid = lookupFluidFromVyneCache(loader, idStr);
            if (fluid != null) {
                return fluid;
            }
        }

        return null;
    }

    private static Object lookupInRegistry(ClassLoader cl, String regPath, String idStr) {
        if ("minecraft:fluid".equals(regPath)) {
            try {
                Object fluid = lookupFluidAcrossKnownLoaders(cl, idStr);
                if (fluid != null) {
                    return fluid;
                }
            } catch (Throwable ignored) {
            }
        }

        Object reg = dev.stolym.vyne.registry.VyneRegistryInjector.resolveRegistryForPath(cl, regPath);
        if (reg != null) {
            try {
                Object res = dev.stolym.vyne.registry.VyneRegistryInjector
                    .getRegistryElementByLocationId(reg, idStr, cl);
                if (res != null && !dev.stolym.vyne.registry.RegistryMirrorUtil.isVanillaAirBlock(res, cl)) {
                    return res;
                }
            } catch (Throwable ignored) {
            }
        }

        if (reg == null) return null;
        Object rl = dev.stolym.vyne.registry.VyneRegistryInjector.toResourceLocation(cl, idStr);
        if (rl == null) return null;

        try {
            java.lang.reflect.Method getMethod = null;
            for (java.lang.reflect.Method m : reg.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && (m.getName().equals("get") || m.getName().equals("method_10223"))) {
                    getMethod = m;
                    break;
                }
            }
            if (getMethod != null) {
                Object res = getMethod.invoke(reg, rl);
                if (res != null && !dev.stolym.vyne.registry.RegistryMirrorUtil.isVanillaAirBlock(res, cl)) {
                    return res;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
