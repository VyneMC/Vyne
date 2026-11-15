/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.registry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import dev.stolym.vyne.util.VyneLogger;
import dev.stolym.vyne.util.ReflectionHelper;

public class ForceSync {
    public static void forceRegisterRegistrate(Object registrate) {
        if (registrate == null) return;
        try {
            Class<?> clazz = registrate.getClass();
            ClassLoader cl = clazz.getClassLoader();
            while (clazz != null && !clazz.equals(Object.class)) {
                for (Field f : clazz.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(registrate);
                    if (val == null) continue;

                    processUnknownObject(registrate, val, cl, 0);
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable t) {
            VyneLogger.warn("[Vyne-Recovery] AbstractRegistrate sync failed: " + t);
        }
    }

    private static void processUnknownObject(Object registrate, Object obj, ClassLoader cl, int depth) {
        if (obj == null || depth > 3) return;

        if (obj instanceof Collection<?> col) {
            for (Object entry : col) processUnknownObject(registrate, entry, cl, depth + 1);
        } else if (obj instanceof Map<?, ?> map) {
            for (Object entry : map.values()) processUnknownObject(registrate, entry, cl, depth + 1);
        } else {
            String name = obj.getClass().getName();
            if (name.contains("Entry") || name.contains("Holder") || name.contains("Builder")) {
                processOneRegistrateEntry(registrate, obj, cl);
            }

            try {
                Method values = obj.getClass().getMethod("values");
                Object vals = values.invoke(obj);
                if (vals instanceof Collection) {
                    for (Object entry : (Collection<?>) vals) processUnknownObject(registrate, entry, cl, depth + 1);
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void processOneRegistrateEntry(Object registrate, Object entry, ClassLoader cl) {
        if (entry == null) return;
        try {
            Object idObj = ReflectionHelper.call(entry, "getId", "method_29177", "getRegistryName", "getName");
            if (idObj == null) return;
            String id = idObj.toString();
            if (id.isEmpty() || id.equals("null")) return;

            Object value = ReflectionHelper.call(entry, "get");
            if (value == null) return;

            String registryPath = "minecraft:block";

            Object regKey = ReflectionHelper.call(entry, "getRegistryKey", "getType", "getRegistryName");
            if (regKey != null) {
                String loc = ReflectionHelper.extractRegistryKeyLocation(regKey);
                if (loc != null && !loc.equals("null")) {
                    registryPath = loc;
                }
            } else {
                String en = entry.getClass().getName();
                if (en.contains("ItemEntry")) registryPath = "minecraft:item";
                else if (en.contains("BlockEntry")) registryPath = "minecraft:block";
                else if (en.contains("FluidEntry")) registryPath = "minecraft:fluid";
                else if (en.contains("EntityEntry")) registryPath = "minecraft:entity_type";
                else {
                    String vn = value.getClass().getName();
                    if (vn.contains("Item") || vn.contains("class_1792")) registryPath = "minecraft:item";
                    else if (vn.contains("Block") || vn.contains("class_2248")) registryPath = "minecraft:block";
                    else if (vn.contains("Fluid") || vn.contains("class_3611")) registryPath = "minecraft:fluid";
                    else if (vn.contains("EntityType") || vn.contains("class_1299")) registryPath = "minecraft:entity_type";
                }
            }

            VyneGenericRegistry.register(registryPath, id, value);

            Object liveReg = VyneRegistryInjector.resolveRegistryForPath(cl, registryPath);
            if (liveReg != null) {
                Object rl = VyneRegistryInjector.toResourceLocation(cl, id);
                if (rl != null) {
                    try {
                        Method forceBind = VyneRegistryInjector.class.getDeclaredMethod("forceBindIntrusiveHolder", Object.class, Object.class, ClassLoader.class);
                        forceBind.setAccessible(true);
                        forceBind.invoke(null, liveReg, value, cl);
                    } catch (Throwable ignored) {}

                    VyneRegistryInjector.injectIntoLiveRegistry(liveReg, rl, value);
                }
            }
        } catch (Throwable ignored) {}
    }
}
