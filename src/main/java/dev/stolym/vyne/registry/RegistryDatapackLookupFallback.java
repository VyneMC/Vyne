/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.registry;

import com.mojang.serialization.JsonOps;

import dev.stolym.vyne.util.ReflectionHelper;
import dev.stolym.vyne.util.VyneLogger;

import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class RegistryDatapackLookupFallback {
    private static final ThreadLocal<Integer> MAPPED_REGISTRY_HOLDER_FALLBACK_DEPTH =
        ThreadLocal.withInitial(() -> 0);

    private static final ThreadLocal<Integer> HOLDER_FROM_LIVE_BRUTE_DEPTH = ThreadLocal.withInitial(() -> 0);

    private static final ThreadLocal<ArrayDeque<Object>> REG_ELEMENT_CODEC_REGISTRY_STACK =
        ThreadLocal.withInitial(ArrayDeque::new);

    private static final String GETTER_IFACE = "net.minecraft.class_6903$class_7863";
    private static final String REG_INFO_CLASS = "net.minecraft.class_6903$class_7862";
    private static final String ENTRY_LOOKUP_IFACE = "net.minecraft.class_7871";
    private static final String REGISTRATION_LOOKUP_IFACE = "net.minecraft.class_7225$class_7226";

    private static final Set<String> AUGMENT_BUILTIN_REGISTRY_PATHS = Set.of(
        "minecraft:block",
        "minecraft:item",
        "minecraft:fluid",
        "minecraft:entity_type",
        "minecraft:block_entity_type",
        "minecraft:mob_effect",
        "minecraft:sound_event",
        "minecraft:particle_type",
        "minecraft:potion",
        "minecraft:enchantment",
        "minecraft:attribute",
        "minecraft:worldgen/configured_feature",
        "minecraft:worldgen/placed_feature",
        "minecraft:worldgen/biome",
        "minecraft:worldgen/structure",
        "minecraft:worldgen/structure_set",
        "minecraft:worldgen/template_pool"
    );

    private static volatile Set<String> effectiveAugmentPaths;

    private static boolean vyneDebugDatapackLookup() {
        return Boolean.getBoolean("vyne.registry.debugDatapackLookup");
    }

    private static boolean vyneTraceElementCodecRecover() {
        return true;
    }

    private static void traceElementCodecRecover(String msg) {
        if (vyneTraceElementCodecRecover()) {
            VyneLogger.info("[RegistryElementCodec-recover] " + msg);
        }
    }

    private RegistryDatapackLookupFallback() {}

    public static void enterRegistryElementCodecDecodeContext(Object registryElementCodec, ClassLoader cl) {
        if (registryElementCodec == null || cl == null
                || Boolean.getBoolean("vyne.registry.skipRegistryElementCodecDecodeContext")) {
            return;
        }
        try {
            Object registryTypeKey = readRegistryElementCodecRegistryRef(registryElementCodec);
            if (registryTypeKey == null) {
                return;
            }
            String loc = ReflectionHelper.extractRegistryKeyLocation(registryTypeKey);
            if (loc == null) {
                return;
            }
            Object reg = readRegistryElementCodecBoundRegistryInstance(registryElementCodec, cl, loc);
            if (reg == null) {
                reg = VyneRegistryInjector.resolveRegistryForPath(cl, loc);
            }
            if (reg != null) {
                REG_ELEMENT_CODEC_REGISTRY_STACK.get().push(reg);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object readRegistryElementCodecBoundRegistryInstance(Object codec, ClassLoader cl,
            String expectedLoc) {
        if (codec == null || cl == null || expectedLoc == null) {
            return null;
        }
        try {
            Class<?> regIface = Class.forName("net.minecraft.class_2378", false, cl);
            for (Class<?> c = codec.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    Class<?> ft = f.getType();
                    String ftn = ft.getName();
                    if (ftn.contains("class_5321") || ftn.contains("ResourceKey")) {
                        continue;
                    }
                    if (!regIface.isAssignableFrom(ft)) {
                        continue;
                    }
                    try {
                        f.setAccessible(true);
                        Object v = f.get(codec);
                        if (v == null) {
                            continue;
                        }
                        Object rk = ReflectionHelper.call(v, "key", "method_30517");
                        String vloc = rk == null ? null : ReflectionHelper.extractRegistryKeyLocation(rk);
                        if (vloc != null && ReflectionHelper.registryKeyLocationsMatch(vloc, expectedLoc)) {
                            return v;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            Class<?> lookupIface = Class.forName(ENTRY_LOOKUP_IFACE, false, cl);
            for (Class<?> c = codec.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    Class<?> ft = f.getType();
                    if (!lookupIface.isAssignableFrom(ft)) {
                        continue;
                    }
                    try {
                        f.setAccessible(true);
                        Object v = f.get(codec);
                        if (v == null) {
                            continue;
                        }
                        Object del = effectiveRegistryEntryLookupDelegate(v, cl);
                        Object reg = tryExtractBackingRegistryFromEntryLookup(del != null ? del : v, cl);
                        if (reg == null) {
                            continue;
                        }
                        Object rk = ReflectionHelper.call(reg, "key", "method_30517");
                        String vloc = rk == null ? null : ReflectionHelper.extractRegistryKeyLocation(rk);
                        if (vloc != null && ReflectionHelper.registryKeyLocationsMatch(vloc, expectedLoc)) {
                            return reg;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static void leaveRegistryElementCodecDecodeContext() {
        try {
            ArrayDeque<Object> d = REG_ELEMENT_CODEC_REGISTRY_STACK.get();
            if (!d.isEmpty()) {
                d.pop();
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object peekRegistryElementCodecPreferredRegistry() {
        ArrayDeque<Object> d = REG_ELEMENT_CODEC_REGISTRY_STACK.get();
        return d.isEmpty() ? null : d.peek();
    }

    private static Set<String> effectiveAugmentPaths() {
        Set<String> c = effectiveAugmentPaths;
        if (c != null) {
            return c;
        }
        synchronized (RegistryDatapackLookupFallback.class) {
            c = effectiveAugmentPaths;
            if (c != null) {
                return c;
            }
            HashSet<String> merged = new HashSet<>(AUGMENT_BUILTIN_REGISTRY_PATHS);
            String extra = System.getProperty("vyne.registry.datapackAugmentExtra");
            if (extra != null) {
                for (String part : extra.split(",")) {
                    if (part == null) {
                        continue;
                    }
                    String p = part.trim();
                    if (!p.isEmpty()) {
                        merged.add(p);
                    }
                }
            }
            c = Set.copyOf(merged);
            effectiveAugmentPaths = c;
            return c;
        }
    }

    public static boolean isDatapackAugmentBuiltinPath(String normalizedRegistryPath) {
        return normalizedRegistryPath != null && effectiveAugmentPaths().contains(normalizedRegistryPath);
    }

    public static Optional<Object> tryResolveHolderLive(ClassLoader knotCl, Object entryResourceKey) {
        return holderFromLiveRegistry(knotCl, entryResourceKey);
    }

    public static boolean isInsideMappedRegistryHolderFallback() {
        return MAPPED_REGISTRY_HOLDER_FALLBACK_DEPTH.get() > 0;
    }

    private static void enterMappedRegistryHolderFallback() {
        MAPPED_REGISTRY_HOLDER_FALLBACK_DEPTH.set(MAPPED_REGISTRY_HOLDER_FALLBACK_DEPTH.get() + 1);
    }

    private static void leaveMappedRegistryHolderFallback() {
        int d = MAPPED_REGISTRY_HOLDER_FALLBACK_DEPTH.get() - 1;
        if (d <= 0) {
            MAPPED_REGISTRY_HOLDER_FALLBACK_DEPTH.remove();
        } else {
            MAPPED_REGISTRY_HOLDER_FALLBACK_DEPTH.set(d);
        }
    }

    public static boolean lenientRegistryContainsResourceLocation(Object mappedRegistry,
            Object resourceLocation, ClassLoader knotCl) {
        if (mappedRegistry == null || resourceLocation == null || knotCl == null
                || Boolean.getBoolean("vyne.registry.skipMappedRegistryLenientContains")) {
            return false;
        }
        try {
            Object regKey = ReflectionHelper.call(mappedRegistry, "key", "method_30517");
            String path = regKey == null ? null : ReflectionHelper.extractRegistryKeyLocation(regKey);
            if (path == null || !effectiveAugmentPaths().contains(path)) {
                return false;
            }
            String id = resourceLocation.toString();
            String norm = ReflectionHelper.normalizeRegistryKeyLocationString(path);
            Object fromGeneric = airToNullIfModBlockGeneric(norm, id,
                VyneGenericRegistry.getLenient(path, id), knotCl);
            if (fromGeneric != null) {
                return true;
            }
            Object canon = VyneRegistryInjector.resolveRegistryForPath(knotCl, path);
            if (canon != null && !canon.equals(mappedRegistry)
                    && invokeRegistryContainsResourceLocation(canon, resourceLocation)) {
                return true;
            }
            if ("minecraft:block".equals(norm)) {
                try {
                    Object fromDr = DeferredRegister.tryResolveEntryFromDeferredRegisters(id);
                    if (fromDr != null && !RegistryMirrorUtil.isVanillaAirBlock(fromDr, knotCl)) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
                if (RegistryMirrorUtil.tryResolveBlockForBlockIdFromItemRegistry(knotCl, id) != null) {
                    return true;
                }
            }
            return lenientRegistryRawHasElement(norm, id, mappedRegistry, canon, knotCl);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean lenientRegistryRawHasElement(String norm, String id, Object mappedRegistry,
            Object canon, ClassLoader knotCl) {
        for (Object reg : new Object[] { mappedRegistry, canon }) {
            if (reg == null) {
                continue;
            }
            try {
                Object v = VyneRegistryInjector.getRegistryElementByLocationId(reg, id, knotCl);
                if (v == null) {
                    v = tryRegistryRawGetByResourceLocation(reg, id, knotCl);
                }
                if (v == null) {
                    continue;
                }
                if ("minecraft:block".equals(norm) && !id.startsWith("minecraft:")
                        && RegistryMirrorUtil.isVanillaAirBlock(v, knotCl)) {
                    continue;
                }
                if ("minecraft:block".equals(norm) && !id.startsWith("minecraft:")
                        && RegistryMirrorUtil.isBuiltInBlockRegistryDefaultAirForModId(id, v, knotCl)) {
                    continue;
                }
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static boolean lenientRegistryContainsResourceKey(Object mappedRegistry,
            Object resourceKey, ClassLoader knotCl) {
        if (mappedRegistry == null || resourceKey == null || knotCl == null) {
            return false;
        }
        Object rl = extractResourceLocationFromEntryKey(resourceKey);
        if (rl == null) {
            return false;
        }
        return lenientRegistryContainsResourceLocation(mappedRegistry, rl, knotCl);
    }

    private static Object airToNullIfModBlockGeneric(String norm, String id, Object v, ClassLoader knotCl) {
        if (v == null) {
            return null;
        }
        if (!"minecraft:block".equals(norm)) {
            return v;
        }
        if (id.startsWith("minecraft:")) {
            return v;
        }
        if (RegistryMirrorUtil.isVanillaAirBlock(v, knotCl)) {
            return null;
        }
        return v;
    }

    private static boolean invokeRegistryContainsResourceLocation(Object registry, Object resourceLocation) {
        if (registry == null || resourceLocation == null) {
            return false;
        }
        Class<?> rlClass = resourceLocation.getClass();
        for (String n : new String[] { "method_10250", "containsKey" }) {
            try {
                Method m = registry.getClass().getMethod(n, rlClass);
                m.setAccessible(true);
                Object r = invokeRegistryLookupDelegate(registry, m, new Object[] { resourceLocation });
                return Boolean.TRUE.equals(r);
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static Optional<Object> lenientHolderAfterMappedRegistryMiss(Object mappedRegistry,
            ClassLoader knotCl, Object resourceLocation) {
        if (mappedRegistry == null || knotCl == null || resourceLocation == null
                || Boolean.getBoolean("vyne.registry.skipMappedRegistryHolderFallback")) {
            return Optional.empty();
        }
        enterMappedRegistryHolderFallback();
        try {
            Object registryTypeKey = dev.stolym.vyne.util.ReflectionHelper.call(
                mappedRegistry, "key", "method_30517");
            if (registryTypeKey == null) {
                return Optional.empty();
            }
            Object entryKey = invokeResourceKeyCreate(knotCl, registryTypeKey, resourceLocation);
            if (entryKey == null) {
                return Optional.empty();
            }
            return holderFromLiveRegistry(knotCl, entryKey, mappedRegistry, true);
        } catch (Throwable ignored) {
            return Optional.empty();
        } finally {
            leaveMappedRegistryHolderFallback();
        }
    }

    public static Optional<Object> lenientHolderAfterMappedRegistryMissForKey(Object mappedRegistry,
            ClassLoader knotCl, Object entryResourceKey) {
        if (mappedRegistry == null || knotCl == null || entryResourceKey == null
                || Boolean.getBoolean("vyne.registry.skipMappedRegistryHolderFallback")) {
            return Optional.empty();
        }
        enterMappedRegistryHolderFallback();
        try {
            return holderFromLiveRegistry(knotCl, entryResourceKey, mappedRegistry, true);
        } catch (Throwable ignored) {
            return Optional.empty();
        } finally {
            leaveMappedRegistryHolderFallback();
        }
    }

    private static Optional<Object> lenientBruteHolderFromMappedRegistryElement(Object mappedRegistry,
            ClassLoader knotCl, Object entryResourceKey) {
        if (mappedRegistry == null || knotCl == null || entryResourceKey == null) {
            return Optional.empty();
        }
        try {
            String path = ReflectionHelper.normalizeRegistryKeyLocationString(
                inferBuiltinRegistryPathForEntryKey(entryResourceKey));
            if (!"minecraft:block".equals(path)) {
                return Optional.empty();
            }
            Object rl = extractResourceLocationFromEntryKey(entryResourceKey);
            if (rl == null) {
                return Optional.empty();
            }
            String id = rl.toString();
            Object canonical = VyneRegistryInjector.resolveRegistryForPath(knotCl, path);
            Object block = null;
            for (Object reg : new Object[] { mappedRegistry, canonical }) {
                if (reg == null) {
                    continue;
                }
                Object candidate = VyneRegistryInjector.getRegistryElementByLocationId(reg, id, knotCl);
                if (candidate == null) {
                    candidate = tryRegistryRawGetByResourceLocation(reg, id, knotCl);
                }
                if (candidate != null && !RegistryMirrorUtil.isVanillaAirBlock(candidate, knotCl)
                        && !RegistryMirrorUtil.isBuiltInBlockRegistryDefaultAirForModId(id, candidate, knotCl)) {
                    block = candidate;
                    break;
                }
            }
            if (block == null) {
                block = RegistryMirrorUtil.tryResolveBlockForBlockIdFromItemRegistry(knotCl, id);
            }
            if (block == null) {
                block = VyneGenericRegistry.getLenient(path, id);
            }
            if (block == null || RegistryMirrorUtil.isVanillaAirBlock(block, knotCl)) {
                return Optional.empty();
            }
            if (!id.startsWith("minecraft:")
                    && RegistryMirrorUtil.isBuiltInBlockRegistryDefaultAirForModId(id, block, knotCl)) {
                return Optional.empty();
            }
            for (Object reg : new Object[] { mappedRegistry, canonical }) {
                if (reg == null) {
                    continue;
                }
                Optional<Object> intrusive = tryCreateIntrusiveHolderReferenceOnMappedRegistry(reg, block,
                    knotCl);
                if (intrusive.isPresent() && isHolderReference(intrusive.get(), knotCl)) {
                    return intrusive;
                }
                Optional<Object> viaEl = tryHolderFromElementValue(reg, block);
                if (viaEl.isPresent()) {
                    Optional<Object> ensured = ensureHolderOptional(viaEl, knotCl);
                    if (ensured.isPresent() && isHolderReference(ensured.get(), knotCl)) {
                        return ensured;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    private static Object tryRegistryRawGetByResourceLocation(Object reg, String entryId, ClassLoader knotCl) {
        if (reg == null || entryId == null || knotCl == null) {
            return null;
        }
        Object rl = VyneRegistryInjector.toResourceLocation(knotCl, entryId);
        if (rl == null) {
            return null;
        }
        Class<?> rlClass = rl.getClass();
        for (String mn : new String[] {"method_10223", "get", "getValue"}) {
            try {
                Method m = reg.getClass().getMethod(mn, rlClass);
                Class<?> rt = m.getReturnType();
                if (rt == void.class || rt == boolean.class) {
                    continue;
                }
                m.setAccessible(true);
                Object v = invokeRegistryLookupDelegate(reg, m, new Object[] { rl });
                if (v instanceof java.util.Optional<?> o) {
                    v = o.orElse(null);
                }
                if (v != null && !RegistryMirrorUtil.isVanillaAirBlock(v, knotCl)
                        && !RegistryMirrorUtil.isBuiltInBlockRegistryDefaultAirForModId(entryId, v, knotCl)) {
                    return v;
                }
            } catch (Throwable ignored) {
            }
        }
        for (Class<?> c = reg.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() != 1 || !m.getParameterTypes()[0].isAssignableFrom(rlClass)) {
                    continue;
                }
                String n = m.getName();
                if (!n.contains("10223") && !n.contains("29107") && !"get".equals(n) && !"getValue".equals(n)) {
                    continue;
                }
                Class<?> rt = m.getReturnType();
                if (rt == void.class || rt == boolean.class) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    Object v = invokeRegistryLookupDelegate(reg, m, new Object[] { rl });
                    if (v instanceof java.util.Optional<?> o) {
                        v = o.orElse(null);
                    }
                    if (v != null && !RegistryMirrorUtil.isVanillaAirBlock(v, knotCl)
                            && !RegistryMirrorUtil.isBuiltInBlockRegistryDefaultAirForModId(entryId, v, knotCl)) {
                        return v;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    public static Optional<Object> tryDeferredRegisterBlockHolderForMappedRegistry(Object mappedRegistry,
            ClassLoader knotCl, Object entryResourceKey) {
        String idStr = ReflectionHelper.extractRegistryKeyLocation(entryResourceKey);
        if (idStr == null) return Optional.empty();

        Object found = VyneRegistryInjector.tryResolveEntryFromTrackedDeferredRegisters(idStr);
        if (found != null) {
            Optional<Object> bound = tryHolderFromElementValue(mappedRegistry, found);
            if (bound.isPresent()) {
                return ensureHolderOptional(bound, knotCl);
            }
            Optional<Object> intrusive = tryCreateIntrusiveHolderReferenceOnMappedRegistry(
                mappedRegistry, found, knotCl);
            if (intrusive.isPresent()) {
                return intrusive;
            }
        }

        if (mappedRegistry == null || knotCl == null || entryResourceKey == null
                || Boolean.getBoolean("vyne.registry.skipDeferredRegisterBlockInject")) {
            return Optional.empty();
        }
        try {
            Object regKey = ReflectionHelper.call(mappedRegistry, "key", "method_30517");
            if (regKey == null) {
                return Optional.empty();
            }
            String path = ReflectionHelper.normalizeRegistryKeyLocationString(
                ReflectionHelper.extractRegistryKeyLocation(regKey));
            if (!"minecraft:block".equals(path)) {
                return Optional.empty();
            }
            Object rl = extractResourceLocationFromEntryKey(entryResourceKey);
            if (rl == null) {
                return Optional.empty();
            }
            String id = rl.toString();
            Object canonical = VyneRegistryInjector.resolveRegistryForPath(knotCl, path);
            Object block = net.neoforged.neoforge.registries.DeferredRegister
                .tryResolveEntryFromDeferredRegisters(id);
            if (block == null) {
                block = RegistryMirrorUtil.tryResolveBlockForBlockIdFromItemRegistry(knotCl, id);
            }
            if (block == null) {
                block = VyneGenericRegistry.getLenient(path, id);
            }
            if (block == null && canonical != null) {
                block = VyneRegistryInjector.getRegistryElementByLocationId(canonical, id, knotCl);
            }
            if (block == null) {
                return Optional.empty();
            }
            if (!id.startsWith("minecraft:")
                    && RegistryMirrorUtil.isVanillaAirBlock(block, knotCl)) {
                return Optional.empty();
            }
            VyneGenericRegistry.register(path, id, block);
            if (canonical == null) {
                return Optional.empty();
            }
            VyneRegistryInjector.injectIntoLiveRegistry(canonical, rl, block);
            Optional<Object> h = holderFromLiveRegistry(knotCl, entryResourceKey, canonical, true);
            if (h.isPresent() && isMinecraftHolderLike(h.get(), knotCl)) {
                return h;
            }
            Optional<Object> bound = tryHolderFromElementValue(canonical, block);
            if (bound.isPresent()) {
                Optional<Object> ensured = ensureHolderOptional(bound, knotCl);
                if (ensured.isPresent()) {
                    return ensured;
                }
            }
            Optional<Object> intrusive = tryCreateIntrusiveHolderReferenceOnMappedRegistry(canonical,
                block, knotCl);
            if (intrusive.isPresent()) {
                return intrusive;
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    public static Optional<Object> tryDeferredRegisterBlockHolderForMappedRegistryRl(Object mappedRegistry,
            ClassLoader knotCl, Object resourceLocation) {
        if (mappedRegistry == null || knotCl == null || resourceLocation == null
                || Boolean.getBoolean("vyne.registry.skipDeferredRegisterBlockInject")) {
            return Optional.empty();
        }
        try {
            Object regKey = ReflectionHelper.call(mappedRegistry, "key", "method_30517");
            if (regKey == null) {
                return Optional.empty();
            }
            String path = ReflectionHelper.normalizeRegistryKeyLocationString(
                ReflectionHelper.extractRegistryKeyLocation(regKey));
            if (!"minecraft:block".equals(path)) {
                return Optional.empty();
            }
            Object entryKey = invokeResourceKeyCreate(knotCl, regKey, resourceLocation);
            if (entryKey == null) {
                return Optional.empty();
            }
            return tryDeferredRegisterBlockHolderForMappedRegistry(mappedRegistry, knotCl, entryKey);
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    public static Object augmentRegistryOpsJsonForDecode(Object registryOps, ClassLoader knotCl) {
        if (registryOps == null || knotCl == null
                || Boolean.getBoolean("vyne.registry.skipRegistryOpsAugment56915")) {
            return null;
        }
        try {
            ClassLoader bridgeCl = dev.stolym.vyne.vcl.VCLManager.getBridgeClassLoader();
            ClassLoader compositeCl = new dev.stolym.vyne.vcl.CompositeClassLoader(knotCl, bridgeCl);

            Class<?> roClass = Class.forName("net.minecraft.class_6903", true, compositeCl);
            if (!roClass.isInstance(registryOps)) {
                return null;
            }
            Object delegateGetter = extractRegistryInfoGetterFromOps(registryOps);
            if (delegateGetter == null) {
                delegateGetter = createPureSyntheticRegistryInfoGetter(compositeCl);
            }
            if (delegateGetter == null) {
                return null;
            }
            Object wrappedGetter = wrapRegistryInfoGetter(delegateGetter, compositeCl);
            Class<?> getterIface = Class.forName(GETTER_IFACE, false, compositeCl);
            Method factory = findRegistryOpsJsonFactory(roClass, getterIface);
            if (factory == null) {
                return null;
            }
            factory.setAccessible(true);
            return factory.invoke(null, JsonOps.INSTANCE, wrappedGetter);
        } catch (Throwable t) {
            VyneLogger.debug("[RegistryDatapackLookupFallback] augmentRegistryOpsJsonForDecode: "
                + t.getMessage());
            return null;
        }
    }

    private static Method findRegistryOpsJsonFactory(Class<?> registryOpsClass, Class<?> getterIface) {
        try {
            return registryOpsClass.getMethod(
                "method_40414", com.mojang.serialization.DynamicOps.class, getterIface);
        } catch (NoSuchMethodException ignored) {
        }
        for (Method m : registryOpsClass.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (!com.mojang.serialization.DynamicOps.class.isAssignableFrom(p[0])) {
                continue;
            }
            if (!getterIface.isAssignableFrom(p[1])) {
                continue;
            }
            if (!registryOpsClass.isAssignableFrom(m.getReturnType())) {
                continue;
            }
            return m;
        }
        return null;
    }

    private static Object extractRegistryInfoGetterFromOps(Object registryOps) {
        if (registryOps == null) {
            return null;
        }
        ClassLoader ld = registryOps.getClass().getClassLoader();
        if (ld == null) {
            ld = RegistryDatapackLookupFallback.class.getClassLoader();
        }
        Class<?> getterIface = null;
        try {
            getterIface = Class.forName("net.minecraft.class_6903$class_7863", false, ld);
        } catch (Throwable ignored) {
        }
        for (String fn : new String[] { "field_40852", "f_276484_", "registryInfoGetter" }) {
            try {
                Field f = findDeclaredField(registryOps.getClass(), fn);
                if (f == null) {
                    continue;
                }
                f.setAccessible(true);
                Object v = f.get(registryOps);
                if (v != null && (getterIface == null || getterIface.isInstance(v))) {
                    return v;
                }
            } catch (Throwable ignored) {
            }
        }
        if (getterIface != null) {
            for (Class<?> c = registryOps.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    try {
                        f.setAccessible(true);
                        Object v = f.get(registryOps);
                        if (v != null && getterIface.isInstance(v)) {
                            return v;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        Class<?> c = registryOps.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(registryOps);
                    if (v == null) {
                        continue;
                    }
                    String tn = v.getClass().getName();
                    if (tn.contains("7863") || tn.contains("RegistryInfoGetter")) {
                        return v;
                    }
                } catch (Throwable ignored) {
                }
            }
            c = c.getSuperclass();
        }
        try {
            for (Method m : registryOps.getClass().getMethods()) {
                if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                Class<?> rt = m.getReturnType();
                String rtn = rt.getName();
                if (!rtn.contains("7863") && !rtn.contains("RegistryInfoGetter")) {
                    continue;
                }
                m.setAccessible(true);
                Object v = m.invoke(registryOps);
                if (v != null) {
                    return v;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Field findDeclaredField(Class<?> start, String name) {
        for (Class<?> cl = start; cl != null && cl != Object.class; cl = cl.getSuperclass()) {
            try {
                return cl.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static Object createPureSyntheticRegistryInfoGetter(ClassLoader cl) {
        if (cl == null) {
            return null;
        }
        try {
            Class<?> iface = Class.forName(GETTER_IFACE, false, cl);
            return Proxy.newProxyInstance(cl, new Class<?>[] { iface },
                new PureSyntheticRegistryInfoGetter(cl));
        } catch (Throwable t) {
            VyneLogger.debug("[RegistryDatapackLookupFallback] createPureSyntheticRegistryInfoGetter: "
                + t.getMessage());
            return null;
        }
    }

    private static final class PureSyntheticRegistryInfoGetter implements InvocationHandler {
        private final ClassLoader knotCl;

        PureSyntheticRegistryInfoGetter(ClassLoader knotCl) {
            this.knotCl = knotCl;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (args != null && args.length == 1
                    && ("getRegistryInfo".equals(name) || "method_46623".equals(name))) {
                return synthesizeRegistryInfoFromLive(args[0], knotCl);
            }
            if ("equals".equals(name) && args != null && args.length == 1) {
                return proxy == args[0];
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(name)) {
                return "VynePureSyntheticRegistryInfoGetter";
            }
            return null;
        }
    }

    public static Object wrapRegistryInfoGetter(Object delegate, ClassLoader knotCl) {
        if (delegate == null || knotCl == null
                || Boolean.getBoolean("vyne.registry.skipDatapackLookupFallback")) {
            return delegate;
        }
        if (isAlreadyOurRegistryInfoGetterProxy(delegate)) {
            return delegate;
        }
        try {
            Class<?> getterIface = Class.forName(GETTER_IFACE, false, knotCl);
            return Proxy.newProxyInstance(knotCl, new Class<?>[] { getterIface },
                new RegistryInfoGetterHandler(delegate, knotCl));
        } catch (Throwable t) {
            VyneLogger.warn("[RegistryDatapackLookupFallback] Could not wrap RegistryInfoGetter: "
                + t.getClass().getSimpleName() + ": " + t.getMessage());
            return delegate;
        }
    }

    public static Object wrapRegistryEntryLookup(Object delegateLookup, ClassLoader knotCl) {
        if (delegateLookup == null || knotCl == null
                || Boolean.getBoolean("vyne.registry.skipDatapackLookupFallback")) {
            return delegateLookup;
        }
        if (isAlreadyOurEntryLookupProxy(delegateLookup)) {
            return delegateLookup;
        }
        Object orig = delegateLookup;
        Object resolved = effectiveRegistryEntryLookupDelegate(delegateLookup, knotCl);
        delegateLookup = resolved != null ? resolved : orig;
        if (!isRegistryLookupLike(delegateLookup, knotCl)) {
            return orig;
        }
        try {
            Class<?>[] ifaces = proxyInterfacesForDelegate(delegateLookup, knotCl);
            if (ifaces == null || ifaces.length == 0) {
                return delegateLookup;
            }
            return Proxy.newProxyInstance(knotCl, ifaces,
                new EntryLookupHandler(delegateLookup, knotCl));
        } catch (Throwable t) {
            VyneLogger.debug("[RegistryDatapackLookupFallback] wrapRegistryEntryLookup: "
                + t.getMessage());
            return delegateLookup;
        }
    }

    private static boolean isRegistryLookupLike(Object o, ClassLoader knotCl) {
        if (o == null || knotCl == null) {
            return false;
        }
        ClassLoader ld = o.getClass().getClassLoader();
        if (ld == null) {
            ld = knotCl;
        }
        try {
            if (Class.forName(ENTRY_LOOKUP_IFACE, false, ld).isInstance(o)) {
                return true;
            }
            if (Class.forName(REGISTRATION_LOOKUP_IFACE, false, ld).isInstance(o)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (Class.forName(ENTRY_LOOKUP_IFACE, false, knotCl).isInstance(o)) {
                return true;
            }
            return Class.forName(REGISTRATION_LOOKUP_IFACE, false, knotCl).isInstance(o);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object effectiveRegistryEntryLookupDelegate(Object delegateLookup, ClassLoader knotCl) {
        if (delegateLookup == null || knotCl == null) {
            return null;
        }
        ClassLoader ifaceLd = delegateLookup.getClass().getClassLoader();
        if (ifaceLd == null) {
            ifaceLd = knotCl;
        }
        try {
            Class<?> lookupIface = Class.forName(ENTRY_LOOKUP_IFACE, false, ifaceLd);
            if (lookupIface.isInstance(delegateLookup)) {
                return delegateLookup;
            }
            Class<?> registrationIface = Class.forName(REGISTRATION_LOOKUP_IFACE, false, ifaceLd);
            if (registrationIface.isInstance(delegateLookup)) {
                return delegateLookup;
            }
            Method m69 = delegateLookup.getClass().getMethod("method_46769");
            m69.setAccessible(true);
            Object v69 = m69.invoke(delegateLookup);
            if (v69 != null) {
                if (lookupIface.isInstance(v69) || registrationIface.isInstance(v69)) {
                    return v69;
                }
                ClassLoader v69Ld = v69.getClass().getClassLoader();
                if (v69Ld == null) {
                    v69Ld = ifaceLd;
                }
                Class<?> v69Lookup = Class.forName(ENTRY_LOOKUP_IFACE, false, v69Ld);
                if (v69Lookup.isInstance(v69)) {
                    return v69;
                }
            }
            Method m71 = delegateLookup.getClass().getMethod("method_46771");
            m71.setAccessible(true);
            Object v71 = m71.invoke(delegateLookup);
            if (v71 != null && (lookupIface.isInstance(v71) || registrationIface.isInstance(v71))) {
                return v71;
            }
            if (v71 != null) {
                ClassLoader v71Ld = v71.getClass().getClassLoader();
                Class<?> v71Lookup = Class.forName(ENTRY_LOOKUP_IFACE, false, v71Ld != null ? v71Ld : ifaceLd);
                Class<?> v71Reg = Class.forName(REGISTRATION_LOOKUP_IFACE, false, v71Ld != null ? v71Ld : ifaceLd);
                if (v71Lookup.isInstance(v71) || v71Reg.isInstance(v71)) {
                    return v71;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static Optional<Object> optionalLiveWrappedEntryLookupForRegistryRef(Object registryTypeKey,
            ClassLoader knotCl) {
        if (registryTypeKey == null || knotCl == null
                || Boolean.getBoolean("vyne.registry.skipDatapackLookupFallback")) {
            return Optional.empty();
        }
        String raw = ReflectionHelper.extractRegistryKeyLocation(registryTypeKey);
        String norm = raw == null ? null : ReflectionHelper.normalizeRegistryKeyLocationString(raw);
        if (norm == null || !effectiveAugmentPaths().contains(norm)) {
            return Optional.empty();
        }
        Object reg = VyneRegistryInjector.resolveRegistryForPath(knotCl, norm);
        if (reg == null) {
            return Optional.empty();
        }
        Object del = null;
        try {
            Method m69 = reg.getClass().getMethod("method_46769");
            m69.setAccessible(true);
            del = m69.invoke(reg);
        } catch (Throwable t) {
            VyneLogger.debug("[RegistryDatapackLookupFallback] optionalLiveEntryLookup method_46769: "
                + t.getMessage());
        }
        if (del == null) {
            del = effectiveRegistryEntryLookupDelegate(reg, knotCl);
        }
        if (del == null || !isRegistryLookupLike(del, knotCl)) {
            if (vyneDebugDatapackLookup()) {
                VyneLogger.debug("[RegistryDatapackLookupFallback] optionalLive miss path=" + norm
                    + " delNull=" + (del == null) + " lookupLike="
                    + (del != null && isRegistryLookupLike(del, knotCl)));
            }
            return Optional.empty();
        }
        Object wrapped = wrapRegistryEntryLookup(del, knotCl);
        if (wrapped == null && vyneDebugDatapackLookup()) {
            VyneLogger.debug("[RegistryDatapackLookupFallback] optionalLive wrap null path=" + norm);
        }
        return wrapped != null ? Optional.of(wrapped) : Optional.empty();
    }

    public static Optional<Object> synthesizeWrappedRegistryEntryLookup(Object registryTypeKey,
            ClassLoader knotCl) {
        if (registryTypeKey == null || knotCl == null
                || Boolean.getBoolean("vyne.registry.skipDatapackLookupFallback")) {
            return Optional.empty();
        }
        String raw = ReflectionHelper.extractRegistryKeyLocation(registryTypeKey);
        String path = raw == null ? null : ReflectionHelper.normalizeRegistryKeyLocationString(raw);
        if (path == null || !effectiveAugmentPaths().contains(path)) {
            return Optional.empty();
        }
        Object reg = VyneRegistryInjector.resolveRegistryForPath(knotCl, path);
        if (reg == null) {
            return Optional.empty();
        }
        Object wrapped = wrapRegistryEntryLookup(reg, knotCl);
        if (wrapped == null || wrapped == reg) {
            return Optional.empty();
        }
        return Optional.of(wrapped);
    }

    private static boolean isAlreadyOurEntryLookupProxy(Object delegate) {
        if (delegate == null || !Proxy.isProxyClass(delegate.getClass())) {
            return false;
        }
        InvocationHandler h = Proxy.getInvocationHandler(delegate);
        return h instanceof EntryLookupHandler;
    }

    private static boolean isAlreadyOurRegistryInfoGetterProxy(Object delegate) {
        if (!Proxy.isProxyClass(delegate.getClass())) {
            return false;
        }
        InvocationHandler h = Proxy.getInvocationHandler(delegate);
        return h instanceof RegistryInfoGetterHandler;
    }

    private static final class RegistryInfoGetterHandler implements InvocationHandler {
        private final Object delegate;
        private final ClassLoader knotCl;

        RegistryInfoGetterHandler(Object delegate, ClassLoader knotCl) {
            this.delegate = delegate;
            this.knotCl = knotCl;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (args != null && args.length == 1
                    && ("getRegistryInfo".equals(name) || "method_46623".equals(name))) {
                Object registryKey = args[0];
                Object opt = invokeRegistryLookupDelegate(delegate, method, args);
                if (!shouldAugmentLookup(registryKey)) {
                    return opt;
                }
                if (opt instanceof Optional<?> o && o.isPresent()) {
                    return augmentRegistryInfoOptional(opt, knotCl);
                }
                Optional<?> liveFallback = synthesizeRegistryInfoFromLive(registryKey, knotCl);
                if (liveFallback != null && liveFallback.isPresent()) {
                    return augmentRegistryInfoOptional(liveFallback, knotCl);
                }
                return opt;
            }
            return invokeRegistryLookupDelegate(delegate, method, args);
        }
    }

    private static boolean shouldAugmentLookup(Object registryKey) {
        if (registryKey == null) {
            return false;
        }
        String loc = ReflectionHelper.extractRegistryKeyLocation(registryKey);
        return loc != null && effectiveAugmentPaths().contains(loc);
    }

    private static Optional<?> synthesizeRegistryInfoFromLive(Object registryTypeKey, ClassLoader cl) {
        if (registryTypeKey == null) {
            return Optional.empty();
        }
        String path = ReflectionHelper.extractRegistryKeyLocation(registryTypeKey);
        if (path == null || !effectiveAugmentPaths().contains(path)) {
            return Optional.empty();
        }
        Object reg = VyneRegistryInjector.resolveRegistryForPath(cl, path);
        if (reg == null) {
            return Optional.empty();
        }
        try {
            Class<?> regInfoClass = Class.forName(REG_INFO_CLASS, false, cl);
            Class<?> registrationLookup = Class.forName(REGISTRATION_LOOKUP_IFACE, false, cl);
            Object lookup = resolveRegistrationLookupForRegistryInfo(reg, registrationLookup);
            if (lookup == null) {
                return Optional.empty();
            }
            Method m = regInfoClass.getMethod("method_57074", registrationLookup);
            Object info = m.invoke(null, lookup);
            return Optional.ofNullable(info);
        } catch (Throwable t) {
            VyneLogger.debug("[RegistryDatapackLookupFallback] synthesize RegistryInfo: " + t.getMessage());
            return Optional.empty();
        }
    }

    private static Object resolveRegistrationLookupForRegistryInfo(Object reg, Class<?> registrationLookupIface) {
        if (reg == null) {
            return null;
        }
        if (registrationLookupIface.isInstance(reg)) {
            return reg;
        }
        try {
            Method m = reg.getClass().getMethod("method_46771");
            if (!Modifier.isPublic(m.getModifiers())) {
                m.setAccessible(true);
            }
            Object candidate = m.invoke(reg);
            if (candidate != null && registrationLookupIface.isInstance(candidate)) {
                return candidate;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Class<?>[] proxyInterfacesForDelegate(Object delegate, ClassLoader cl) {
        List<Class<?>> ifaces = new ArrayList<>(3);
        try {
            Class<?> registration = Class.forName(REGISTRATION_LOOKUP_IFACE, false, cl);
            if (registration.isInstance(delegate)) {
                ifaces.add(registration);
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> entry = Class.forName(ENTRY_LOOKUP_IFACE, false, cl);
            if (ifaces.isEmpty() && entry.isInstance(delegate)) {
                ifaces.add(entry);
            }
        } catch (Throwable ignored) {
        }
        if (!ifaces.isEmpty()) {
            return ifaces.toArray(Class[]::new);
        }
        try {
            return new Class<?>[] { Class.forName(ENTRY_LOOKUP_IFACE, false, cl) };
        } catch (Throwable t) {
            throw new IllegalStateException("Registry lookup interfaces unavailable", t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object augmentRegistryInfoOptional(Object optionalFromVanilla, ClassLoader cl) {
        if (!(optionalFromVanilla instanceof Optional<?> opt) || opt.isEmpty()) {
            return optionalFromVanilla;
        }
        Object regInfo = opt.get();
        try {
            Class<?> regInfoClass = Class.forName(REG_INFO_CLASS, false, cl);
            if (!regInfoClass.isInstance(regInfo)) {
                return optionalFromVanilla;
            }
            Class<?> lookupIface = Class.forName(ENTRY_LOOKUP_IFACE, false, cl);
            Object innerLookup = invokeRecordComponent(regInfo, "entryLookup", "comp_1131");
            if (innerLookup == null || !lookupIface.isInstance(innerLookup)) {
                innerLookup = findRecordComponentByType(regInfo, lookupIface);
            }
            if (innerLookup == null || !lookupIface.isInstance(innerLookup)) {
                return optionalFromVanilla;
            }
            Class<?>[] proxyIfaces = proxyInterfacesForDelegate(innerLookup, cl);
            Object wrappedLookup = Proxy.newProxyInstance(cl, proxyIfaces,
                new EntryLookupHandler(innerLookup, cl));
            Object owner = invokeRecordComponent(regInfo, "owner", "comp_1130");
            Object lifecycle = invokeRecordComponent(regInfo, "elementsLifecycle", "comp_1132");
            Constructor<?> ctor = findRegistryInfoCtor(regInfoClass);
            if (ctor == null) {
                return optionalFromVanilla;
            }
            ctor.setAccessible(true);
            boolean sameRef = owner == innerLookup
                || owner != null && innerLookup != null && owner.equals(innerLookup);
            Object ownerArg = sameRef ? wrappedLookup : Proxy.newProxyInstance(cl, proxyIfaces,
                new EntryLookupHandler(owner, cl));
            Object rebuilt = ctor.newInstance(ownerArg, wrappedLookup, lifecycle);
            return Optional.of(rebuilt);
        } catch (Throwable t) {
            VyneLogger.debug("[RegistryDatapackLookupFallback] augment failed: " + t.getMessage());
            return optionalFromVanilla;
        }
    }

    private static Object invokeRecordComponent(Object record, String... names) {
        Class<?> c = record.getClass();
        for (String n : names) {
            try {
                Method m = c.getMethod(n);
                return m.invoke(record);
            } catch (Throwable ignored) {
            }
        }
        for (Method m : c.getMethods()) {
            if (m.getParameterCount() != 0) {
                continue;
            }
            String n = m.getName();
            for (String want : names) {
                if (n.equals(want)) {
                    try {
                        return m.invoke(record);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return null;
    }

    private static Object findRecordComponentByType(Object record, Class<?> wantedType) {
        if (record == null || wantedType == null) {
            return null;
        }
        for (Method m : record.getClass().getMethods()) {
            if (m.getParameterCount() != 0) {
                continue;
            }
            if (!wantedType.isAssignableFrom(m.getReturnType())) {
                continue;
            }
            try {
                m.setAccessible(true);
                return m.invoke(record);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Constructor<?> findRegistryInfoCtor(Class<?> regInfoClass) {
        for (Constructor<?> c : regInfoClass.getConstructors()) {
            if (c.getParameterCount() == 3) {
                return c;
            }
        }
        for (Constructor<?> c : regInfoClass.getDeclaredConstructors()) {
            if (c.getParameterCount() == 3) {
                return c;
            }
        }
        return null;
    }

    private static Object tryExtractBackingRegistryFromEntryLookup(Object delegateLookup, ClassLoader cl) {
        if (delegateLookup == null || cl == null) {
            return null;
        }
        try {
            Class<?> registryIface = Class.forName("net.minecraft.class_2378", false, cl);
            for (Class<?> c = delegateLookup.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(delegateLookup);
                        if (v != null && registryIface.isAssignableFrom(v.getClass())) {
                            return v;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            for (Method m : delegateLookup.getClass().getMethods()) {
                if (m.getParameterCount() != 0 || !registryIface.isAssignableFrom(m.getReturnType())) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    Object v = invokeRegistryLookupDelegate(delegateLookup, m, new Object[0]);
                    if (v != null) {
                        return v;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean isTagKeyLike(Class<?> p0, ClassLoader knotCl, Object arg0) {
        if (p0 == null) {
            return false;
        }
        String p0n = p0.getName().toLowerCase(Locale.ROOT);
        if (p0n.contains("class_6862") || p0n.contains("tagkey")) {
            return true;
        }
        if (knotCl != null && arg0 != null) {
            try {
                Class<?> tk = Class.forName("net.minecraft.class_6862", false, knotCl);
                if (tk.isAssignableFrom(p0) || tk.isInstance(arg0)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static Object invokeRegistryLookupDelegate(Object delegate, Method ifaceMethod, Object[] args)
            throws Throwable {
        try {
            return ifaceMethod.invoke(delegate, args);
        } catch (IllegalArgumentException ex) {
            try {
                Method m = delegate.getClass().getMethod(
                    ifaceMethod.getName(), ifaceMethod.getParameterTypes());
                m.setAccessible(true);
                return m.invoke(delegate, args);
            } catch (ReflectiveOperationException ex2) {
                ex.addSuppressed(ex2);
                throw ex;
            }
        }
    }

    private static final class EntryLookupHandler implements InvocationHandler {
        private final Object delegateLookup;
        private final ClassLoader knotCl;

        EntryLookupHandler(Object delegateLookup, ClassLoader knotCl) {
            this.delegateLookup = delegateLookup;
            this.knotCl = knotCl;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (args != null && args.length == 1
                    && isTagKeyLike(method.getParameterTypes()[0], knotCl, args[0])) {
                return invokeRegistryLookupDelegate(delegateLookup, method, args);
            }
            if (args != null && args.length == 1
                    && isRegistryEntryKeyLike(method.getParameterTypes()[0], knotCl, args[0])) {
                Object key = args[0];
                String n = method.getName();
                Class<?> ret = method.getReturnType();

                if (ret == Optional.class || Optional.class.isAssignableFrom(ret)) {
                    Object fromDelegate = invokeRegistryLookupDelegate(delegateLookup, method, args);
                    if (fromDelegate instanceof Optional<?> o && o.isPresent()) {
                        return fromDelegate;
                    }
                    Object backingReg = tryExtractBackingRegistryFromEntryLookup(delegateLookup, knotCl);
                    Optional<Object> live = holderFromLiveRegistry(knotCl, key, backingReg);
                    if (live.isPresent()) {
                        Optional<Object> fin = finalizeHolderForRegistryLookup(
                            live.get(), knotCl, key, backingReg);
                        if (fin.isPresent()) {
                            return fin;
                        }
                    }
                    return fromDelegate;
                }

                if ("getOrThrow".equals(n) || "method_46747".equals(n)) {
                    Object backingReg0 = tryExtractBackingRegistryFromEntryLookup(delegateLookup, knotCl);
                    Optional<Object> opt = tryGetOptional(delegateLookup, key);
                    if (opt.isPresent()) {
                        return finalizeHolderForRegistryLookup(opt.get(), knotCl, key, backingReg0)
                            .orElse(opt.get());
                    }
                    Optional<Object> live = holderFromLiveRegistry(knotCl, key, backingReg0);
                    if (live.isPresent()) {
                        return finalizeHolderForRegistryLookup(live.get(), knotCl, key, backingReg0)
                            .orElse(live.get());
                    }
                    return invokeRegistryLookupDelegate(delegateLookup, method, args);
                }
            }
            return invokeRegistryLookupDelegate(delegateLookup, method, args);
        }
    }

    private static boolean isRegistryEntryKeyLike(Class<?> p0, ClassLoader knotCl, Object arg0) {
        if (p0 == null) {
            return false;
        }
        String p0n = p0.getName().toLowerCase(Locale.ROOT);
        if (p0n.contains("registrykey") || p0n.contains("class_5321")) {
            return true;
        }
        if (knotCl != null && arg0 != null) {
            try {
                Class<?> rk = Class.forName("net.minecraft.class_5321", false, knotCl);
                if (rk.isAssignableFrom(p0) || rk.isInstance(arg0)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static Optional<Object> tryGetOptional(Object delegateLookup, Object resourceKey)
            throws Throwable {
        Class<?> c = delegateLookup.getClass();
        for (String name : new String[] { "getOptional", "method_46746" }) {
            try {
                Method m = findMethodOneArg(c, delegateLookup, name, resourceKey);
                if (m != null) {
                    m.setAccessible(true);
                    Object r = invokeRegistryLookupDelegate(delegateLookup, m, new Object[] { resourceKey });
                    if (r instanceof Optional<?> o) {
                        return (Optional<Object>) o;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return Optional.empty();
    }

    private static Method findMethodOneArg(Class<?> start, Object delegate, String name,
                                           Object resourceKey) {
        Class<?> c = start;
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != 1) {
                    continue;
                }
                if (m.getParameterTypes()[0].isInstance(resourceKey)) {
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        for (Class<?> ifc : delegate.getClass().getInterfaces()) {
            for (Method m : ifc.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != 1) {
                    continue;
                }
                if (m.getParameterTypes()[0].isInstance(resourceKey)) {
                    return m;
                }
            }
        }
        for (Method m : delegate.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != 1) {
                continue;
            }
            if (m.getParameterTypes()[0].isInstance(resourceKey)) {
                return m;
            }
        }
        return null;
    }

    private static boolean isHolderReference(Object holder, ClassLoader cl) {
        if (holder == null || cl == null) {
            return false;
        }
        try {
            Class<?> refIface = Class.forName("net.minecraft.class_6880$class_6883", false, cl);
            return refIface.isInstance(holder);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean registryHolderIsReference(Object holder, ClassLoader cl) {
        return isHolderReference(holder, cl);
    }

    private static Optional<Object> coerceHolderToReferenceForElementCodec(Object holder, ClassLoader knotCl,
            String registryPath, Object preferredRegistry) {
        if (holder == null || knotCl == null) {
            return Optional.empty();
        }

        try {
            if (Boolean.FALSE.equals(ReflectionHelper.call(holder, "isBound", "method_40248"))) {
                ReflectionHelper.call(holder, "bindValue", "method_40250");
                if (Boolean.FALSE.equals(ReflectionHelper.call(holder, "isBound", "method_40248"))) {
                    VyneLogger.warn("[Vyne-Registry] Holder could not be bound: " + holder);
                    return Optional.empty(); 
                }
            }
        } catch (Throwable t) {
            VyneLogger.warn("[Vyne-Registry] Exception during holder binding: " + t.getMessage());
            return Optional.empty();
        }

        if (isHolderReference(holder, knotCl)) {
            return Optional.of(holder);
        }
        if (!isMinecraftHolderLike(holder, knotCl)) {
            return Optional.empty();
        }
        Object element = tryUnwrapHolderElement(holder);
        if (element == null) {
            return Optional.empty();
        }
        Object stackPeek = peekRegistryElementCodecPreferredRegistry();
        Object canonical = VyneRegistryInjector.resolveRegistryForPath(knotCl, registryPath);
        for (Object reg : new Object[] { preferredRegistry, stackPeek, canonical }) {
            if (reg == null) {
                continue;
            }
            Optional<Object> bound = tryHolderFromElementValue(reg, element);
            if (bound.isPresent() && isHolderReference(bound.get(), knotCl)) {
                return bound;
            }
            Optional<Object> intrusive = tryCreateIntrusiveHolderReferenceOnMappedRegistry(reg, element,
                knotCl);
            if (intrusive.isPresent()) {
                return intrusive;
            }
        }
        return Optional.empty();
    }

    private static Optional<Object> tryCreateIntrusiveHolderReferenceOnMappedRegistry(Object registry,
            Object element, ClassLoader knotCl) {
        if (registry == null || element == null || knotCl == null) {
            return Optional.empty();
        }
        try {
            Class<?> refIface = Class.forName("net.minecraft.class_6880$class_6883", false, knotCl);
            Class<?> elemClass = element.getClass();
            for (Class<?> c = registry.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 1) {
                        continue;
                    }
                    Class<?> p0 = m.getParameterTypes()[0];
                    if (!p0.isAssignableFrom(elemClass) && !p0.getName().equals(elemClass.getName())) {
                        continue;
                    }
                    if (p0.getName().contains("ResourceKey") || p0.getName().contains("class_5321")
                            || p0.getName().contains("ResourceLocation") || p0.getName().contains("class_2960")) {
                        continue;
                    }
                    Class<?> rt = m.getReturnType();
                    if (!refIface.isAssignableFrom(rt)) {
                        continue;
                    }
                    try {
                        m.setAccessible(true);
                        Object h = m.invoke(registry, element);
                        if (h != null && refIface.isInstance(h)) {
                            return Optional.of(h);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    private static Optional<Object> finalizeHolderForRegistryLookup(Object holder, ClassLoader knotCl,
            Object entryResourceKey, Object preferredRegistry) {
        if (holder == null || knotCl == null) {
            return Optional.empty();
        }
        if (isHolderReference(holder, knotCl)) {
            return Optional.of(holder);
        }
        String path = inferBuiltinRegistryPathForEntryKey(entryResourceKey);
        return coerceHolderToReferenceForElementCodec(holder, knotCl, path, preferredRegistry);
    }

    public static boolean isMinecraftHolderLike(Object o, ClassLoader knotCl) {
        if (o == null || o instanceof Number) {
            return false;
        }
        ClassLoader fromValue = o.getClass().getClassLoader();
        ClassLoader[] tryLoaders = fromValue != null
            ? new ClassLoader[] { fromValue, knotCl }
            : new ClassLoader[] { knotCl };
        for (ClassLoader ld : tryLoaders) {
            if (ld == null) {
                continue;
            }
            try {
                Class<?> holderIface = Class.forName("net.minecraft.class_6880", false, ld);
                if (holderIface.isInstance(o)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static Optional<Object> ensureHolderOptional(Optional<Object> o, ClassLoader cl) {
        if (o == null || o.isEmpty()) {
            return o == null ? Optional.empty() : o;
        }
        if (isMinecraftHolderLike(o.get(), cl)) {
            return o;
        }
        return Optional.empty();
    }

    private static Optional<Object> holderFromLiveRegistry(ClassLoader cl, Object resourceKey) {
        return holderFromLiveRegistry(cl, resourceKey, null, false);
    }

    private static Optional<Object> holderFromLiveRegistry(ClassLoader cl, Object resourceKey,
            Object preferredRegistry) {
        return holderFromLiveRegistry(cl, resourceKey, preferredRegistry, false);
    }

    private static Optional<Object> holderFromLiveRegistry(ClassLoader cl, Object resourceKey,
            Object preferredRegistry, boolean forceReference) {
        String path = inferBuiltinRegistryPathForEntryKey(resourceKey);
        if (path == null) {
            return Optional.empty();
        }
        Object explicitPreferred = preferredRegistry;
        Object stackPreferred = peekRegistryElementCodecPreferredRegistry();
        Object preferred = explicitPreferred != null ? explicitPreferred : stackPreferred;
        Object canonical = VyneRegistryInjector.resolveRegistryForPath(cl, path);
        Object primary = preferred != null ? preferred : canonical;
        if (primary == null) {
            return Optional.empty();
        }
        Optional<Object> r = holderFromLiveRegistryOnReg(cl, resourceKey, primary, path);
        if (r.isPresent() && isHolderReference(r.get(), cl)) {
            return r;
        }
        if (canonical != null && primary != null && !Objects.equals(primary, canonical)) {
            VyneRegistryInjector.dropCachedRegistry(path);
            canonical = VyneRegistryInjector.resolveRegistryForPath(cl, path);
            if (canonical != null) {
                Optional<Object> onCanon = holderFromLiveRegistryOnReg(cl, resourceKey, canonical, path);
                if (onCanon.isPresent() && isHolderReference(onCanon.get(), cl)) {
                    return onCanon;
                }
                if (r.isEmpty() && onCanon.isPresent()) {
                    r = onCanon;
                }
            }
        }
        if (r.isPresent() && !isHolderReference(r.get(), cl)) {
            Object regForRef = VyneRegistryInjector.resolveRegistryForPath(cl, path);
            Object rlObj = extractResourceLocationFromEntryKey(resourceKey);
            String idStr = rlObj != null ? rlObj.toString() : null;
            Object element = idStr != null && regForRef != null
                ? VyneRegistryInjector.getRegistryElementByLocationId(regForRef, idStr, cl)
                : null;
            if (element == null) {
                element = tryUnwrapHolderElement(r.get());
            }
            if (element != null && regForRef != null) {
                Optional<Object> bound = tryHolderFromElementValue(regForRef, element);
                if (bound.isPresent() && isHolderReference(bound.get(), cl)) {
                    return ensureHolderOptional(bound, cl);
                }
            }
        }
        boolean needBrute = r.isEmpty() || (r.isPresent() && !isHolderReference(r.get(), cl));
        if (needBrute && HOLDER_FROM_LIVE_BRUTE_DEPTH.get() < 2) {
            HOLDER_FROM_LIVE_BRUTE_DEPTH.set(HOLDER_FROM_LIVE_BRUTE_DEPTH.get() + 1);
            try {
                for (Object regAttempt : new Object[] { primary, canonical }) {
                    if (regAttempt == null) {
                        continue;
                    }
                    Optional<Object> brute = lenientBruteHolderFromMappedRegistryElement(regAttempt, cl,
                        resourceKey);
                    if (brute.isPresent() && isHolderReference(brute.get(), cl)) {
                        return brute;
                    }
                }
            } finally {
                HOLDER_FROM_LIVE_BRUTE_DEPTH.set(HOLDER_FROM_LIVE_BRUTE_DEPTH.get() - 1);
            }
        }
        if (forceReference && r.isPresent() && !isHolderReference(r.get(), cl)) {
            return Optional.empty();
        }
        return r;
    }

    private static Object tryUnwrapHolderElement(Object holder) {
        if (holder == null) {
            return null;
        }
        return ReflectionHelper.call(holder, "comp_349", "value", "method_40246");
    }

    private static Optional<Object> tryHolderFromElementValue(Object reg, Object value) {
        if (reg == null || value == null) {
            return Optional.empty();
        }
        Optional<Object> viaKey = tryHolderFromRegisteredElementKey(reg, value);
        if (viaKey.isPresent()) {
            return viaKey;
        }
        for (String n : new String[] { "method_47983", "getHolder", "wrapAsHolder" }) {
            for (Class<?> vc = value.getClass(); vc != null && vc != Object.class; vc = vc.getSuperclass()) {
                try {
                    Method m = reg.getClass().getMethod(n, vc);
                    m.setAccessible(true);
                    Object h = invokeRegistryLookupDelegate(reg, m, new Object[] { value });
                    if (h != null) {
                        try {
                            if (Boolean.TRUE.equals(ReflectionHelper.call(h, "isBound", "method_40248"))) {
                                return Optional.of(h);
                            }
                            ReflectionHelper.call(h, "bindValue", "method_40250");
                            if (Boolean.TRUE.equals(ReflectionHelper.call(h, "isBound", "method_40248"))) {
                                return Optional.of(h);
                            }
                            return Optional.of(h);
                        } catch (Throwable ignored) {
                            return Optional.of(h);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        for (Method m : reg.getClass().getMethods()) {
            if (m.getParameterCount() != 1 || !m.getParameterTypes()[0].isInstance(value)) {
                continue;
            }
            String mn = m.getName();
            boolean wanted = mn.contains("47983") || mn.equals("getHolder") || mn.equals("wrapAsHolder")
                || mn.startsWith("method_");
            if (!wanted) {
                continue;
            }
            Class<?> rt = m.getReturnType();
            if (rt == void.class) {
                continue;
            }
            try {
                m.setAccessible(true);
                Object h = invokeRegistryLookupDelegate(reg, m, new Object[] { value });
                if (h != null) {
                    return Optional.of(h);
                }
            } catch (Throwable ignored) {
            }
        }
        return Optional.empty();
    }

    public static Optional<Object> tryResourceKeyForRegistryValue(Object reg, Object value) {
        if (reg == null || value == null) {
            return Optional.empty();
        }
        Method getKey = null;
        for (Class<?> vc = value.getClass(); vc != null && vc != Object.class; vc = vc.getSuperclass()) {
            try {
                getKey = reg.getClass().getMethod("getResourceKey", vc);
                break;
            } catch (NoSuchMethodException ignored) {
            }
        }
        if (getKey == null) {
            for (Method m : reg.getClass().getMethods()) {
                if (m.getParameterCount() != 1 || !m.getParameterTypes()[0].isInstance(value)) {
                    continue;
                }
                if (m.getReturnType() != Optional.class || !"getResourceKey".equals(m.getName())) {
                    continue;
                }
                getKey = m;
                break;
            }
        }
        if (getKey == null) {
            return Optional.empty();
        }
        try {
            getKey.setAccessible(true);
            Object opt = invokeRegistryLookupDelegate(reg, getKey, new Object[] { value });
            if (!(opt instanceof Optional<?> o) || o.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(o.get());
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Object> tryHolderFromRegisteredElementKey(Object reg, Object value) {
        Optional<Object> rkOpt = tryResourceKeyForRegistryValue(reg, value);
        if (rkOpt.isEmpty()) {
            return Optional.empty();
        }
        try {
            return tryHolderByResourceKeyAndLocation(reg, rkOpt.get());
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Object> holderFromLiveRegistryOnReg(ClassLoader cl, Object resourceKey,
            Object reg, String path) {
        if (reg == null || path == null) {
            return Optional.empty();
        }
        try {
            if ("minecraft:block".equals(path)) {
                Object canon = VyneRegistryInjector.resolveRegistryForPath(cl, path);
                Object rlObj = extractResourceLocationFromEntryKey(resourceKey);
                if (canon != null && rlObj != null) {
                    String idStr = rlObj.toString();
                    Object genBlock = VyneGenericRegistry.getLenient(path, idStr);
                    if (genBlock != null && !idStr.startsWith("minecraft:")
                            && RegistryMirrorUtil.isVanillaAirBlock(genBlock, cl)) {
                        genBlock = null;
                    }
                    if (genBlock != null) {
                        Optional<Object> byGen = ensureHolderOptional(
                            tryHolderFromElementValue(canon, genBlock), cl);
                        if (byGen.isPresent() && isHolderReference(byGen.get(), cl)) {
                            return byGen;
                        }
                    }
                    Object blockVal = RegistryMirrorUtil.tryResolveBlockForBlockIdFromItemRegistry(cl,
                            idStr);
                    if (blockVal != null) {
                        Optional<Object> byIdentity = ensureHolderOptional(
                            tryHolderFromElementValue(canon, blockVal), cl);
                        if (byIdentity.isPresent() && isHolderReference(byIdentity.get(), cl)) {
                            return byIdentity;
                        }
                    }
                }
            }
            Optional<Object> direct = ensureHolderOptional(
                tryHolderByResourceKeyAndLocation(reg, resourceKey), cl);
            if (direct.isPresent()) {
                return direct;
            }
            Optional<Object> viaValue = ensureHolderOptional(
                tryHolderViaRegisteredValue(reg, resourceKey), cl);
            if (viaValue.isPresent()) {
                return viaValue;
            }
            Optional<Object> fromPublic = ensureHolderOptional(
                invokeRegistryHolderMethods(reg, resourceKey, true), cl);
            if (fromPublic.isPresent()) {
                return fromPublic;
            }
            Optional<Object> synced = ensureHolderOptional(
                trySyncGenericToLiveThenHolder(cl, resourceKey, reg, path), cl);
            if (synced.isPresent()) {
                return synced;
            }
            Optional<Object> fromGenericDirect = ensureHolderOptional(
                tryDirectHolderFromGenericSnapshot(cl, resourceKey, path), cl);
            if (fromGenericDirect.isPresent()) {
                return fromGenericDirect;
            }
            return ensureHolderOptional(invokeRegistryHolderMethods(reg, resourceKey, false), cl);
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    private static Optional<Object> tryDirectHolderFromGenericSnapshot(ClassLoader cl, Object resourceKey,
            String path) {
        Object rl = extractResourceLocationFromEntryKey(resourceKey);
        if (rl == null || path == null) {
            return Optional.empty();
        }
        String id = rl.toString();
        Object value = VyneGenericRegistry.getLenient(path, id);
        if ("minecraft:block".equals(path) && value != null && !id.startsWith("minecraft:")
                && RegistryMirrorUtil.isVanillaAirBlock(value, cl)) {
            value = null;
        }
        if (value == null && "minecraft:block".equals(path)) {
            value = RegistryMirrorUtil.tryResolveBlockForBlockIdFromItemRegistry(cl, id);
            if (value != null) {
                VyneGenericRegistry.register(path, id, value);
            }
        }
        if (value == null) {
            return Optional.empty();
        }
        if ("minecraft:block".equals(path) && !id.startsWith("minecraft:")
                && RegistryMirrorUtil.isVanillaAirBlock(value, cl)) {
            return Optional.empty();
        }
        return tryCreateDirectHolder(cl, value);
    }

    private static Optional<Object> tryDirectHolderFromBlockIdString(ClassLoader cl, String blockId,
            String registryPath) {
        if (cl == null || blockId == null || registryPath == null) {
            return Optional.empty();
        }
        String norm = ReflectionHelper.normalizeRegistryKeyLocationString(registryPath);
        if (!"minecraft:block".equals(norm)) {
            return Optional.empty();
        }
        Object value = RegistryMirrorUtil.tryResolveBlockForBlockIdFromItemRegistry(cl, blockId);
        if (value == null) {
            return Optional.empty();
        }
        VyneGenericRegistry.register(registryPath, blockId, value);
        return tryCreateDirectHolder(cl, value);
    }

    public static boolean canMaterializeDirectHolder(ClassLoader cl, Object value) {
        return tryCreateDirectHolder(cl, value).isPresent();
    }

    private static Optional<Object> tryCreateDirectHolder(ClassLoader cl, Object value) {
        if (value == null || cl == null) {
            return Optional.empty();
        }
        try {
            for (String holderBinary : new String[] {
                    "net.minecraft.class_6880",
                    "net.minecraft.core.Holder"
            }) {
                Class<?> holderIface;
                try {
                    holderIface = Class.forName(holderBinary, true, cl);
                } catch (Throwable ignored) {
                    continue;
                }
                for (String factoryName : new String[] { "method_40223", "direct" }) {
                    try {
                        Method m = holderIface.getMethod(factoryName, Object.class);
                        if (!Modifier.isStatic(m.getModifiers())) {
                            continue;
                        }
                        m.setAccessible(true);
                        Object h = m.invoke(null, value);
                        if (h != null && isMinecraftHolderLike(h, cl)) {
                            return Optional.of(h);
                        }
                    } catch (Throwable ignored) {
                    }
                }
                List<Method> candidates = new ArrayList<>();
                for (Method m : holderIface.getMethods()) {
                    if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 1) {
                        continue;
                    }
                    if (!m.getParameterTypes()[0].isInstance(value)) {
                        continue;
                    }
                    if (!holderIface.isAssignableFrom(m.getReturnType())) {
                        continue;
                    }
                    candidates.add(m);
                }
                candidates.sort((a, b) -> Integer.compare(factorySortKey(a.getName()), factorySortKey(b.getName())));
                for (Method m : candidates) {
                    try {
                        m.setAccessible(true);
                        Object h = m.invoke(null, value);
                        if (h != null && isMinecraftHolderLike(h, cl)) {
                            return Optional.of(h);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    private static int factorySortKey(String name) {
        if ("method_40223".equals(name) || "direct".equals(name)) {
            return 0;
        }
        return 1;
    }

    private static Optional<Object> trySyncGenericToLiveThenHolder(ClassLoader cl,
            Object resourceKey, Object reg, String path) {
        Object rl = extractResourceLocationFromEntryKey(resourceKey);
        if (rl == null || path == null) {
            return Optional.empty();
        }
        String id = rl.toString();
        Object value = VyneGenericRegistry.getLenient(path, id);
        if ("minecraft:block".equals(path) && value != null && !id.startsWith("minecraft:")
                && RegistryMirrorUtil.isVanillaAirBlock(value, cl)) {
            value = null;
        }
        if (value == null && "minecraft:block".equals(path)) {
            value = RegistryMirrorUtil.tryResolveBlockForBlockIdFromItemRegistry(cl, id);
            if (value != null) {
                VyneGenericRegistry.register(path, id, value);
                try {
                    if (!VyneRegistryInjector.injectIntoLiveRegistry(reg, rl, value)) {
                        VyneRegistryInjector.inject(path, id, value);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        if (value == null) {
            return Optional.empty();
        }
        if (registryHasValueForRl(reg, rl, cl, path)) {
            return Optional.empty();
        }
        if (!VyneRegistryInjector.injectIntoLiveRegistry(reg, rl, value)) {
            Object canon = VyneRegistryInjector.resolveRegistryForPath(cl, path);
            for (Object candidate : new Object[] { canon, reg }) {
                if (candidate == null) {
                    continue;
                }
                Optional<Object> byVal = tryHolderFromElementValue(candidate, value);
                if (byVal.isPresent() && isHolderReference(byVal.get(), cl)) {
                    return ensureHolderOptional(byVal, cl);
                }
            }
            return Optional.empty();
        }
        try {
            Optional<Object> direct = ensureHolderOptional(
                tryHolderByResourceKeyAndLocation(reg, resourceKey), cl);
            if (direct.isPresent()) {
                return direct;
            }
            return ensureHolderOptional(tryHolderViaRegisteredValue(reg, resourceKey), cl);
        } catch (Throwable t) {
            VyneLogger.debug("[RegistryDatapackLookupFallback] post-sync holder: " + t.getMessage());
        }
        return Optional.empty();
    }

    private static boolean registryHasValueForRl(Object reg, Object rl, ClassLoader cl, String path) {
        if (rl == null) {
            return false;
        }
        Class<?> rlClass = rl.getClass();
        for (String n : new String[] { "method_10223", "get", "getValue" }) {
            try {
                Method m = reg.getClass().getMethod(n, rlClass);
                Class<?> rt = m.getReturnType();
                if (rt == void.class || rt == boolean.class) {
                    continue;
                }
                m.setAccessible(true);
                Object v = invokeRegistryLookupDelegate(reg, m, new Object[] { rl });
                if (v != null) {
                    if ("minecraft:block".equals(path) && cl != null
                            && RegistryMirrorUtil.isBuiltInBlockRegistryDefaultAirForModId(
                                    rl.toString(), v, cl)) {
                        return false;
                    }
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static Optional<Object> tryHolderViaRegisteredValue(Object reg, Object resourceKey) {
        Object rl = extractResourceLocationFromEntryKey(resourceKey);
        if (rl == null) {
            return Optional.empty();
        }
        Class<?> rlClass = rl.getClass();
        Object value = null;
        for (String n : new String[] { "method_10223", "get", "getValue" }) {
            try {
                Method m = reg.getClass().getMethod(n, rlClass);
                Class<?> rt = m.getReturnType();
                if (rt == void.class || rt == boolean.class) {
                    continue;
                }
                m.setAccessible(true);
                value = invokeRegistryLookupDelegate(reg, m, new Object[] { rl });
                if (value != null) {
                    break;
                }
            } catch (Throwable ignored) {
            }
        }
        if (value == null) {
            return Optional.empty();
        }
        return tryHolderFromElementValue(reg, value);
    }

    private static Optional<Object> tryHolderByResourceKeyAndLocation(Object reg, Object resourceKey)
            throws Throwable {
        for (String n : new String[] { "method_40264", "getHolder" }) {
            try {
                Method m = reg.getClass().getMethod(n, resourceKey.getClass());
                m.setAccessible(true);
                Object r = invokeRegistryLookupDelegate(reg, m, new Object[] { resourceKey });
                if (r instanceof Optional<?> o && o.isPresent()) {
                    return (Optional<Object>) o;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        Object rl = extractResourceLocationFromEntryKey(resourceKey);
        if (rl != null) {
            for (String n : new String[] { "method_55841", "getHolder" }) {
                try {
                    Method m = reg.getClass().getMethod(n, rl.getClass());
                    m.setAccessible(true);
                    Object r = invokeRegistryLookupDelegate(reg, m, new Object[] { rl });
                    if (r instanceof Optional<?> o && o.isPresent()) {
                        return (Optional<Object>) o;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        for (String n : new String[] { "method_46746", "getOptional" }) {
            try {
                Method m = findMethodOneArg(reg.getClass(), reg, n, resourceKey);
                if (m != null) {
                    m.setAccessible(true);
                    Object r = invokeRegistryLookupDelegate(reg, m, new Object[] { resourceKey });
                    if (r instanceof Optional<?> o && o.isPresent()) {
                        return (Optional<Object>) o;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return Optional.empty();
    }

    private static Object extractResourceLocationFromEntryKey(Object resourceKey) {
        if (resourceKey == null) {
            return null;
        }
        for (String n : new String[] { "method_29177", "method_41185", "location" }) {
            try {
                Method m = resourceKey.getClass().getMethod(n);
                if (m.getParameterCount() != 0) {
                    continue;
                }
                m.setAccessible(true);
                return m.invoke(resourceKey);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean isAcceptableHolderLookupMethodName(String mn) {
        if ("method_29107".equals(mn) || "method_57058".equals(mn) || "method_35842".equals(mn)) {
            return false;
        }
        return mn.contains("40264") || mn.contains("55841") || mn.contains("40265")
            || mn.contains("46746") || mn.contains("Holder") || "getOptional".equals(mn);
    }

    @SuppressWarnings("unchecked")
    private static Optional<Object> invokeRegistryHolderMethods(Object reg, Object resourceKey,
                                                                boolean publicOnly) {
        Iterable<Method> methods = publicOnly
            ? java.util.Arrays.asList(reg.getClass().getMethods())
            : collectDeclaredMethods(reg.getClass());
        for (Method m : methods) {
            if (m.getParameterCount() != 1) {
                continue;
            }
            if (!m.getParameterTypes()[0].isInstance(resourceKey)) {
                continue;
            }
            String mn = m.getName();
            if (!isAcceptableHolderLookupMethodName(mn)) {
                continue;
            }
            Class<?> rt = m.getReturnType();
            if (rt == void.class || rt == boolean.class) {
                continue;
            }
            try {
                m.setAccessible(true);
                Object r = invokeRegistryLookupDelegate(reg, m, new Object[] { resourceKey });
                if (r instanceof Optional<?> o) {
                    if (o.isPresent()) {
                        return (Optional<Object>) o;
                    }
                    continue;
                }
                if (r != null) {
                    return Optional.of(r);
                }
            } catch (Throwable ignored) {
            }
        }
        return Optional.empty();
    }

    private static java.util.List<Method> collectDeclaredMethods(Class<?> start) {
        java.util.ArrayList<Method> out = new java.util.ArrayList<>();
        for (Class<?> c = start; c != null && c != Object.class; c = c.getSuperclass()) {
            out.addAll(java.util.Arrays.asList(c.getDeclaredMethods()));
        }
        return out;
    }

    private static String inferBuiltinRegistryPathForEntryKey(Object entryResourceKey) {
        try {
            Object registryTypeKey = readResourceKeyRegistryField(entryResourceKey);
            if (registryTypeKey != null) {
                String loc = ReflectionHelper.extractRegistryKeyLocation(registryTypeKey);
                if (loc != null && !loc.isEmpty() && !"null".equals(loc)) {
                    return loc;
                }
            }
        } catch (Throwable ignored) {
        }
        return "minecraft:block";
    }

    private static Object readResourceKeyRegistryField(Object entryResourceKey) {
        try {
            for (Method m : entryResourceKey.getClass().getMethods()) {
                if (m.getParameterCount() != 0) {
                    continue;
                }
                String n = m.getName();
                if (!"registry".equals(n) && !n.startsWith("method_")) {
                    continue;
                }
                Class<?> rt = m.getReturnType();
                String rtn = rt.getName();
                if (!rtn.contains("ResourceKey") && !rtn.contains("class_5321")) {
                    continue;
                }
                m.setAccessible(true);
                return m.invoke(entryResourceKey);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static com.mojang.serialization.DataResult<?> recoverAfterRegistryElementDecodeFailure(
            Object registryElementCodec,
            com.mojang.serialization.DynamicOps<?> ops,
            Object input,
            com.mojang.serialization.DataResult<?> failed,
            ClassLoader knotCl,
            Throwable decodeCause) {
        if (knotCl == null || failed == null) {
            return failed;
        }
        try {
            Object registryRef = readRegistryElementCodecRegistryRef(registryElementCodec);
            String idStr = parseCodecDecodeInputAsStringId(ops, input);
            String pathFromError = null;
            if (idStr == null || idStr.indexOf(':') < 0) {
                if (idStr == null) return failed;
            }
            Object rl = VyneRegistryInjector.toResourceLocation(knotCl, idStr);
            if (rl == null) {
                return failed;
            }
            String path = "minecraft:block";
            if (pathFromError != null && !pathFromError.isEmpty()) {
                path = pathFromError;
            } else if (registryRef != null) {
                String pl = ReflectionHelper.extractRegistryKeyLocation(registryRef);
                if (pl != null && !pl.isEmpty()) {
                    path = pl;
                }
            }
            traceElementCodecRecover("enter id=" + idStr + " path=" + path + " pathFromError=" + pathFromError
                + " hasRegistryRef=" + (registryRef != null)
                + " genericBlock=" + (VyneGenericRegistry.getLenient("minecraft:block", idStr) != null));
            Object liveReg = VyneRegistryInjector.resolveRegistryForPath(knotCl, path);
            Object entryKey = VyneRegistryInjector.createRegistryElementResourceKeyForLiveRegistry(
                knotCl, liveReg, rl);
            if (entryKey == null) {
                entryKey = invokeResourceKeyCreate(knotCl, registryRef, rl);
            }
            if (entryKey == null) {
                return failed;
            }
            Optional<Object> holder = ensureHolderOptional(
                holderFromLiveRegistry(knotCl, entryKey, liveReg), knotCl);
            traceElementCodecRecover("after holderFromLive empty=" + holder.isEmpty() + " liveRegNull="
                + (liveReg == null));
            if (holder.isEmpty() && liveReg != null) {
                String normPath = ReflectionHelper.normalizeRegistryKeyLocationString(path);
                Object rawEl = VyneRegistryInjector.getRegistryElementByLocationId(liveReg, idStr, knotCl);
                if (rawEl == null) {
                    rawEl = VyneRegistryInjector.tryGetVolatileBuiltinEntryByLocationId(knotCl, normPath,
                        idStr);
                }
                if (rawEl != null && "minecraft:block".equals(normPath) && !idStr.startsWith("minecraft:")
                        && RegistryMirrorUtil.isBuiltInBlockRegistryDefaultAirForModId(idStr, rawEl, knotCl)) {
                    rawEl = null;
                }
                if (rawEl != null) {
                    holder = ensureHolderOptional(tryHolderFromElementValue(liveReg, rawEl), knotCl);
                }
                traceElementCodecRecover("after rawEl/holderFromElement empty=" + holder.isEmpty()
                    + " rawElNull=" + (rawEl == null));
            }
            if (holder.isEmpty()) {
                String normDecodePath = ReflectionHelper.normalizeRegistryKeyLocationString(path);
                holder = ensureHolderOptional(
                    tryDirectHolderFromGenericSnapshot(knotCl, entryKey, normDecodePath), knotCl);
                traceElementCodecRecover("after generic snapshot normPath=" + normDecodePath + " empty="
                    + holder.isEmpty());
            }
            if (holder.isEmpty()) {
                String inferPath = inferBuiltinRegistryPathForEntryKey(entryKey);
                holder = ensureHolderOptional(
                    tryDirectHolderFromGenericSnapshot(knotCl, entryKey, inferPath), knotCl);
                traceElementCodecRecover("after generic snapshot inferPath=" + inferPath + " empty="
                    + holder.isEmpty());
            }
            if (holder.isEmpty()) {
                holder = ensureHolderOptional(
                    tryDirectHolderFromBlockIdString(knotCl, idStr, path), knotCl);
                traceElementCodecRecover("after blockIdString bridge empty=" + holder.isEmpty());
            }
            if (holder.isEmpty()) {
                traceElementCodecRecover("FAIL no holder id=" + idStr);
                return failed;
            }
            String effPath = path;
            String inferredPath = inferBuiltinRegistryPathForEntryKey(entryKey);
            if (inferredPath != null && !inferredPath.isEmpty()) {
                effPath = inferredPath;
            }
            Object resolved = holder.get();
            if (!isHolderReference(resolved, knotCl)) {
                Optional<Object> coerced = coerceHolderToReferenceForElementCodec(
                    resolved, knotCl, effPath, liveReg);
                if (coerced.isPresent()) {
                    resolved = coerced.get();
                }
            }
            if (!isHolderReference(resolved, knotCl)) {
                traceElementCodecRecover("FAIL not Holder.Reference id=" + idStr + " class="
                    + (resolved != null ? resolved.getClass().getName() : "null"));
                return failed;
            }
            traceElementCodecRecover("OK id=" + idStr);
            return com.mojang.serialization.DataResult.success(resolved);
        } catch (Throwable t) {
            VyneLogger.debug("[RegistryDatapackLookupFallback] RegistryElementCodec decode recovery: "
                + t.getMessage());
            return failed;
        }
    }

    private static Object readRegistryElementCodecRegistryRef(Object codec) {
        Class<?> c = codec.getClass();
        while (c != null && c != Object.class) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if ("field_25507".equals(f.getName())) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(codec);
                        if (v != null) {
                            return v;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            c = c.getSuperclass();
        }
        c = codec.getClass();
        while (c != null && c != Object.class) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                Class<?> ft = f.getType();
                String tn = ft.getName();
                if (!tn.contains("class_5321") && !tn.contains("ResourceKey")) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object v = f.get(codec);
                    if (v != null) {
                        return v;
                    }
                } catch (Throwable ignored) {
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static String extractDataResultErrorMessage(com.mojang.serialization.DataResult<?> dr) {
        if (dr == null) {
            return null;
        }
        try {
            return dr.error().map(e -> e.message()).orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String parseCodecDecodeInputAsStringId(com.mojang.serialization.DynamicOps<?> ops,
                                                        Object input) {
        if (input == null) {
            return null;
        }
        if (input instanceof com.google.gson.JsonElement je) {
            if (je.isJsonPrimitive() && je.getAsJsonPrimitive().isString()) {
                return je.getAsString();
            }
            if (je.isJsonObject()) {
                com.google.gson.JsonObject jo = je.getAsJsonObject();
                for (String k : new String[] { "Name", "name" }) {
                    com.google.gson.JsonElement name = jo.get(k);
                    if (name != null && name.isJsonPrimitive() && name.getAsJsonPrimitive().isString()) {
                        return name.getAsString();
                    }
                }
            }
            return null;
        }
        if (input instanceof java.util.Map<?, ?> m) {
            Object name = m.get("Name");
            if (name instanceof String s) {
                return s;
            }
            if (name != null) {
                return name.toString();
            }
        }
        if (input instanceof String s) {
            return s;
        }
        return null;
    }

    private static Object invokeResourceKeyCreate(ClassLoader cl, Object registryRef, Object rl) {
        try {
            Class<?> rk = Class.forName("net.minecraft.class_5321", true, cl);
            for (java.lang.reflect.Method m : rk.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                if (m.getParameterCount() != 2) {
                    continue;
                }
                Class<?> p0 = m.getParameterTypes()[0];
                Class<?> p1 = m.getParameterTypes()[1];
                if (!p0.isInstance(registryRef) || !p1.isInstance(rl)) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    Object out = m.invoke(null, registryRef, rl);
                    if (out != null && rk.isInstance(out)) {
                        return out;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
