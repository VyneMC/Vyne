/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.registry;

import dev.stolym.vyne.util.ReflectionHelper;
import dev.stolym.vyne.util.VyneLogLevel;
import dev.stolym.vyne.util.VyneLogger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class VyneRegistryInjector {
    private static final Map<String, Object> REGISTRY_CACHE = new ConcurrentHashMap<>();

    private static final Set<Integer> DRAINED_DEFERRED_IDS = ConcurrentHashMap.newKeySet();

    private static final Map<String, String> REGISTRY_INTERMEDIARY_KEYS = Map.of(
        "minecraft:block",            "field_11146.field_11146.minecraft:block",
        "minecraft:item",             "field_11146.field_11146.minecraft:item",
        "minecraft:entity_type",      "field_11146.field_11146.minecraft:entity_type",
        "minecraft:block_entity_type","field_11146.field_11146.minecraft:block_entity_type",
        "minecraft:fluid",            "field_11146.field_11146.minecraft:fluid",
        "neoforge:fluid_type",        "NeoForgeRegistries.FLUID_TYPES"
    );

    private static final java.util.Set<String> KNOWN_UNEMULATED = java.util.Set.of(
        "neoforge:global_loot_modifier_serializers",
        "neoforge:biome_modifier_serializers",
        "neoforge:structure_modifier_serializers",
        "neoforge:condition_codecs",
        "neoforge:ingredient_serializers",
        "neoforge:attachment_types"
    );

    public static void inject(ClassLoader cl, String registryPath, Object id, Object value) {
        try {
            Object registry = resolveRegistry(cl, registryPath);
            if (registry == null) {
                if (KNOWN_UNEMULATED.contains(registryPath)) {
                    if (dev.stolym.vyne.util.ContainmentLogThrottle.shouldLog(
                            "registry-unemulated", registryPath,
                            Long.MAX_VALUE )) {
                        VyneLogger.info("[VyneRegistryInjector] Skipping injections into "
                            + registryPath + " — NeoForge-only registry, not emulated by "
                            + "Vyne 0.1.x. Features depending on it (loot modifiers, biome "
                            + "modifiers, …) won't apply. Further ids for this registry are silenced.");
                    }
                    return;
                }
                VyneLogger.warn("[VyneRegistryInjector] No live registry resolved for path: " + registryPath
                    + " (id=" + id + " not injected)");
                return;
            }
            Object rl = toResourceLocation(cl, id.toString());
            if (rl == null) return;
            registerValue(registry, rl, value, cl);
            VyneLogger.info("[VyneRegistryInjector] Injected " + id + " -> " + value
                + " into " + registryPath);
        } catch (Throwable t) {
            Throwable cause = t;
            if (t instanceof java.lang.reflect.InvocationTargetException ite) {
                cause = ite.getTargetException();
            }
            VyneLogger.warn("[VyneRegistryInjector] Failed to inject " + id
                + " into " + registryPath + ": " + cause);
            if (cause != null) {
                for (StackTraceElement ste : cause.getStackTrace()) {
                    VyneLogger.warn("[VyneRegistryInjector]   at " + ste);
                }
            }
        }
    }

    public static void inject(String registryPath, Object id, Object value) {
        inject(Thread.currentThread().getContextClassLoader(), registryPath, id, value);
    }

    public static void dispatchDeferredRegister(Object deferredRegister, Object eventBus) {
        if (deferredRegister == null || eventBus == null) return;
        try {
            for (Method m : deferredRegister.getClass().getMethods()) {
                if (m.getName().equals("register") && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    m.invoke(deferredRegister, eventBus);
                    return;
                }
            }
        } catch (Throwable t) {
            VyneLogger.warn("[VyneRegistryInjector] DeferredRegister.register failed: " + t);
        }
    }

    public static void drainDeferredRegisters(Object modMainInstance, Object eventBus,
                                               ClassLoader modCl) {
        if (modMainInstance == null) return;
        java.util.Set<String> seen = new java.util.HashSet<>();
        Class<?> cursor = modMainInstance.getClass();
        while (cursor != null && !cursor.equals(Object.class)) {
            for (Field f : cursor.getDeclaredFields()) {
                String sig = cursor.getName() + "#" + f.getName();
                if (!seen.add(sig)) continue;

                String typeName = f.getType().getName();
                boolean isDeferred = typeName.contains("DeferredRegister");
                boolean isRegistrate = typeName.contains("AbstractRegistrate");

                if (!isDeferred && !isRegistrate) continue;

                try {
                    f.setAccessible(true);
                    Object reg = f.get(Modifier.isStatic(f.getModifiers()) ? null : modMainInstance);
                    if (reg != null) {
                        if (isDeferred) {
                            drainOneDeferredRegister(reg, eventBus, "@" + f.getName());
                        } else {
                            drainOneRegistrate(reg, eventBus, "@" + f.getName());
                        }
                    }
                } catch (Throwable t) {
                    VyneLogger.warn("[VyneRegistryInjector] drainDeferredRegisters field "
                        + f.getName() + " failed: " + t);
                }
            }
            cursor = cursor.getSuperclass();
        }
    }

    public static void drainDeferredRegistersFromModPackage(Path jarPath, Class<?> modMainClass,
                                                             Object eventBus, ClassLoader modCl) {
        if (eventBus == null || modCl == null || modMainClass == null) {
            return;
        }

        Set<String> classNames = new HashSet<>();
        VyneLogger.info("[Vyne-Feeder] Scanning JAR " + jarPath.getFileName() + " (exhaustive scan)...");
        if (jarPath != null && Files.isRegularFile(jarPath)) {
            try (ZipFile zf = new ZipFile(jarPath.toFile())) {
                Enumeration<? extends ZipEntry> en = zf.entries();
                int totalClasses = 0;
                while (en.hasMoreElements()) {
                    ZipEntry entry = en.nextElement();
                    String name = entry.getName();
                    if (!name.endsWith(".class")) {
                        continue;
                    }
                    totalClasses++;

                    if (!classBytesDeclareStaticDeferredRegister(zf, entry)) {
                        continue;
                    }
                    classNames.add(name.substring(0, name.length() - 6).replace('/', '.'));
                }
                VyneLogger.info("[Vyne-Feeder]   - Found " + classNames.size() + " candidate classes out of " + totalClasses + " classes in JAR.");
            } catch (Throwable t) {
                VyneLogger.warn("[Vyne-Feeder]   - Zip scan failed for " + jarPath.getFileName() + ": " + t);
            }
        }
        for (String binName : classNames) {
            try {
                Class<?> c = Class.forName(binName, true, modCl);
                drainDeferredRegistersFromClassOnly(c, eventBus);
            } catch (ExceptionInInitializerError eiie) {
                Throwable inner = eiie.getException();
                if (inner == null) inner = eiie.getCause();
                VyneLogger.warn("[VyneRegistryInjector] <clinit> failed for " + binName
                    + ": " + (inner == null ? eiie.toString()
                                            : inner.getClass().getName() + ": " + inner.getMessage()));
                if (inner != null) {
                    StackTraceElement[] trace = inner.getStackTrace();
                    int limit = Math.min(trace.length, 8);
                    for (int i = 0; i < limit; i++) {
                        VyneLogger.warn("[VyneRegistryInjector]   at " + trace[i]);
                    }

                    Throwable causeWalker = inner.getCause();
                    int depth = 1;
                    java.util.Set<Throwable> seen = new java.util.HashSet<>();
                    seen.add(inner);
                    while (causeWalker != null && seen.add(causeWalker) && depth < 4) {
                        VyneLogger.warn("[VyneRegistryInjector]   caused by: "
                            + causeWalker.getClass().getName() + ": " + causeWalker.getMessage());
                        StackTraceElement[] ct = causeWalker.getStackTrace();
                        int cl = Math.min(ct.length, 6);
                        for (int i = 0; i < cl; i++) {
                            VyneLogger.warn("[VyneRegistryInjector]     at " + ct[i]);
                        }
                        causeWalker = causeWalker.getCause();
                        depth++;
                    }
                }
            } catch (Throwable t) {
                VyneLogger.debug("[VyneRegistryInjector] skip class " + binName + ": " + t.getMessage());
            }
        }
    }

    private static boolean classBytesDeclareStaticDeferredRegister(ZipFile zf, ZipEntry entry) {
        try (InputStream is = zf.getInputStream(entry)) {
            ClassReader cr = new ClassReader(is);
            final boolean[] found = { false };
            cr.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor,
                                               String signature, Object value) {
                    if (!found[0]
                            && (access & Opcodes.ACC_STATIC) != 0
                            && descriptor != null
                            && (descriptor.contains("DeferredRegister") || descriptor.contains("Registrate"))) {
                        found[0] = true;
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return found[0];
        } catch (Throwable t) {
            return false;
        }
    }

    private static void drainDeferredRegistersFromClassOnly(Class<?> c, Object eventBus) {
        Class<?> cursor = c;
        while (cursor != null && cursor != Object.class) {
            for (Field f : cursor.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (!f.getType().getName().contains("DeferredRegister")) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object dr = f.get(null);
                    if (dr != null) {
                        drainOneDeferredRegister(dr, eventBus,
                            cursor.getSimpleName() + "." + f.getName());
                    }
                } catch (Throwable t) {
                    VyneLogger.debug("[VyneRegistryInjector] field " + cursor.getName() + "#" + f.getName()
                        + ": " + t.getMessage());
                }
            }
            cursor = cursor.getSuperclass();
        }
    }

    private static void drainOneRegistrate(Object registrate, Object eventBus, String label) {
        if (registrate == null) return;
        trackDeferredRegisterForLateSync(registrate);
        try {
            Class<?> clazz = registrate.getClass();
            while (clazz != null && !clazz.equals(Object.class)) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (f.getName().equals("entries") || f.getName().contains("entries")) {
                        f.setAccessible(true);
                        Object entries = f.get(registrate);
                        if (entries instanceof java.util.Collection<?> col) {
                            for (Object entry : col) {
                                for (Method m : entry.getClass().getMethods()) {
                                    if (m.getName().equals("register") && m.getParameterCount() == 0) {
                                        m.invoke(entry);
                                    }
                                }
                            }
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
            VyneLogger.debug("[VyneRegistryInjector] AbstractRegistrate drained — " + label);
        } catch (Throwable t) {
            VyneLogger.warn("[VyneRegistryInjector] AbstractRegistrate drain failed for " + label + ": " + t);
        }
    }

    private static void drainOneDeferredRegister(Object deferredRegister, Object eventBus, String label) {
        int id = System.identityHashCode(deferredRegister);
        if (!DRAINED_DEFERRED_IDS.add(id)) {
            return;
        }

        try {
            for (Method m : deferredRegister.getClass().getMethods()) {
                if (m.getName().equals("register") && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    m.invoke(deferredRegister, eventBus);
                    break;
                }
            }

            VyneLogger.debug("[VyneRegistryInjector] DeferredRegister.register(bus) — " + label);
        } catch (Throwable t) {
            VyneLogger.warn("[VyneRegistryInjector] DeferredRegister drain failed for " + label + ": " + t);
        }
    }

    private static Object resolveRegistry(ClassLoader cl, String registryPath) {
        try {
            if (registryPath.equals("neoforge:fluid_type")) {
                Object reg = resolveNeoForgeFluidTypeRegistry(cl);
                return reg;
            }

            String rlClassName = intermediaryClassName("net/minecraft/resources/ResourceLocation", cl);
            Class<?> rlClass = Class.forName(rlClassName, true, cl);

            String registriesClassName = intermediaryClassName(
                "net/minecraft/core/registries/BuiltInRegistries", cl);
            Class<?> builtIn = Class.forName(registriesClassName, true, cl);

            Object builtInMatch = builtInRegistriesByPath(builtIn).get(registryPath);
            if (builtInMatch != null) {
                return builtInMatch;
            }

            Object targetRl = createResourceLocation(rlClass, registryPath);

            String rootClassName = intermediaryClassName("net/minecraft/core/Registry", cl);
            Class<?> rootClass = Class.forName(rootClassName, true, cl);
            for (Field f : rootClass.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object root = f.get(null);
                if (root == null) continue;
                for (Method m : root.getClass().getMethods()) {
                    if (m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isAssignableFrom(rlClass)
                            && (m.getName().contains("get") || m.getName().contains("method_"))) {
                        try {
                            Object reg = m.invoke(root, targetRl);
                            if (reg != null) {
                                return reg;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable t) {
            VyneLogger.debug("[VyneRegistryInjector] resolveRegistry failed for "
                + registryPath + ": " + t);
        }
        return null;
    }

    private record BuiltInRegistryIndex(Class<?> owner, java.util.Map<String, Object> byPath) {}

    private static volatile BuiltInRegistryIndex builtInRegistryIndex;

    private static java.util.Map<String, Object> builtInRegistriesByPath(Class<?> builtIn) throws IllegalAccessException {
        BuiltInRegistryIndex cached = builtInRegistryIndex;
        if (cached != null && cached.owner() == builtIn) {
            return cached.byPath();
        }
        java.util.Map<String, Object> byPath = new java.util.HashMap<>();
        boolean complete = true;
        for (Field f : builtIn.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            Object candidate = f.get(null);
            if (candidate == null) {
                complete = false;
                continue;
            }
            Object candidateKey = dev.stolym.vyne.util.ReflectionHelper.call(
                candidate, "key", "method_30517");
            if (candidateKey == null) continue;
            String loc = dev.stolym.vyne.util.ReflectionHelper.extractRegistryKeyLocation(
                candidateKey);
            if (loc != null) {
                byPath.putIfAbsent(loc, candidate);
            }
        }
        if (complete) {
            builtInRegistryIndex = new BuiltInRegistryIndex(builtIn, byPath);
        }
        return byPath;
    }

    private static Object resolveNeoForgeFluidTypeRegistry(ClassLoader cl) throws Throwable {
        try {
            Class<?> nfr = Class.forName(
                "net.neoforged.neoforge.registries.NeoForgeRegistries", true, cl);
            for (Field f : nfr.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object val = f.get(null);
                if (val != null && val.toString().toLowerCase().contains("fluid_type")) {
                    return val;
                }
            }
        } catch (ClassNotFoundException ignored) {}
        return dev.stolym.vyne.fluid.VyneFluidTypeRegistry.all();
    }

    private static void registerValue(Object registry, Object rl, Object value,
                                       ClassLoader cl) throws Throwable {
        if (value == null) return;
        value = unwrapHolderIfNeeded(value, cl);
        if (value == null) return;

        java.lang.reflect.Field frozenField = null;
        java.lang.reflect.Field intrusiveCacheField = null;
        boolean wasFrozen = false;
        Object oldCache = null;

        try {
            try {
                Class<?> curr = registry.getClass();
                while (curr != null && curr != Object.class) {
                    for (java.lang.reflect.Field f : curr.getDeclaredFields()) {
                        String fn = f.getName();
                        if (frozenField == null && (fn.equals("frozen") || fn.equals("field_36488") || fn.equals("field_11003") || fn.equals("field_36463"))) {
                            frozenField = f;
                            frozenField.setAccessible(true);
                        } else if (intrusiveCacheField == null && (fn.equals("intrusiveHolderCache") || fn.equals("field_36490") || fn.equals("field_40584"))) {
                            intrusiveCacheField = f;
                            intrusiveCacheField.setAccessible(true);
                        }
                    }
                    if (frozenField != null && intrusiveCacheField != null) break;
                    curr = curr.getSuperclass();
                }

                if (frozenField != null) {
                    wasFrozen = (boolean) frozenField.get(registry);
                    if (wasFrozen) {
                        frozenField.set(registry, false);
                    }
                }

                if (intrusiveCacheField != null) {
                    Object cache = intrusiveCacheField.get(registry);
                    if (cache == null) {
                        intrusiveCacheField.set(registry, new java.util.IdentityHashMap<>());
                    }
                }

                if (intrusiveCacheField != null) {
                    Object cache = intrusiveCacheField.get(registry);
                    if (cache == null) {
                        cache = new java.util.IdentityHashMap<>();
                        intrusiveCacheField.set(registry, cache);
                    }

                    if (!((Map<?,?>)cache).containsKey(value)) {
                        boolean success = false;
                        try {
                            Method createIntrusive = null;
                            curr = registry.getClass();
                            while (curr != null && curr != Object.class) {
                                for (Method m : curr.getDeclaredMethods()) {
                                    String mn = m.getName();
                                    if (mn.equals("createIntrusiveHolder") || mn.equals("method_40263") || mn.equals("method_40271") || mn.equals("method_10271")) {
                                        createIntrusive = m;
                                        break;
                                    }
                                }
                                if (createIntrusive != null) break;
                                curr = curr.getSuperclass();
                            }

                            if (createIntrusive != null) {
                                createIntrusive.setAccessible(true);
                                try {
                                    createIntrusive.invoke(registry, value);
                                    success = true;
                                } catch (Throwable t) {
                                }
                            }
                        } catch (Throwable t) {}

                        if (!success) {
                            manuallyInjectIntrusiveHolder(registry, intrusiveCacheField, value, cl);
                        }
                    }

                    forceBindIntrusiveHolder(registry, value, cl);
                }
            } catch (Throwable t) {
                VyneLogger.debug("[Vyne-Recovery] Setup failed for " + rl + ": " + t);
            }

            boolean registered = registerWithInfo(registry, rl, value, cl);
            for (Method m : registered ? new Method[0] : registry.getClass().getMethods()) {
                if (m.getName().equals("register") || m.getName().contains("method_10230")) {
                     if (m.getParameterCount() == 4) {
                         int rawId = 0; 
                         try {
                             Method sizeM = registry.getClass().getMethod("size");
                             rawId = (int) sizeM.invoke(registry) + 2000;
                             Field byIdF = null;
                             Class<?> c = registry.getClass();
                             while (c != null) {
                                 for (Field f : c.getDeclaredFields()) {
                                     if (f.getName().equals("byId") || f.getName().equals("field_11006")) {
                                         byIdF = f; break;
                                     }
                                 }
                                 if (byIdF != null) break;
                                 c = c.getSuperclass();
                             }
                             if (byIdF != null) {
                                 byIdF.setAccessible(true);
                                 Object byId = byIdF.get(registry);
                                 Method size = byId.getClass().getMethod("size");
                                 rawId = (int) size.invoke(byId);
                             }
                         } catch (Throwable ignored) {}
                         Object key = createResourceKey(registry, rl, cl);

                         Class<?> lifecycleClass = Class.forName("com.mojang.serialization.Lifecycle", true, cl);
                         Object lifecycle = lifecycleClass.getMethod("stable").invoke(null);

                         m.setAccessible(true);
                         m.invoke(registry, rawId, key, value, lifecycle);
                         registered = true;
                         break;
                     }
                }
            }
            if (!registered) {
                for (Method m : registry.getClass().getMethods()) {
                    if (!m.getName().equals("register") && !m.getName().contains("method_10230")
                            && !m.getName().contains("method_10245")) continue;
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 2 && value != null && params[1].isAssignableFrom(value.getClass())) {
                        Object convertedRl = convertResourceLocation(rl, params[0]);
                        if (convertedRl != null && value != null) {
                            if (m.getParameterCount() == 2) {
                                m.invoke(registry, convertedRl, value);
                            } else {
                                m.invoke(registry, rl, convertedRl, value);
                            }
                            registered = true;
                            break;
                        }
                    }
                }
            }

            if (!registered) {
                throw new IllegalStateException("no register method on " + registry.getClass().getName()
                        + " accepts this value");
            }
            forceBindIntrusiveHolder(registry, value, cl);
            net.minecraftforge.vyne.VyneShims.applyPendingSettings(value);
            return;
        } finally {
            try {
                if (wasFrozen && frozenField != null) {
                    frozenField.set(registry, true);
                }
            } catch (Throwable t) {}
        }
    }

    private static boolean registerWithInfo(Object registry, Object rl, Object value, ClassLoader cl) throws Throwable {
        Class<?> infoClass;
        try {
            infoClass = Class.forName("net.minecraft.class_9248", true, cl);
        } catch (ClassNotFoundException e) {
            return false;
        }
        Object builtIn = null;
        for (Field f : infoClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == infoClass) {
                f.setAccessible(true);
                builtIn = f.get(null);
                break;
            }
        }
        if (builtIn == null) return false;
        for (Method m : registry.getClass().getMethods()) {
            Class<?>[] params = m.getParameterTypes();
            if (params.length != 3 || params[2] != infoClass || Modifier.isStatic(m.getModifiers())) continue;
            if (!params[0].getName().equals("net.minecraft.class_5321")) continue;
            Class<?> locationClass = Class.forName("net.minecraft.class_2960", true, cl);
            Object location = locationClass.getMethod("method_60654", String.class).invoke(null, rl.toString());
            if (isRegistered(registry, value)) return true;
            Object registryKey = registry.getClass().getMethod("method_30517").invoke(registry);
            Object key = params[0].getMethod("method_29179", params[0], locationClass).invoke(null, registryKey, location);
            m.setAccessible(true);
            m.invoke(registry, key, value, builtIn);
            return true;
        }
        return false;
    }

    private static boolean isRegistered(Object registry, Object value) {
        try {
            Method m = registry.getClass().getMethod("method_29113", Object.class);
            return ((java.util.Optional<?>) m.invoke(registry, value)).isPresent();
        } catch (ReflectiveOperationException | ClassCastException e) {
            return false;
        }
    }

    private static boolean tryInvokeIntrusiveHolder(Method createIntrusive, Object registry,
                                                    Object first, Object second) {
        try {
            createIntrusive.invoke(registry, first, second);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object tryCreateIntrusiveHolderViaRegistry(Object registry, Object value,
                                                              ClassLoader cl) {
        if (registry == null || value == null || cl == null) return null;
        try {
            Class<?> holderClass = Class.forName("net.minecraft.class_6880", false, cl);
            for (Class<?> curr = registry.getClass(); curr != null && curr != Object.class;
                    curr = curr.getSuperclass()) {
                for (Method m : curr.getDeclaredMethods()) {
                    if (Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 1) {
                        continue;
                    }
                    String mn = m.getName();
                    if (!mn.equals("createIntrusiveHolder") && !mn.equals("wrapAsHolder")
                            && !mn.contains("40263") && !mn.contains("40271")
                            && !mn.contains("10271") && !mn.contains("47983")) {
                        continue;
                    }
                    Class<?> p0 = m.getParameterTypes()[0];
                    if (!p0.isAssignableFrom(value.getClass())
                            && !p0.getName().equals(value.getClass().getName())) {
                        continue;
                    }
                    if (!holderClass.isAssignableFrom(m.getReturnType())
                            && m.getReturnType() != Object.class) {
                        continue;
                    }
                    try {
                        m.setAccessible(true);
                        Object holder = m.invoke(registry, value);
                        if (holder != null && holderClass.isInstance(holder)) {
                            return holder;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Object manuallyInjectIntrusiveHolder(Object registry, Field cacheField, Object value, ClassLoader cl) {
        if (cacheField == null || value == null || cl == null) return null;
        try {
            Map<Object, Object> cache = (Map<Object, Object>) cacheField.get(registry);
            if (cache == null) {
                cache = new java.util.IdentityHashMap<>();
                cacheField.set(registry, cache);
            }
            Object existing = cache.get(value);
            if (existing != null) return existing;

            Object holder = tryCreateIntrusiveHolderViaRegistry(registry, value, cl);
            if (holder != null) {
                cache.put(value, holder);
                return holder;
            }

            Class<?> refClass = Class.forName("net.minecraft.class_6880$class_6883", false, cl);

            Method standAlone = null;
            for (Method m : refClass.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())
                        && m.getParameterCount() == 2
                        && refClass.isAssignableFrom(m.getReturnType())) {
                    standAlone = m;
                    break;
                }
            }

            if (standAlone != null) {
                standAlone.setAccessible(true);
                holder = standAlone.invoke(null, null, null);
                boolean bound = false;
                for (Method m : holder.getClass().getDeclaredMethods()) {
                    if (m.getName().equals("bindValue") || m.getName().equals("method_40250")) {
                        m.setAccessible(true);
                        m.invoke(holder, value);
                        bound = true;
                        break;
                    }
                }
                if (!bound) {
                    VyneLogger.warn("[Vyne-Recovery] Holder for " + value + " was created but bindValue/method_40250 could not be found.");
                }
                cache.put(value, holder);
                return holder;
            } else {
                VyneLogger.warn("[Vyne-Recovery] Could not construct intrusive holder via registry or "
                    + "Holder.Reference factory for class: " + refClass.getName());
            }
        } catch (Throwable t) {
            VyneLogger.warn("[Vyne-Recovery] manuallyInjectIntrusiveHolder failed: " + t);
            t.printStackTrace();
        }
        return null;
    }

    private static void forceBindIntrusiveHolder(Object registry, Object value, ClassLoader cl) {
        if (value == null || cl == null) return;
        try {
            java.lang.reflect.Field intrusiveCacheField = null;
            Field frozenField = null;
            Class<?> curr = registry.getClass();
            while (curr != null && curr != Object.class) {
                for (java.lang.reflect.Field f : curr.getDeclaredFields()) {
                    String fn = f.getName();
                    if (fn.equals("intrusiveHolderCache") || fn.equals("field_36490") || fn.equals("field_40584")) {
                        intrusiveCacheField = f;
                        intrusiveCacheField.setAccessible(true);
                    } else if (fn.equals("frozen") || fn.equals("field_10926")) {
                        frozenField = f;
                        frozenField.setAccessible(true);
                    }
                }
                if (intrusiveCacheField != null && frozenField != null) break;
                curr = curr.getSuperclass();
            }
            if (intrusiveCacheField == null) return;

            boolean wasFrozen = frozenField != null && (boolean) frozenField.get(registry);
            if (wasFrozen) {
                frozenField.set(registry, false);
            }

            try {
                Map<Object, Object> cache = (Map<Object, Object>) intrusiveCacheField.get(registry);
                if (cache == null) {
                    cache = new java.util.IdentityHashMap<>();
                    intrusiveCacheField.set(registry, cache);
                }

                Object holder = cache.get(value);
                if (holder == null) {
                    manuallyInjectIntrusiveHolder(registry, intrusiveCacheField, value, cl);
                    holder = cache.get(value);
                }
                if (holder != null) {
                    for (Method m : holder.getClass().getMethods()) {
                        if (m.getName().equals("bindValue") || m.getName().equals("method_40250")) {
                            m.setAccessible(true);
                            m.invoke(holder, value);
                            break;
                        }
                    }
                }
            } finally {
                if (wasFrozen) {
                    frozenField.set(registry, true);
                }
            }
        } catch (Throwable t) {
            VyneLogger.debug("[Vyne-Recovery] forceBindIntrusiveHolder failed: " + t);
        }
    }

    private static Object unwrapHolderIfNeeded(Object value, ClassLoader cl) {
        if (value == null || cl == null) return value;
        try {
            Class<?> holderClass = Class.forName("net.minecraft.class_6880", false, cl);
            if (holderClass.isInstance(value)) {
                Object unwrapped = ReflectionHelper.call(value, "method_40246", "value");
                if (unwrapped != null) return unwrapped;
            }
        } catch (Throwable ignored) {}
        return value;
    }

    private static Object convertResourceLocation(Object rl, Class<?> target) {
        if (target.isInstance(rl)) return rl;
        if (target.getName().contains("ResourceLocation") || target.getName().contains("class_2960")) {
            return dev.stolym.vyne.util.ReflectionHelper.newResourceLocationKnotWithStringFallback(
                target, rl.toString());
        }
        return null;
    }

    public static Object toResourceLocation(ClassLoader cl, String id) {
        try {
            Class<?> rlClass;
            try {
                rlClass = Class.forName("net.minecraft.resources.ResourceLocation", true, cl);
            } catch (ClassNotFoundException e) {
                String rlClassName = intermediaryClassName("net/minecraft/resources/ResourceLocation", cl);
                rlClass = Class.forName(rlClassName, true, cl);
            }
            return createResourceLocation(rlClass, id);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object createResourceLocation(Class<?> rlClass, String id) throws Throwable {
        Object out = dev.stolym.vyne.util.ReflectionHelper.newResourceLocationKnotWithStringFallback(
            rlClass, id);
        if (out != null) {
            return out;
        }
        throw new IllegalStateException("Cannot construct ResourceLocation for: " + id);
    }

    private static Object createResourceKey(Object registry, Object rl, ClassLoader cl) throws Throwable {
        Object registryKey = null;
        for (String name : new String[] {"key", "method_30517", "getRegistryKey"}) {
            try {
                Method m = registry.getClass().getMethod(name);
                if (m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    registryKey = m.invoke(registry);
                    break;
                }
            } catch (Throwable ignored) {}
        }
        if (registryKey == null) {
            String path = getRegistryPath(registry);
            if (path != null) {
                Object registryLocation = toResourceLocation(cl, path);
                registryKey = createRegistryTypeKey(cl, registryLocation);
            }
        }
        Object key = createRegistryElementResourceKeyForLiveRegistry(cl, registryKey, rl);
        if (key != null) {
            return key;
        }
        throw new IllegalStateException("Cannot construct ResourceKey for " + rl + " in registry " + registry);
    }

    private static Object createRegistryTypeKey(ClassLoader cl, Object registryLocation) {
        if (registryLocation == null) return null;
        for (Class<?> keyClass : resourceKeyClasses(cl)) {
            for (Method m : keyClass.getMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 1) continue;
                String name = m.getName();
                if (!name.equals("createRegistryKey") && !name.equals("method_29177")) continue;
                Object converted = convertResourceLocation(registryLocation, m.getParameterTypes()[0]);
                if (converted == null) continue;
                try {
                    m.setAccessible(true);
                    return m.invoke(null, converted);
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static java.util.List<Class<?>> resourceKeyClasses(ClassLoader cl) {
        java.util.ArrayList<Class<?>> classes = new java.util.ArrayList<>();
        for (String name : new String[] {"net.minecraft.resources.ResourceKey", "net.minecraft.class_5321"}) {
            try {
                Class<?> c = Class.forName(name, true, cl);
                if (!classes.contains(c)) {
                    classes.add(c);
                }
            } catch (Throwable ignored) {}
        }
        return classes;
    }

    public static String intermediaryClassName(String mojmapInternal, ClassLoader cl) {
        if (dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
            return dev.stolym.vyne.mappings.MappingRegistry.get()
                .mapClass(mojmapInternal).replace('/', '.');
        }
        return switch (mojmapInternal) {
            case "net/minecraft/resources/ResourceLocation" -> "net.minecraft.class_2960";
            case "net/minecraft/core/Registry"              -> "net.minecraft.class_2378";
            case "net/minecraft/core/registries/BuiltInRegistries" -> "net.minecraft.class_7923";
            default -> mojmapInternal.replace('/', '.');
        };
    }

    public static Object resolveRegistryForPath(ClassLoader cl, String registryPath) {
        return resolveRegistry(cl, registryPath);
    }

    public static boolean injectIntoLiveRegistry(Object registry, Object rl, Object value) {
        if (registry == null || rl == null || value == null) return false;
        try {
            registerValue(registry, rl, value, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean liveRegistryHasId(ClassLoader knot, String myPath, String idStr) {
        try {
            Object registry = resolveRegistry(knot, myPath);
            if (registry == null) return false;
            Class<?> locationClass = Class.forName("net.minecraft.class_2960", true, knot);
            Object location = locationClass.getMethod("method_60654", String.class).invoke(null, idStr);
            Object value = registry.getClass().getMethod("method_10223", locationClass).invoke(registry, location);
            if (value == null) return false;
            Object key = registry.getClass().getMethod("method_10221", Object.class).invoke(registry, value);
            return location.equals(key);
        } catch (Throwable t) {
            return false;
        }
    }

    private static final Set<Object> TRACKED_FOREIGN_REGISTERS;
    static {
        synchronized (System.getProperties()) {
            Set<Object> existing = (Set<Object>) System.getProperties().get("vyne.tracked_registers");
            if (existing == null) {
                existing = ConcurrentHashMap.newKeySet();
                System.getProperties().put("vyne.tracked_registers", existing);
            }
            TRACKED_FOREIGN_REGISTERS = existing;
        }
    }

    public static void trackDeferredRegisterForLateSync(Object dr) {
        if (dr != null) TRACKED_FOREIGN_REGISTERS.add(dr);
    }

    public static Object tryResolveEntryFromTrackedDeferredRegisters(String fullId) {
        for (Object dr : TRACKED_FOREIGN_REGISTERS) {
            Object found = resolveFromDeferred(dr, fullId);
            if (found != null) return found;
        }

        for (Object dr : net.neoforged.neoforge.registries.DeferredRegister.getAllRegisters()) {
            Object found = resolveFromDeferred(dr, fullId);
            if (found != null) return found;
        }
        return null;
    }

    private static Object resolveFromDeferred(Object dr, String fullId) {
        try {
            Method getEntries = dr.getClass().getMethod("getEntries");
            getEntries.setAccessible(true);
            java.util.Collection<?> entries = (java.util.Collection<?>) getEntries.invoke(dr);
            for (Object holder : entries) {
                Method getId = holder.getClass().getMethod("getId");
                getId.setAccessible(true);
                Object idObj = getId.invoke(holder);
                if (fullId.equals(idObj.toString())) {
                    try {
                        Method resolve = dr.getClass().getDeclaredMethod("resolveHolderValue", Class.forName("net.neoforged.neoforge.registries.DeferredHolder", true, dr.getClass().getClassLoader()));
                        resolve.setAccessible(true);
                        return resolve.invoke(dr, holder);
                    } catch (Throwable t) {
                        return holder;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static void fillAllRegistryGapsForTrackedForeign(ClassLoader k) {
        for (Object dr : TRACKED_FOREIGN_REGISTERS) {
            try {
                if (dr.getClass().getName().contains("AbstractRegistrate")) {
                    ForceSync.forceRegisterRegistrate(dr);
                    continue;
                }
                Method m = dr.getClass().getDeclaredMethod("fillBlockRegistryGapsIntoVyneGenericOne");
                m.setAccessible(true);
                m.invoke(dr);
            } catch (Throwable ignored) {
                try {
                    ForceSync.forceRegisterRegistrate(dr);
                } catch (Throwable ignored2) {}
            }
        }
    }

    public static void forceRegisterDeferredRegister(Object dr) {
        try {
            Method getEntries = dr.getClass().getMethod("getEntries");
            getEntries.setAccessible(true);
            java.util.Collection<?> entries = (java.util.Collection<?>) getEntries.invoke(dr);

            Object registryKey = ReflectionHelper.call(dr, "getRegistryKey", "method_30517");
            if (registryKey == null) return;
            String path = ReflectionHelper.normalizeRegistryKeyLocationString(
                ReflectionHelper.extractRegistryKeyLocation(registryKey));

            Object liveRegistry = resolveRegistryForPath(Thread.currentThread().getContextClassLoader(), path);
            if (liveRegistry == null) {
                VyneLogger.warn("[Vyne-Recovery] Proactive registration: Cannot resolve live registry for path: " + path);
                return;
            }

            VyneLogger.info("[Vyne-Recovery] Processing " + entries.size() + " entries for " + path);

            for (Object holder : entries) {
                Method getId = holder.getClass().getMethod("getId");
                getId.setAccessible(true);
                Object idObj = getId.invoke(holder);
                String id = idObj.toString();

                Object value = null;
                try {
                    Method resolve = dr.getClass().getDeclaredMethod("resolveHolderValue", Class.forName("net.neoforged.neoforge.registries.DeferredHolder", true, dr.getClass().getClassLoader()));
                    resolve.setAccessible(true);
                    value = resolve.invoke(dr, holder);
                } catch (NoSuchMethodException nsme) {
                    Method get = holder.getClass().getMethod("get");
                    get.setAccessible(true);
                    value = get.invoke(holder);
                }

                Object rl = toResourceLocation(Thread.currentThread().getContextClassLoader(), id);
                if (rl != null && value != null) {
                    try {
                        forceBindIntrusiveHolder(liveRegistry, value, Thread.currentThread().getContextClassLoader());
                    } catch (Throwable t) {
                        VyneLogger.debug("[Vyne-Recovery] Pre-registration intrusive binding failed for " + id + ": " + t.getMessage());
                    }

                    try {
                        registerValue(liveRegistry, rl, value, Thread.currentThread().getContextClassLoader());
                        VyneLogger.info("[Vyne-Recovery] Proactively registered " + id + " into " + path + " (live)");
                    } catch (Throwable t) {
                        VyneLogger.warn("[Vyne-Recovery] register() call failed for " + id + ": " + t.getMessage());
                    }
                } else if (value == null) {
                    VyneLogger.debug("[Vyne-Recovery] Skipping null value for " + id + " (registry not ready)");
                } else {
                    VyneLogger.warn("[Vyne-Recovery] Failed to resolve id: " + id + " or value: " + value);
                }
            }
        } catch (Throwable t) {
            VyneLogger.warn("[Vyne-Recovery] Proactive registration failed for register: " + t);
        }
    }

    public static void syncMissingGenericEntriesToLiveRegistry(ClassLoader k, String path) {
        try {
            Object live = resolveRegistryForPath(k, path);
            if (live == null) return;
            java.util.Map<String, Object> genericEntries = dev.stolym.vyne.registry.VyneGenericRegistry.getRegistry(path);
            if (genericEntries != null) {
                for (java.util.Map.Entry<String, Object> entry : genericEntries.entrySet()) {
                    if (!liveRegistryHasId(k, path, entry.getKey())) {
                        Object rl = toResourceLocation(k, entry.getKey());
                        if (rl != null) {
                            injectIntoLiveRegistry(live, rl, entry.getValue());
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void invalidateCache() {
        REGISTRY_CACHE.clear();
    }

    public static Object resolveViaBridge(Object holder, ClassLoader bridgeCl) {
        try {
            Method getMethod = holder.getClass().getMethod("get");
            getMethod.setAccessible(true);
            return getMethod.invoke(holder);
        } catch (Throwable t) {
            VyneLogger.debug("[Vyne-Recovery] resolveViaBridge failed for " + holder + ": " + t.getMessage());
            return null;
        }
    }

    public static void forceFireRegistration(String registryKey, ClassLoader knot) {
        try {
            VyneLogger.info("[Vyne-Recovery] Forcing RegisterEvent for " + registryKey);

            Class<?> eventClass = Class.forName("net.neoforged.neoforge.registries.RegisterEvent", true, knot);
            Object event = eventClass.getConstructor(Object.class).newInstance(registryKey);

            Class<?> vebClass = Class.forName("net.neoforged.bus.api.VyneEventBus", true, knot);

            Method globalMethod = null;
            for (Method m : vebClass.getDeclaredMethods()) {
                if (m.getName().equals("global") && m.getParameterCount() == 0) {
                    globalMethod = m;
                    break;
                }
            }
            if (globalMethod == null) throw new RuntimeException("Could not find global() on VyneEventBus");

            Object bus = globalMethod.invoke(null);

            Method postMethod = null;
            for (Method m : bus.getClass().getMethods()) {
                if (m.getName().equals("post") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == Object.class) {
                    postMethod = m;
                    break;
                }
            }
            if (postMethod == null) throw new RuntimeException("Could not find post(Object) on " + bus.getClass());

            postMethod.invoke(bus, event);

            if ("minecraft:item".equals(registryKey)) {
                VyneRegistryGate.unlock();
            }
        } catch (Throwable t) {
            VyneLogger.warn("[Vyne-Recovery] forceFireRegistration failed for " + registryKey + ": " + t);
        }
    }

    private static Method[] safeDeclaredMethods(Class<?> cls) {
        try {
            return cls.getDeclaredMethods();
        } catch (Throwable t) {
            VyneLogger.warn("[Vyne-Recovery] Could not enumerate declared methods on " + cls
                + " (a referenced type is likely missing on this classloader): " + t);
            return new Method[0];
        }
    }

    private static Method findDeclaredMethodResilient(Class<?> cls, String name, Class<?>... params) {
        try {
            return cls.getDeclaredMethod(name, params);
        } catch (NoSuchMethodException direct) {
            for (Method m : safeDeclaredMethods(cls)) {
                if (m.getName().equals(name) && m.getParameterCount() == params.length) {
                    return m;
                }
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void syncDatapackCriticalRegistries(ClassLoader knot) {
        boolean hasEmulatedMods = dev.stolym.vyne.core.VyneLauncher.getPendingVCLMods() != null
                && !dev.stolym.vyne.core.VyneLauncher.getPendingVCLMods().isEmpty();
        if (!hasEmulatedMods) {
            VyneLogger.info("[Vyne-Recovery] No (Neo)Forge mods present — skipping critical registry sync.");
            VyneRegistryGate.unlock();
            return;
        }

        VyneLogger.info("[Vyne-Recovery] Proactively syncing all datapack-critical registries on " + knot + "...");

        try {
            Class<?> drClass = Class.forName("net.neoforged.neoforge.registries.DeferredRegister", true, knot);

            Method fillGapsMethod = findDeclaredMethodResilient(
                drClass, "fillAllRegistryGapsIntoVyneGeneric");
            if (fillGapsMethod != null) {
                fillGapsMethod.invoke(null);
            } else {
                VyneLogger.warn("[Vyne-Recovery] Could not find fillAllRegistryGapsIntoVyneGeneric on " + drClass);
            }

            for (String registryKey : new String[] {
                "minecraft:block", "minecraft:item", "minecraft:recipe_type", "minecraft:recipe_serializer",
                "minecraft:worldgen/feature", "minecraft:worldgen/configured_feature", "minecraft:worldgen/placed_feature"
            }) {
                forceFireRegistration(registryKey, knot);
            }

            ClassLoader bridgeCl = dev.stolym.vyne.vcl.VCLManager.getBridgeClassLoader();

            Method getAllRegistersMethod = findDeclaredMethodResilient(drClass, "getAllRegisters");

            if (getAllRegistersMethod == null) {
                VyneLogger.warn("[Vyne-Recovery] Could not find getAllRegisters on " + drClass);
                return;
            }

            @SuppressWarnings("unchecked")
            List<Object> allRegisters = (List<Object>) getAllRegistersMethod.invoke(null);

            VyneLogger.info("[Vyne-Recovery] Found " + allRegisters.size() + " registers to sync via reflective lookup.");

            Method isCriticalMethod = findDeclaredMethodResilient(
                drClass, "isDatapackCriticalRegistry", String.class);

            for (Object dr : allRegisters) {
                try {
                    Object registryKey = ReflectionHelper.call(dr, "getRegistryKey", "method_30517");
                    if (registryKey == null) continue;
                    String path = ReflectionHelper.normalizeRegistryKeyLocationString(
                        ReflectionHelper.extractRegistryKeyLocation(registryKey));

                    if (path != null && isCriticalMethod != null && (boolean)isCriticalMethod.invoke(null, path)) {
                        Method getEntries = null;
                        for (Method m : dr.getClass().getMethods()) {
                            if (m.getName().equals("getEntries") && m.getParameterCount() == 0) {
                                getEntries = m;
                                break;
                            }
                        }

                        if (getEntries != null) {
                            java.util.Collection<?> entries = (java.util.Collection<?>) getEntries.invoke(dr);
                            for (Object holder : entries) {
                                resolveViaBridge(holder, bridgeCl);
                            }
                        }

                        try {
                            forceRegisterDeferredRegister(dr);
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable t) {
                    VyneLogger.debug("[Vyne-Recovery] Sync failed for dr: " + t);
                }
            }
        } catch (Throwable t) {
            VyneLogger.error("[Vyne-Recovery] CRITICAL: Failed to perform reflective registry sync: " + t.getMessage(), t);
        } finally {
            VyneRegistryGate.unlock();
        }
    }
    public static String getRegistryPath(Object registry) {
        if (registry == null) return null;
        try {
            Object key = dev.stolym.vyne.util.ReflectionHelper.call(registry, "key", "method_30517");
            if (key != null) return dev.stolym.vyne.util.ReflectionHelper.extractRegistryKeyLocation(key);
        } catch (Throwable ignored) {}
        return null;
    }

    public static void syncMissingGenericEntriesIntoSpecificRegistry(ClassLoader cl, String path, Object registry) {
        try {
            java.util.Map<String, Object> genericEntries = dev.stolym.vyne.registry.VyneGenericRegistry.getRegistry(path);
            if (genericEntries != null) {
                for (java.util.Map.Entry<String, Object> entry : genericEntries.entrySet()) {
                    Object rl = toResourceLocation(cl, entry.getKey());
                    if (rl != null) {
                        injectIntoLiveRegistry(registry, rl, entry.getValue());
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static int reactiveInjectMissingKeys(IllegalStateException e, Object registry, ClassLoader cl) {
        String msg = e.getMessage();
        if (msg == null) return 0;

        net.neoforged.neoforge.registries.DeferredRegister.fillAllRegistryGapsIntoVyneGeneric();

        int injected = 0;

        java.util.regex.Pattern p1 = java.util.regex.Pattern.compile("(?:Unknown registry key in ResourceKey\\[(?:minecraft:root / )?([a-z0-9_.-]+:[a-z0-9_./-]+)\\]:|Failed to get element)\\s+([^;]+(?:;\\s*[^;]+)*)");
        java.util.regex.Matcher m1 = p1.matcher(msg);

        while (m1.find()) {
            String targetRegistryPath = m1.group(1);
            String idsStr = m1.group(2);
            for (String missingId : idsStr.split(";")) {
                injected += tryReactiveInject(targetRegistryPath, missingId.trim(), registry, cl);
            }
        }

        java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("Unbound values in registry ResourceKey\\[(?:minecraft:root / )?([a-z0-9_.-]+:[a-z0-9_./-]+)\\]: \\[(.+?)\\]");
        java.util.regex.Matcher m2 = p2.matcher(msg);

        while (m2.find()) {
            String targetRegistryPath = m2.group(1);
            String idsStr = m2.group(2);
            for (String missingId : idsStr.split(", ")) {
                injected += tryReactiveInject(targetRegistryPath, missingId.trim(), registry, cl);
            }
        }

        return injected;
    }

    private static int tryReactiveInject(String targetRegistryPath, String missingId, Object registry, ClassLoader cl) {
        Object val = null;
        if (targetRegistryPath != null) {
            val = dev.stolym.vyne.registry.VyneGenericRegistry.getLenient(targetRegistryPath, missingId);
            if (VyneLogLevel.isVerbose()) VyneLogger.debug("[Vyne-Recovery] Looking for " + missingId + " in " + targetRegistryPath + ": " + (val != null ? "FOUND" : "MISSING"));
        }
        if (val == null) {
            val = dev.stolym.vyne.registry.VyneGenericRegistry.findAnywhere(missingId);
            if (VyneLogLevel.isVerbose()) VyneLogger.debug("[Vyne-Recovery] Looking for " + missingId + " anywhere: " + (val != null ? "FOUND" : "MISSING"));
        }

        if (val == null) {
            String modId = missingId.contains(":") ? missingId.split(":")[0] : null;
            if (VyneLogLevel.isVerbose()) VyneLogger.debug("[Vyne-Recovery] Still missing " + missingId + ", trying proactive mod sync for: " + modId);
            if (modId != null) {
                for (Object dr : net.neoforged.neoforge.registries.DeferredRegister.getAllRegisters()) {
                    try {
                        String drModId = (String) dr.getClass().getMethod("getNamespace").invoke(dr);
                        if (modId.equals(drModId)) {
                            forceRegisterDeferredRegister(dr);
                        }
                    } catch (Throwable ignored) {}
                }
                for (Object reg : TRACKED_FOREIGN_REGISTERS) {
                    try {
                        dev.stolym.vyne.registry.ForceSync.forceRegisterRegistrate(reg);
                    } catch (Throwable ignored) {}
                }
                val = dev.stolym.vyne.registry.VyneGenericRegistry.findAnywhere(missingId);
            }
        }

        if (val != null) {
            Object targetRegistry = registry;
            if (targetRegistryPath != null) {
                Object resolved = resolveRegistry(cl, targetRegistryPath);
                if (resolved != null) targetRegistry = resolved;
            }

            Object rl = toResourceLocation(cl, missingId);
            if (rl != null) {
               try {
                   registerValue(targetRegistry, rl, val, cl);
                   VyneLogger.info("[Vyne-Recovery] Reactively injected and bound " + missingId + " into " + getRegistryPath(targetRegistry));
                   return 1;
               } catch (Throwable t) {
                   VyneLogger.warn("[Vyne-Recovery] Failed to inject and bind " + missingId + ": " + t);
               }
            }
        }
        return 0;
    }

    public static int snapshotLiveBuiltInIntoGeneric(ClassLoader cl, String path) {
        Object live = resolveRegistryForPath(cl, path);
        if (live == null) return 0;
        try {
            java.lang.reflect.Method keySetM = live.getClass().getMethod("keySet");
            java.util.Set<?> keys = (java.util.Set<?>) keySetM.invoke(live);
            for (Object key : keys) {
                String id = key.toString();
                Object val = getRegistryElementByLocationId(live, id, cl);
                if (val != null && dev.stolym.vyne.registry.VyneGenericRegistry.getLenient(path, id) == null) {
                    dev.stolym.vyne.registry.VyneGenericRegistry.register(path, id, val);
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    public static int syncAllTrackedGenericEntriesToLiveRegistry(ClassLoader cl) {
        for (String path : dev.stolym.vyne.registry.VyneGenericRegistry.getAllRegistryPaths()) {
            syncMissingGenericEntriesToLiveRegistry(cl, path);
        }
        return 0;
    }

    public static Object getRegistryElementByLocationId(Object registry, String idStr, ClassLoader cl) {
        try {
            Object rl = toResourceLocation(cl, idStr);
            if (rl != null) {
                for (Method m : registry.getClass().getMethods()) {
                    if (m.getName().equals("get") || m.getName().equals("method_10223")) {
                        Object converted = convertResourceLocation(rl, m.getParameterTypes()[0]);
                        if (converted != null) return m.invoke(registry, converted);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static void dropCachedRegistry(String path) {
        REGISTRY_CACHE.remove(path);
    }

    public static Object createRegistryElementResourceKeyForLiveRegistry(ClassLoader cl, Object registryKey, Object elementId) {
        if (registryKey == null || elementId == null) return null;
        Object elementLocation = elementId instanceof String s ? toResourceLocation(cl, s) : elementId;
        if (elementLocation == null) return null;
        java.util.ArrayList<Class<?>> keyClasses = new java.util.ArrayList<>(resourceKeyClasses(cl));
        Class<?> registryKeyClass = registryKey.getClass();
        if (!keyClasses.contains(registryKeyClass)) {
            keyClasses.add(0, registryKeyClass);
        }
        for (Class<?> keyClass : keyClasses) {
            for (Method m : keyClass.getMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 2) continue;
                String name = m.getName();
                if (!name.equals("create") && !name.equals("method_29184")) continue;
                Class<?>[] params = m.getParameterTypes();
                if (!params[0].isInstance(registryKey)) continue;
                Object convertedLocation = convertResourceLocation(elementLocation, params[1]);
                if (convertedLocation == null) continue;
                try {
                    m.setAccessible(true);
                    return m.invoke(null, registryKey, convertedLocation);
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    public static Object tryGetVolatileBuiltinEntryByLocationId(ClassLoader cl, String registryPath, String elementId) {
        Object registry = resolveRegistryForPath(cl, registryPath);
        if (registry != null) {
            return getRegistryElementByLocationId(registry, elementId, cl);
        }
        return null;
    }
}
