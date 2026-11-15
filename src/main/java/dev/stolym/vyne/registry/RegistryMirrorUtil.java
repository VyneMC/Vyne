/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.registry;

import dev.stolym.vyne.util.ReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
public final class RegistryMirrorUtil {
    private RegistryMirrorUtil() {}

    public static boolean isVanillaAirBlock(Object block, ClassLoader cl) {
        if (block == null || cl == null) {
            return false;
        }
        String blockClassName = block.getClass().getName();
        if ("net.minecraft.class_2189".equals(blockClassName) || blockClassName.endsWith(".AirBlock")) {
            return true;
        }
        try {
            Class<?> blocksClass = Class.forName("net.minecraft.class_2246", true, cl);
            for (String fieldName : new String[] {"field_10124", "AIR"}) {
                try {
                    Field f = blocksClass.getField(fieldName);
                    f.setAccessible(true);
                    if (f.get(null) == block) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        Object blockReg = VyneRegistryInjector.resolveRegistryForPath(cl, "minecraft:block");
        if (blockReg == null) {
            return false;
        }
        for (String airId : new String[] { "minecraft:air", "minecraft:cave_air", "minecraft:void_air" }) {
            Object rl = VyneRegistryInjector.toResourceLocation(cl, airId);
            if (rl == null) {
                continue;
            }
            Object air = invokeRegistryGet(blockReg, rl);
            if (air != null && air == block) {
                return true;
            }
        }
        try {
            String id = tryGetBlockIdString(block, cl);
            if (id != null && ("minecraft:air".equals(id) || "minecraft:cave_air".equals(id)
                    || "minecraft:void_air".equals(id))) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static boolean isBuiltInBlockRegistryDefaultAirForModId(String blockId, Object blockValue,
                                                                   ClassLoader cl) {
        if (blockId == null || blockValue == null || cl == null) {
            return false;
        }
        if (blockId.startsWith("minecraft:")) {
            return false;
        }
        return isVanillaAirBlock(blockValue, cl);
    }

    public static String tryGetBlockIdString(Object block, ClassLoader cl) {
        if (block == null || cl == null) {
            return null;
        }
        Object blockReg = VyneRegistryInjector.resolveRegistryForPath(cl, "minecraft:block");
        if (blockReg == null) {
            return null;
        }
        for (java.lang.reflect.Method m : blockReg.getClass().getMethods()) {
            if (m.getParameterCount() != 1 || !m.getParameterTypes()[0].isInstance(block)) {
                continue;
            }
            String mn = m.getName();
            if (!"getKey".equals(mn) && !"getId".equals(mn) && !mn.contains("method_")) {
                continue;
            }
            Class<?> rt = m.getReturnType();
            if (rt == void.class || rt.isPrimitive()) {
                continue;
            }
            if (!rt.getName().contains("ResourceKey") && !rt.getName().contains("ResourceLocation")
                    && !rt.getName().contains("class_5321") && !rt.getName().contains("class_2960")) {
                continue;
            }
            try {
                m.setAccessible(true);
                Object k = m.invoke(blockReg, block);
                if (k == null) {
                    continue;
                }
                String kn = k.getClass().getName();
                if (kn.contains("class_2960") || kn.contains("ResourceLocation")) {
                    return k.toString();
                }
                Object loc = ReflectionHelper.call(k, "location", "method_29177", "method_41185");
                if (loc != null) {
                    return loc.toString();
                }
                return k.toString();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object resolveItemForBlockBridge(ClassLoader cl, String blockId) {
        Object volatileItem = VyneRegistryInjector.tryGetVolatileBuiltinEntryByLocationId(cl,
            "minecraft:item", blockId);
        Object usableVolatile = coalesceRealItemInstance(volatileItem);
        if (usableVolatile != null) {
            return usableVolatile;
        }
        Object itemReg = VyneRegistryInjector.resolveRegistryForPath(cl, "minecraft:item");
        if (itemReg != null) {
            Object rl = VyneRegistryInjector.toResourceLocation(cl, blockId);
            if (rl != null) {
                Object live = invokeRegistryGet(itemReg, rl);
                Object usable = coalesceRealItemInstance(live);
                if (usable != null) {
                    return usable;
                }
                Object byLoc = VyneRegistryInjector.getRegistryElementByLocationId(itemReg, blockId, cl);
                usable = coalesceRealItemInstance(byLoc);
                if (usable != null) {
                    return usable;
                }
            }
        }
        Object generic = VyneGenericRegistry.getLenient("minecraft:item", blockId);
        Object usableGeneric = coalesceRealItemInstance(generic);
        if (usableGeneric != null) {
            return usableGeneric;
        }
        return null;
    }

    private static Object coalesceRealItemInstance(Object candidate) {
        if (candidate == null) {
            return null;
        }
        if (!isProbableItemRegistryPlaceholder(candidate)) {
            return candidate;
        }
        Object unwrapped = tryUnwrapRegistryItemShell(candidate, 0);
        if (unwrapped != null && !isProbableItemRegistryPlaceholder(unwrapped)) {
            return unwrapped;
        }
        return null;
    }

    private static Object tryUnwrapRegistryItemShell(Object o, int depth) {
        if (o == null || depth > 10) {
            return null;
        }
        if (o instanceof java.util.function.Supplier<?> s) {
            try {
                Object g = s.get();
                if (g != null && g != o) {
                    if (!isProbableItemRegistryPlaceholder(g)) {
                        return g;
                    }
                    Object inner = tryUnwrapRegistryItemShell(g, depth + 1);
                    if (inner != null) {
                        return inner;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (String mn : new String[] {"get", "getObject"}) {
                try {
                    Method m = c.getDeclaredMethod(mn);
                    if (m.getParameterCount() != 0) {
                        continue;
                    }
                    m.setAccessible(true);
                    Object r = m.invoke(o);
                    if (r != null && r != o) {
                        if (!isProbableItemRegistryPlaceholder(r)) {
                            return r;
                        }
                        Object d = tryUnwrapRegistryItemShell(r, depth + 1);
                        if (d != null) {
                            return d;
                        }
                    }
                } catch (ReflectiveOperationException ignored) {
                } catch (Throwable ignored) {
                }
            }
        }
        for (String mn : new String[] {"get", "getObject", "value", "orElseThrow"}) {
            try {
                Method m = o.getClass().getMethod(mn);
                if (m.getParameterCount() != 0) {
                    continue;
                }
                m.setAccessible(true);
                Object r = m.invoke(o);
                if (r != null && r != o) {
                    if (!isProbableItemRegistryPlaceholder(r)) {
                        return r;
                    }
                    Object d = tryUnwrapRegistryItemShell(r, depth + 1);
                    if (d != null) {
                        return d;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        String shellName = o.getClass().getName();
        boolean aggressiveShell = shellName.contains("RegistryObject") || shellName.contains("DeferredHolder")
            || shellName.contains("Unregistered") || shellName.contains("Holder");
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) {
                    continue;
                }
                Class<?> t = f.getType();
                if (!aggressiveShell && !java.util.function.Supplier.class.isAssignableFrom(t)
                        && !t.getName().contains("Supplier")
                        && !t.getName().contains("Lazy")) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object v = f.get(o);
                    if (v instanceof java.util.function.Supplier<?> sup) {
                        try {
                            Object g = sup.get();
                            if (g != null && !isProbableItemRegistryPlaceholder(g)) {
                                return g;
                            }
                            Object d = tryUnwrapRegistryItemShell(g, depth + 1);
                            if (d != null) {
                                return d;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static boolean isProbableItemRegistryPlaceholder(Object item) {
        if (item == null) {
            return true;
        }
        String ts = String.valueOf(item);
        return ts.contains("[unregistered]") || ts.contains("UnregisteredHolder")
            || (ts.contains("RegistryObject") && ts.contains("empty"));
    }

    public static Object tryResolveLinkedBlockFromItemRegistryBestEffort(ClassLoader cl, String blockId) {
        if (cl == null || blockId == null || blockId.isEmpty()) {
            return null;
        }
        Object item = VyneRegistryInjector.tryGetVolatileBuiltinEntryByLocationId(cl, "minecraft:item",
            blockId);
        if (item == null) {
            Object itemReg = VyneRegistryInjector.resolveRegistryForPath(cl, "minecraft:item");
            Object rl = VyneRegistryInjector.toResourceLocation(cl, blockId);
            if (itemReg != null && rl != null) {
                item = invokeRegistryGet(itemReg, rl);
            }
        }
        Object peeled = tryUnwrapRegistryItemShell(item, 0);
        if (peeled != null) {
            item = peeled;
        }
        if (item == null) {
            return null;
        }
        Object b = nonAirOrNull(tryGetBlockFromBlockItemLike(item), cl);
        if (b != null) {
            return b;
        }
        b = nonAirOrNull(tryReadBlockFieldFromBlockItem(item), cl);
        if (b != null) {
            return b;
        }
        b = nonAirOrNull(tryReadDeclaredBlockFieldFromItem(item, cl), cl);
        if (b != null) {
            return b;
        }
        b = nonAirOrNull(tryAnyNoArgMethodReturningBlock(item), cl);
        if (b != null) {
            return b;
        }
        return nonAirOrNull(tryBlockByItemStatic(cl, item), cl);
    }

    public static Object tryExtractBlockFromItemInstanceBestEffort(Object item, ClassLoader cl) {
        if (item == null || cl == null) {
            return null;
        }
        Object peeled = tryUnwrapRegistryItemShell(item, 0);
        if (peeled != null) {
            item = peeled;
        }
        Object b = nonAirOrNull(tryGetBlockFromBlockItemLike(item), cl);
        if (b != null) {
            return b;
        }
        b = nonAirOrNull(tryReadBlockFieldFromBlockItem(item), cl);
        if (b != null) {
            return b;
        }
        b = nonAirOrNull(tryReadDeclaredBlockFieldFromItem(item, cl), cl);
        if (b != null) {
            return b;
        }
        b = nonAirOrNull(tryAnyNoArgMethodReturningBlock(item), cl);
        if (b != null) {
            return b;
        }
        return nonAirOrNull(tryBlockByItemStatic(cl, item), cl);
    }

    public static Object tryResolveBlockForBlockIdFromItemRegistry(ClassLoader cl, String blockId) {
        if (cl == null || blockId == null || blockId.isEmpty()) {
            return null;
        }
        if (blockId.contains("zinc")) Diagnostic.log("Resolving: " + blockId);
        Object fromStaticTable = nonAirOrNull(
            ModBlockTableReflect.tryResolveBlockFromModStaticTables(cl, blockId), cl);
        if (fromStaticTable != null && !isBuiltInBlockRegistryDefaultAirForModId(blockId, fromStaticTable, cl)) {
            if (blockId.contains("zinc")) Diagnostic.log("Resolved via Table: " + fromStaticTable);
            return fromStaticTable;
        }
        Object fromDeferred = nonAirOrNull(
            net.neoforged.neoforge.registries.DeferredRegister.tryResolveEntryFromDeferredRegisters(blockId),
            cl);
        if (fromDeferred != null && !isBuiltInBlockRegistryDefaultAirForModId(blockId, fromDeferred, cl)) {
            if (blockId.contains("zinc")) Diagnostic.log("Resolved via Deferred: " + fromDeferred);
            return fromDeferred;
        }
        Object fromItemEarly = tryResolveLinkedBlockFromItemRegistryBestEffort(cl, blockId);
        Object earlyBlock = nonAirOrNull(fromItemEarly, cl);
        if (earlyBlock != null && !isBuiltInBlockRegistryDefaultAirForModId(blockId, fromItemEarly, cl)) {
            if (blockId.contains("zinc")) Diagnostic.log("Resolved via ItemEarly: " + earlyBlock);
            return earlyBlock;
        }
        Object fabricSweep = VyneRegistryInjector.tryGetVolatileBuiltinEntryByLocationId(cl,
            "minecraft:block", blockId);
        Object fromSweep = nonAirOrNull(fabricSweep, cl);
        if (fromSweep != null && !isBuiltInBlockRegistryDefaultAirForModId(blockId, fabricSweep, cl)) {
            if (blockId.contains("zinc")) Diagnostic.log("Resolved via Sweep: " + fromSweep);
            return fromSweep;
        }
        Object blockReg = VyneRegistryInjector.resolveRegistryForPath(cl, "minecraft:block");
        if (blockReg != null) {
            Object byKey = VyneRegistryInjector.getRegistryElementByLocationId(blockReg, blockId, cl);
            Object fromKey = nonAirOrNull(byKey, cl);
            if (fromKey != null && !isBuiltInBlockRegistryDefaultAirForModId(blockId, byKey, cl)) {
                if (blockId.contains("zinc")) Diagnostic.log("Resolved via RegistryKey: " + fromKey);
                return fromKey;
            }
            Object rl = VyneRegistryInjector.toResourceLocation(cl, blockId);
            if (rl != null) {
                Object rawGet = invokeRegistryGet(blockReg, rl);
                Object fromGet = nonAirOrNull(rawGet, cl);
                if (fromGet != null && !isBuiltInBlockRegistryDefaultAirForModId(blockId, rawGet, cl)) {
                    if (blockId.contains("zinc")) Diagnostic.log("Resolved via RegistryGet: " + fromGet);
                    return fromGet;
                }
            }
        }
        Object item = resolveItemForBlockBridge(cl, blockId);
        if (item == null) {
            if (blockId.contains("zinc")) Diagnostic.log("Failed to resolve Zinc Ore.");
            return null;
        }
        return null;
    }

    private static Object nonAirOrNull(Object block, ClassLoader cl) {
        if (block == null || isVanillaAirBlock(block, cl)) {
            return null;
        }
        return block;
    }

    private static Object tryReadBlockFieldFromBlockItem(Object item) {
        if (item == null) {
            return null;
        }
        for (Class<?> c = item.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            String cn = c.getName();
            if (!cn.endsWith("BlockItem") && !"net.minecraft.class_1747".equals(cn)) {
                continue;
            }
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object raw = f.get(item);
                    Object b = unwrapHolderToBlock(raw);
                    if (b != null && looksLikeBlockClass(b.getClass())) {
                        return b;
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return null;
    }

    private static Object unwrapHolderToBlock(Object raw) {
        return deepUnwrapToBlock(raw, 0);
    }

    private static Object deepUnwrapToBlock(Object raw, int depth) {
        if (raw == null || depth > 10) {
            return null;
        }
        if (raw instanceof java.util.function.Supplier<?> s) {
            try {
                raw = s.get();
            } catch (Throwable ignored) {
                return null;
            }
        }
        if (raw == null) {
            return null;
        }
        if (looksLikeBlockClass(raw.getClass())) {
            return raw;
        }
        Class<?> rc = raw.getClass();
        for (String n : new String[] {"value", "get", "method_40246"}) {
            try {
                Method m = rc.getMethod(n);
                if (m.getParameterCount() != 0) {
                    continue;
                }
                m.setAccessible(true);
                Object next = m.invoke(raw);
                Object b = deepUnwrapToBlock(next, depth + 1);
                if (b != null) {
                    return b;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static Object tryAnyNoArgMethodReturningBlock(Object item) {
        if (item == null) {
            return null;
        }
        for (Class<?> c = item.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() != 0) {
                    continue;
                }
                if (!methodProbablyReturnsBlockOrState(m)) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    Object raw = m.invoke(item);
                    Object b = unwrapBlockStateToBlock(raw);
                    if (b != null) {
                        return b;
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return null;
    }

    private static Object unwrapBlockStateToBlock(Object o) {
        if (o == null) {
            return null;
        }
        if (looksLikeBlockClass(o.getClass())) {
            return o;
        }
        try {
            Method gb = o.getClass().getMethod("getBlock");
            if (gb.getParameterCount() == 0) {
                gb.setAccessible(true);
                return gb.invoke(o);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static boolean methodProbablyReturnsBlockOrState(Method m) {
        if (methodProbablyReturnsBlock(m)) {
            return true;
        }
        Class<?> r = m.getReturnType();
        if (r == null) {
            return false;
        }
        int guard = 0;
        while (r != null && r != Object.class && guard++ < 24) {
            String n = r.getName();
            if (n.contains("BlockState") || n.contains("class_2680")) {
                return true;
            }
            r = r.getSuperclass();
        }
        return false;
    }

    private static Object invokeRegistryGet(Object reg, Object rl) {
        if (reg == null || rl == null) {
            return null;
        }
        Class<?> rlClass = rl.getClass();
        for (String n : new String[] {"method_10223", "get", "getValue"}) {
            try {
                Method m = reg.getClass().getMethod(n, rlClass);
                Class<?> rt = m.getReturnType();
                if (rt == void.class || rt == boolean.class) {
                    continue;
                }
                m.setAccessible(true);
                Object v = m.invoke(reg, rl);
                if (v != null) {
                    return v;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static Object tryBlockByItemStatic(ClassLoader cl, Object item) {
        try {
            Class<?> blockClass = Class.forName("net.minecraft.class_2248", true, cl);
            for (Method m : blockClass.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 1) {
                    continue;
                }
                if (!m.getParameterTypes()[0].isInstance(item)) {
                    continue;
                }
                Class<?> ret = m.getReturnType();
                if (!looksLikeBlockClass(ret)) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    Object r = m.invoke(null, item);
                    if (r != null) {
                        return r;
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object tryBlocksClassStaticItemToBlock(ClassLoader cl, Object item) {
        try {
            Class<?> blocks = Class.forName("net.minecraft.class_2246", true, cl);
            for (Method m : blocks.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 1) {
                    continue;
                }
                if (!m.getParameterTypes()[0].isInstance(item)) {
                    continue;
                }
                if (!looksLikeBlockClass(m.getReturnType())) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    Object r = m.invoke(null, item);
                    if (r != null) {
                        return r;
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean looksLikeBlockClass(Class<?> r) {
        if (r == null || r == void.class) {
            return false;
        }
        int guard = 0;
        while (r != null && r != Object.class && guard++ < 24) {
            String n = r.getName();
            if (n.endsWith("Block") || n.contains("class_2248")) {
                return true;
            }
            r = r.getSuperclass();
        }
        return false;
    }

    public static void mirrorBlockItemToBlockRegistryIfNeeded(String registryPath, Object id, Object value) {
        if (registryPath == null || id == null || value == null) {
            return;
        }
        String norm = ReflectionHelper.normalizeRegistryKeyLocationString(registryPath);
        if (!"minecraft:item".equals(norm)) {
            return;
        }
        ClassLoader cl = value.getClass().getClassLoader();
        Object block = tryGetBlockFromBlockItemLike(value);
        if (block == null || isVanillaAirBlock(block, cl)) {
            block = tryExtractBlockFromItemInstanceBestEffort(value, cl);
        }
        if (block == null || isVanillaAirBlock(block, cl)) {
            try {
                net.neoforged.neoforge.registries.DeferredRegister.fillAllRegistryGapsIntoVyneGeneric();
            } catch (Throwable ignored) {
            }
            try {
                dev.stolym.vyne.vcl.VCLFieldPopulator.retryPending(cl);
            } catch (Throwable ignored) {
            }
            String idStrEarly = id.toString();
            block = ModBlockTableReflect.tryResolveBlockFromModStaticTables(cl, idStrEarly);
        }
        if (block == null || isVanillaAirBlock(block, cl)) {
            block = tryResolveBlockForBlockIdFromItemRegistry(cl, id.toString());
        }
        if (block == null || isVanillaAirBlock(block, cl)) {
            return;
        }
        String idStr = id.toString();
        VyneGenericRegistry.register("minecraft:block", idStr, block);
        try {
            VyneRegistryInjector.inject("minecraft:block", idStr, block);
        } catch (Throwable ignored) {
        }
    }

    private static Object tryGetBlockFromBlockItemLike(Object item) {
        ClassLoader cl = item != null ? item.getClass().getClassLoader() : null;
        for (Class<?> c = item.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (String mn : new String[] {"getBlock", "method_7711"}) {
                Method m = null;
                try {
                    m = c.getDeclaredMethod(mn);
                } catch (NoSuchMethodException e) {
                    try {
                        m = c.getMethod(mn);
                    } catch (NoSuchMethodException ignored) {
                        continue;
                    }
                }
                if (m.getParameterCount() != 0) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    Object raw = m.invoke(item);
                    Object b = deepUnwrapToBlock(raw, 0);
                    if (b != null && !isVanillaAirBlock(b, cl)) {
                        return b;
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return null;
    }

    private static Object tryReadDeclaredBlockFieldFromItem(Object item, ClassLoader cl) {
        if (item == null || cl == null) {
            return null;
        }
        try {
            Class<?> blockClass = Class.forName("net.minecraft.class_2248", true, cl);
            for (Class<?> c = item.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    Class<?> t = f.getType();
                    if (!blockClass.isAssignableFrom(t) && !t.getName().endsWith("Block")
                            && !t.getName().contains("class_2248")) {
                        continue;
                    }
                    try {
                        f.setAccessible(true);
                        Object raw = f.get(item);
                        if (raw instanceof java.util.function.Supplier<?> s) {
                            raw = s.get();
                        }
                        Object b = deepUnwrapToBlock(raw, 0);
                        if (b != null && !isVanillaAirBlock(b, cl)) {
                            return b;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean methodProbablyReturnsBlock(Method m) {
        Class<?> r = m.getReturnType();
        if (r == null || r == void.class || r == boolean.class || r.isPrimitive()) {
            return false;
        }
        int guard = 0;
        while (r != null && r != Object.class && guard++ < 24) {
            String n = r.getName();
            if (n.endsWith("Block") || "net.minecraft.class_2248".equals(n)) {
                return true;
            }
            r = r.getSuperclass();
        }
        return false;
    }
}
