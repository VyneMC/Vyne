/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl;

import dev.stolym.vyne.registry.RegistryMirrorUtil;
import dev.stolym.vyne.registry.VyneGenericRegistry;
import dev.stolym.vyne.registry.VyneRegistryInjector;
import dev.stolym.vyne.util.NullMemberWrapper;
import dev.stolym.vyne.util.VyneLogger;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class VCLFieldPopulator {
    private record PendingField(String className, String fieldName,
                                String registryName, String modId, String sourceField) {}
    private static final java.util.List<PendingField> PENDING =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void populate(Class<?> cls) {
        String name = cls.getName();
        if (PERMANENT_SKIP.contains(name)) return;
        if (shouldSkipForCurrentEnvironment(name)) return;
        String pkg = "";
        int lastDot = name.lastIndexOf('.');
        if (lastDot != -1) pkg = name.substring(0, lastDot);

        if (isVyneInternalPackage(pkg)) return;

        String modId = resolveModIdFromPkg(pkg);
        if (modId == null) return;

        VyneLogger.debug("[VCLPopulator] Processing " + name + " (mod: " + modId + ")");
        try {
            for (Field field : cls.getDeclaredFields()) {
                try {
                    if (!Modifier.isStatic(field.getModifiers())) continue;

                    Class<?> type = field.getType();
                    String typeName = type.getName();
                    if (!isRegistryLikeType(typeName)) continue;

                    VyneFieldRegistry.lazyPopulate(cls, field);

                    field.setAccessible(true);
                    Object val = field.get(null);
                    if (val != null) continue;

                    VyneLogger.debug("[VCLPopulator] Field " + name + "." + field.getName()
                        + " is null — attempting smart wrap.");

                    Object wrapped = null;
                    if (type.isInterface()) {
                        wrapped = NullMemberWrapper.wrap(null, type);
                    } else if (typeName.contains("Block") || typeName.contains("class_2248")) {
                        try {
                            wrapped = tryRegistryGetWithCandidates(
                                "minecraft:block", modId, field.getName(), cls.getClassLoader(), type);
                            if (wrapped == null) {
                                if ("minecraft".equals(modId)) {
                                    Class<?> blocks = Class.forName("net.minecraft.class_2246", true, cls.getClassLoader());
                                    wrapped = blocks.getField("field_10124").get(null); 
                                    VyneLogger.debug("[VCLPopulator] Using AIR fallback for block: " + field.getName());
                                } else if (isNullSentinel(field.getName())) {
                                    Class<?> blocks = Class.forName("net.minecraft.class_2246", true, cls.getClassLoader());
                                    wrapped = blocks.getField("field_10124").get(null); 
                                    VyneLogger.debug("[VCLPopulator] Using AIR sentinel fallback for block: " + field.getName());
                                } else {
                                    queuePending(cls, field, "minecraft:block", modId);
                                    VyneLogger.debug("[VCLPopulator] No block registry entry yet for " + modId + ":" + field.getName().toLowerCase(java.util.Locale.ROOT));
                                }
                            }
                        } catch (Throwable ignored) {}
                    } else if (typeName.contains("Item") || typeName.contains("class_1792")) {
                        try {
                            wrapped = tryRegistryGetWithCandidates(
                                "minecraft:item", modId, field.getName(), cls.getClassLoader(), type);
                            if (wrapped == null) {
                                if ("minecraft".equals(modId)) {
                                    Class<?> items = Class.forName("net.minecraft.class_1802", true, cls.getClassLoader());
                                    wrapped = items.getField("field_8162").get(null); 
                                    VyneLogger.debug("[VCLPopulator] Using AIR fallback for item: " + field.getName());
                                } else if (isNullSentinel(field.getName())) {
                                    Class<?> items = Class.forName("net.minecraft.class_1802", true, cls.getClassLoader());
                                    wrapped = items.getField("field_8162").get(null); 
                                    VyneLogger.debug("[VCLPopulator] Using AIR sentinel fallback for item: " + field.getName());
                                } else {
                                    queuePending(cls, field, "minecraft:item", modId);
                                    VyneLogger.debug("[VCLPopulator] No item registry entry yet for " + modId + ":" + field.getName().toLowerCase(java.util.Locale.ROOT));
                                }
                            }
                        } catch (Throwable ignored) {}
                    } else {
                        String deducedReg = deduceRegistryName(type);
                        queuePending(cls, field, deducedReg, modId);
                        VyneLogger.debug("[VCLPopulator] Queued pending generic entry for " + modId + ":" + field.getName() + " in registry: " + deducedReg);
                    }

                    if (wrapped != null) {
                        try {
                            Field modifiersField = Field.class.getDeclaredField("modifiers");
                            modifiersField.setAccessible(true);
                            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
                        } catch (Throwable ignored) {}

                        field.set(null, wrapped);
                        VyneLogger.debug("[VCLPopulator] Successfully populated field: " + name + "." + field.getName());
                    }
                } catch (Throwable t) {
                    VyneLogger.error("Failed to populate " + name + "." + field.getName(), t);
                }
            }
        } catch (LinkageError e) {
            if (e instanceof ClassCircularityError) {
                VyneLogger.debug("[VCLPopulator] Circularity detected for " + name + " — will retry later.");
                queueClassRetry(name);
            } else if (e instanceof VerifyError) {
                VyneLogger.warn("[VCLPopulator] Bytecode verification failed for " + name
                    + " (VerifyError) — retrying later. Raw verifier message: " + e);
                queueClassRetry(name);
            } else {
                VyneLogger.warn("[VCLPopulator] Skipping population for " + name + " ("
                    + e.getClass().getSimpleName() + "): " + e.getMessage() + " — will retry later.");
                queueClassRetry(name);
            }
        }
    }

    private static final java.util.Set<String> PERMANENT_SKIP =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final java.util.Set<String> RETRY_CLASSES =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void queueClassRetry(String className) {
        RETRY_CLASSES.add(className);
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, String> PKG_TO_MODID =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean shouldSkipForCurrentEnvironment(String className) {
        if (className == null || className.isEmpty()) return false;
        if (!isDedicatedServerEnvironment()) return false;
        return className.contains(".compat.jei.")
            || className.contains(".compat.emi.")
            || className.contains(".compat.computercraft.")
            || className.contains(".data.recipe.")
            || className.contains(".client.")
            || className.contains(".gui.")
            || className.contains(".screen.");
    }

    private static boolean isDedicatedServerEnvironment() {
        try {
            Object inst = net.fabricmc.loader.api.FabricLoader.getInstance();
            Object env = inst.getClass().getMethod("getEnvironmentType").invoke(inst);
            return env != null && "SERVER".equals(String.valueOf(env));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isVyneInternalPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        return pkg.startsWith("dev.stolym.vyne")
            || pkg.startsWith("net.neoforged.")
            || pkg.startsWith("net.minecraftforge.")
            || pkg.startsWith("net.fabricmc.")
            || pkg.startsWith("net.minecraft.")
            || pkg.equals("net.minecraft")
            || pkg.startsWith("com.llamalad7.mixinextras");
    }

    public static void registerModPackage(String rootPkg, String modId) {
        PKG_TO_MODID.put(rootPkg, modId);
    }

    private static String resolveModIdFromPkg(String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;

        String bestMatch = null;
        String bestModId = null;
        for (java.util.Map.Entry<String, String> e : PKG_TO_MODID.entrySet()) {
            if (pkg.equals(e.getKey()) || pkg.startsWith(e.getKey() + ".")) {
                if (bestMatch == null || e.getKey().length() > bestMatch.length()) {
                    bestMatch = e.getKey();
                    bestModId = e.getValue();
                }
            }
        }
        if (bestModId != null) return bestModId;

        String[] parts = pkg.split("\\.");
        if (parts.length >= 2) {
            if (parts[0].equals("com") || parts[0].equals("net") || parts[0].equals("org") || parts[0].equals("dev") || parts[0].equals("io")) {
                if (parts.length >= 3 && (parts[1].equals("github") || parts[1].equals("gitlab"))) {
                     return parts[2];
                }
                return parts[1];
            }
            return parts[0];
        }
        return pkg;
    }

    private static Object tryRegistryGet(String registryName, String location, ClassLoader cl) {
        try {
            String rlName = dev.stolym.vyne.mappings.MappingRegistry.isReady()
                ? dev.stolym.vyne.mappings.MappingRegistry.get()
                    .mapClass("net/minecraft/resources/ResourceLocation")
                    .replace('/', '.')
                : "net.minecraft.class_2960";
            Class<?> rlClass = Class.forName(rlName, true, cl);
            Object rl = buildResourceLocation(rlClass, location);
            if (rl == null) {
                return null;
            }
            Object reg = VyneRegistryInjector.resolveRegistryForPath(cl, registryName);
            if (reg == null) {
                return null;
            }
            Object result = invokeRegistryGet(reg, rl);
            if (result != null) {
                if ("minecraft:block".equals(registryName)
                        && RegistryMirrorUtil.isBuiltInBlockRegistryDefaultAirForModId(
                                location, result, cl)) {
                    return null;
                }
                VyneGenericRegistry.rememberLiveEntry(registryName, location, result);
                VyneLogger.debug("[VCLPopulator] Found " + location + " in registry " + registryName);
            }
            return result;
        } catch (Throwable ignored) {}
        return null;
    }

    public static int retryPending(ClassLoader cl) {
        int resolved = 0;

        for (String className : new java.util.ArrayList<>(RETRY_CLASSES)) {
            if (PERMANENT_SKIP.contains(className)) {
                RETRY_CLASSES.remove(className);
                continue;
            }
            try {
                Class<?> cls = Class.forName(className, false, cl);
                RETRY_CLASSES.remove(className);
                populate(cls);
                resolved++;
            } catch (Throwable ignored) {}
        }

        java.util.List<PendingField> snapshot = new java.util.ArrayList<>(PENDING);
        for (PendingField p : snapshot) {
            try {
                Class<?> cls = Class.forName(p.className(), false, cl);
                Field f = cls.getDeclaredField(p.fieldName());
                f.setAccessible(true);
                if (f.get(null) != null) {
                    PENDING.remove(p);
                    continue;
                }
                Object value = tryRegistryGetWithCandidates(
                    p.registryName(), p.modId(), p.sourceField(), cl, f.getType());
                if (value == null) continue;
                try {
                    Field modifiersField = Field.class.getDeclaredField("modifiers");
                    modifiersField.setAccessible(true);
                    modifiersField.setInt(f, f.getModifiers() & ~Modifier.FINAL);
                } catch (Throwable ignored) {}
                f.set(null, value);
                PENDING.remove(p);
                resolved++;
            } catch (Throwable ignored) {}
        }
        if (resolved > 0) {
            VyneLogger.debug("[VCLPopulator] Resolved pending static fields: " + resolved);
        }
        return resolved;
    }

    private static void queuePending(Class<?> cls, Field field, String registryName, String modId) {
        PendingField p = new PendingField(cls.getName(), field.getName(), registryName, modId, field.getName());
        if (!PENDING.contains(p)) {
            PENDING.add(p);
        }
    }

    private static Object buildResourceLocation(Class<?> rlClass, String id) {
        try {
            for (java.lang.reflect.Method m : rlClass.getMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != String.class) continue;
                if (m.getName().equals("parse") || m.getName().equals("of") || m.getName().contains("method_")) {
                    try { return m.invoke(null, id); } catch (Throwable ignored) {}
                }
            }
            for (java.lang.reflect.Constructor<?> c : rlClass.getConstructors()) {
                if (c.getParameterCount() == 1 && c.getParameterTypes()[0] == String.class) {
                    return c.newInstance(id);
                }
                if (c.getParameterCount() == 2
                        && c.getParameterTypes()[0] == String.class
                        && c.getParameterTypes()[1] == String.class) {
                    String ns = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft";
                    String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
                    return c.newInstance(ns, path);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object invokeRegistryGet(Object registry, Object rl) {
        for (java.lang.reflect.Method m : registry.getClass().getMethods()) {
            if (m.getParameterCount() != 1) continue;
            if (m.getReturnType().isPrimitive()) continue;
            if (m.getName().equals("get") || m.getName().equals("getValue")
                    || m.getName().contains("method_")) {
                Class<?> pt = m.getParameterTypes()[0];
                if (pt.isInstance(rl) || pt.getName().contains("ResourceLocation")
                        || pt.getName().contains("class_2960")) {
                    try { return m.invoke(registry, rl); } catch (Throwable ignored) {}
                }
            }
        }
        return null;
    }

    private static Object tryRegistryGetWithCandidates(String registryName, String modId,
                                                       String fieldName, ClassLoader cl,
                                                       Class<?> expectedType) {
        for (String candidate : registryPathCandidates(fieldName)) {
            Object found = tryRegistryGet(registryName, modId + ":" + candidate, cl);
            if (found != null && expectedType != null && !expectedType.isInstance(found)) {
                found = null;
            }
            if (found == null) {
                found = VyneGenericRegistry.get(registryName, modId + ":" + candidate);
                if (found != null && expectedType != null && !expectedType.isInstance(found)) {
                    found = null;
                }
            }
            if (found != null) return found;
        }
        return null;
    }

    private static java.util.List<String> registryPathCandidates(String fieldName) {
        String snake = fieldName.toLowerCase(java.util.Locale.ROOT);
        String compact = snake.replace("_", "");
        String dashed = snake.replace('_', '-');
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        set.add(snake);
        set.add(compact);
        set.add(dashed);
        return new java.util.ArrayList<>(set);
    }

    private static boolean isRegistryLikeType(String typeName) {
        return typeName.endsWith(".Block")
            || typeName.endsWith(".Item")
            || typeName.endsWith(".Fluid")
            || typeName.endsWith(".FluidType")
            || typeName.endsWith(".EntityType")
            || typeName.endsWith(".BlockEntityType")
            || typeName.endsWith(".SoundEvent")
            || typeName.endsWith(".MobEffect")
            || typeName.endsWith(".Potion")
            || typeName.endsWith(".Enchantment")
            || typeName.endsWith(".ConfiguredFeature")
            || typeName.endsWith(".PlacedFeature")
            || typeName.endsWith(".Biome")
            || typeName.endsWith(".ParticleType")
            || typeName.endsWith(".MenuType")
            || typeName.endsWith(".CreativeModeTab")
            || typeName.endsWith(".class_2248")
            || typeName.endsWith(".class_1792")
            || typeName.endsWith(".class_3611")
            || typeName.endsWith(".BlockEntry")
            || typeName.endsWith(".ItemEntry")
            || typeName.endsWith(".EntityEntry")
            || typeName.endsWith(".FluidEntry")
            || typeName.endsWith(".MenuEntry");
    }

    private static String deduceRegistryName(Class<?> type) {
        if (type == null) return "minecraft:unknown";
        String name = type.getSimpleName();
        if (name.equals("class_2248")) return "minecraft:block";
        if (name.equals("class_1792")) return "minecraft:item";
        if (name.equals("class_3611")) return "minecraft:fluid";

        if (name.endsWith("Entry")) {
            name = name.substring(0, name.length() - 5);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return "minecraft:" + sb.toString();
    }

    private static boolean isNullSentinel(String fieldName) {
        String up = fieldName.toUpperCase(java.util.Locale.ROOT);
        return up.equals("NULL_BLOCK")
            || up.equals("NULL_ITEM")
            || up.equals("LIQUID_NULL")
            || up.startsWith("NULL_");
    }

    public static void onLiveRegistryEntryInjected(String registryPathNorm, String entryId,
            Object value, ClassLoader cl) {
        if (registryPathNorm == null || entryId == null || value == null || cl == null) {
            return;
        }
        String norm = dev.stolym.vyne.util.ReflectionHelper.normalizeRegistryKeyLocationString(
            registryPathNorm);
        int colon = entryId.indexOf(':');
        if (colon <= 0) {
            return;
        }
        String modId = entryId.substring(0, colon).toLowerCase(java.util.Locale.ROOT);
        java.util.ArrayList<PendingField> resolved = new java.util.ArrayList<>();
        for (PendingField p : PENDING) {
            if (p.registryName() != null && !norm.equals(p.registryName())) {
                continue;
            }
            if (!modId.equalsIgnoreCase(p.modId())) {
                continue;
            }
            boolean idMatches = false;
            for (String cand : registryPathCandidates(p.sourceField())) {
                if (entryId.equalsIgnoreCase(p.modId() + ":" + cand)) {
                    idMatches = true;
                    break;
                }
            }
            if (!idMatches) {
                continue;
            }
            if (tryAssignPendingField(p, entryId, value, cl)) {
                resolved.add(p);
            }
        }
        PENDING.removeAll(resolved);
    }

    private static boolean tryAssignPendingField(PendingField p, String entryId, Object value,
            ClassLoader cl) {
        try {
            Class<?> cls = Class.forName(p.className(), false, cl);
            Field f = cls.getDeclaredField(p.fieldName());
            f.setAccessible(true);
            if (f.get(null) != null) {
                return true;
            }
            if (!isAssignableRegistryValue(f.getType(), value)) {
                return false;
            }
            try {
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(f, f.getModifiers() & ~Modifier.FINAL);
            } catch (Throwable ignored) {
            }
            try {
                f.set(null, value);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            VyneLogger.debug("[VCLPopulator] Bound pending static " + p.className() + "#" + p.fieldName()
                + " from live inject " + p.registryName() + " id=" + (entryId != null ? entryId : ""));
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isAssignableRegistryValue(Class<?> fieldType, Object value) {
        if (fieldType == null || value == null) {
            return false;
        }
        if (fieldType.isInstance(value)) {
            return true;
        }
        String ft = fieldType.getName();
        String vt = value.getClass().getName();

        String ftSimple = ft.substring(ft.lastIndexOf('.') + 1);
        String vtSimple = vt.substring(vt.lastIndexOf('.') + 1);
        if (ftSimple.equals(vtSimple)) {
            return true;
        }

        boolean fieldBlock = ft.endsWith(".Block") || ft.contains("class_2248");
        boolean valBlock = vt.endsWith(".Block") || vt.contains("class_2248");
        boolean fieldItem = ft.endsWith(".Item") || ft.contains("class_1792");
        boolean valItem = vt.endsWith(".Item") || vt.contains("class_1792");
        return (fieldBlock && valBlock) || (fieldItem && valItem);
    }
}
