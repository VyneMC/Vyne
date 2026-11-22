/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mappings;

import dev.stolym.vyne.util.VyneLogger;
import org.objectweb.asm.ClassReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.MappingTree.ClassMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodMapping;
import net.fabricmc.mappingio.tree.MappingTree.FieldMapping;
import net.fabricmc.mappingio.format.proguard.ProGuardFileReader;
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;

import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public final class MappingComposer {
    private static final ThreadLocal<ClassLoader> CLASS_BYTE_PROBE_LOADER =
        new ThreadLocal<>();

    public static void pushClassByteProbeLoader(ClassLoader loader) {
        CLASS_BYTE_PROBE_LOADER.set(loader);
    }

    public static void popClassByteProbeLoader() {
        CLASS_BYTE_PROBE_LOADER.remove();
    }

    public static ClassLoader peekClassByteProbeLoader() {
        return CLASS_BYTE_PROBE_LOADER.get();
    }

    private static final byte[] PROBE_NOT_FOUND = new byte[0];

    private static final java.util.concurrent.ConcurrentHashMap<String, byte[]> PROBE_BYTES_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    public static byte[] readIntermediaryClassBytesProbe(String interInternal) {
        if (interInternal == null) {
            return null;
        }
        byte[] cached = PROBE_BYTES_CACHE.get(interInternal);
        if (cached != null) {
            return cached == PROBE_NOT_FOUND ? null : cached;
        }
        byte[] direct = readClassBytesExactProbe(interInternal);
        if (direct != null) {
            PROBE_BYTES_CACHE.put(interInternal, direct);
            return direct;
        }
        if (interInternal.startsWith("net/minecraftforge/")) {
            String rewritten = interInternal.replace("net/minecraftforge/",
                "net/neoforged/neoforge/");
            byte[] rewrittenResult = readClassBytesExactProbe(rewritten);
            byte[] toCache = rewrittenResult != null ? rewrittenResult : PROBE_NOT_FOUND;
            PROBE_BYTES_CACHE.put(interInternal, toCache);
            return rewrittenResult;
        }
        if (dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
            PROBE_BYTES_CACHE.put(interInternal, PROBE_NOT_FOUND);
        }
        return null;
    }

    public static void invalidateCache() {
        PROBE_BYTES_CACHE.clear();
    }

    private static volatile java.util.zip.ZipFile CLIENT_JAR_PROBE;

    private static volatile Map<String, String> BUILT_INTER_TO_OFFICIAL;

    private static volatile Map<String, String> BUILT_OFFICIAL_TO_INTER;

    public static Map<String, String> getBuiltInterToOfficialMap() {
        return BUILT_INTER_TO_OFFICIAL;
    }

    public static void setClientJarProbe(java.nio.file.Path clientJar) {
        Map<String, String> i2o = BUILT_INTER_TO_OFFICIAL;
        if (i2o == null) return;
        try {
            CLIENT_JAR_PROBE = new java.util.zip.ZipFile(clientJar.toFile());
        } catch (Throwable ignored) {}
    }

    private static byte[] readClassBytesExactProbe(String interInternal) {
        String path = interInternal + ".class";
        for (ClassLoader cl : candidateResourceClassLoadersProbe()) {
            if (cl == null) {
                continue;
            }
            try (java.io.InputStream in = cl.getResourceAsStream(path)) {
                if (in == null) {
                    continue;
                }
                return in.readAllBytes();
            } catch (Throwable ignored) {
            }
        }
        return readFromClientJar(interInternal);
    }

    private static byte[] readFromClientJar(String interInternal) {
        java.util.zip.ZipFile jar = CLIENT_JAR_PROBE;
        Map<String, String> i2o = BUILT_INTER_TO_OFFICIAL;
        if (jar == null || i2o == null) return null;
        String official = i2o.get(interInternal);
        if (official == null) return null;
        try {
            java.util.zip.ZipEntry entry = jar.getEntry(official + ".class");
            if (entry == null) return null;
            try (java.io.InputStream is = jar.getInputStream(entry)) {
                byte[] raw = is.readAllBytes();
                Map<String, String> o2i = BUILT_OFFICIAL_TO_INTER;
                if (o2i != null && !o2i.isEmpty()) {
                    raw = OfficialClientJarHeaderRemapper
                        .remap(raw, interInternal, o2i);
                }
                return raw;
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ClassLoader[] candidateResourceClassLoadersProbe() {
        ClassLoader probe = CLASS_BYTE_PROBE_LOADER.get();
        ClassLoader knot = dev.stolym.vyne.core.GameLoader.get();
        return new ClassLoader[] {
            probe,
            knot,
            Thread.currentThread().getContextClassLoader(),
            MappingComposer.class.getClassLoader(),
            ClassLoader.getSystemClassLoader(),
        };
    }

    public record ComposedMappings(
        Map<String, String> classMap,
        Map<String, Map<String, String>> methodMap,
        Map<String, Map<String, String>> fieldMap,
        Map<String, String> reverseClassMap,
        Map<String, Map<String, String>> reverseMethodMap
    ) {
        private static String normalizeClassMapKey(String key) {
            if (key == null || key.isEmpty()) {
                return key;
            }
            String s = key.trim();
            if (s.length() >= 2 && s.charAt(0) == 'L' && s.charAt(1) != '[') {
                if (s.endsWith(";")) {
                    s = s.substring(1, s.length() - 1);
                } else {
                    s = s.substring(1);
                }
            }
            return s.indexOf('.') >= 0 ? s.replace('.', '/') : s;
        }

        public String mapClass(String internalName) {
            String in = normalizeClassMapKey(internalName);
            if (in == null) {
                return null;
            }
            if (in.contains("class_") || in.contains("method_") || in.contains("field_")) {
                return in;
            }
            if (in.equals("net/minecraft/resources/ResourceKey")) return "net/minecraft/class_5321";
            if (in.equals("net/minecraft/resources/ResourceLocation")) return "net/minecraft/class_2960";
            if (in.equals("net/minecraft/world/item/CreativeModeTab")) return "net/minecraft/class_1761";
            if ("net/minecraft/world/item/CreativeModeTab$Builder".equals(in)) {
                return "net/minecraft/class_1761$class_7913";
            }
            if ("net/minecraft/world/item/CreativeModeTab$Row".equals(in)) {
                return "net/minecraft/class_1761$class_7915";
            }
            return classMap.getOrDefault(in, in);
        }

        public String mapMethod(String mojClass, String mojMethod, String mojDesc) {
            String[] cached = MOD_CLASS_HIERARCHY.get(mojClass);
            if (cached == null) {
                cached = discoverHierarchyFromBytecode(mojClass);
            }
            if (cached != null && cached.length > 0) {
                String[] ifaces = cached.length > 1
                    ? java.util.Arrays.copyOfRange(cached, 1, cached.length)
                    : null;
                return mapMethod(mojClass, cached[0], ifaces, mojMethod, mojDesc);
            }
            if (mojClass != null && mojClass.startsWith("net/minecraft/")) {
                return mapMethod(mojClass, mojClass, null, mojMethod, mojDesc);
            }
            return mapMethod(mojClass, null, null, mojMethod, mojDesc);
        }

        private String[] discoverHierarchyFromBytecode(String mojClass) {
            if (mojClass == null || mojClass.startsWith("[")) return null;
            String lookup = classMap.getOrDefault(mojClass, mojClass);
            byte[] raw = readIntermediaryClassBytes(lookup);
            if (raw == null) {
                MOD_CLASS_HIERARCHY.put(mojClass, HIERARCHY_NONE);
                return null;
            }
            try {
                ClassReader cr = new ClassReader(raw);
                String interSuper = cr.getSuperName();
                String[] interIfaces = cr.getInterfaces();
                String mojSuper = interSuper == null
                    ? null
                    : reverseClassMap.getOrDefault(interSuper, interSuper);
                String[] mojIfaces = new String[interIfaces == null ? 0 : interIfaces.length];
                for (int i = 0; i < mojIfaces.length; i++) {
                    mojIfaces[i] = reverseClassMap.getOrDefault(interIfaces[i], interIfaces[i]);
                }
                String[] arr = new String[1 + mojIfaces.length];
                arr[0] = mojSuper;
                System.arraycopy(mojIfaces, 0, arr, 1, mojIfaces.length);
                MOD_CLASS_HIERARCHY.put(mojClass, arr);
                return arr;
            } catch (Throwable ignored) {
                MOD_CLASS_HIERARCHY.put(mojClass, HIERARCHY_NONE);
                return null;
            }
        }

        private static final String[] HIERARCHY_NONE = new String[0];

        private static final java.util.concurrent.ConcurrentHashMap<String, String[]> MOD_CLASS_HIERARCHY =
            new java.util.concurrent.ConcurrentHashMap<>();

        public static void recordClassHierarchy(String mojClass, String mojSuper,
                                                String[] mojInterfaces) {
            if (mojClass == null) return;
            int extra = (mojInterfaces != null) ? mojInterfaces.length : 0;
            String[] arr = new String[1 + extra];
            arr[0] = mojSuper;
            if (extra > 0) System.arraycopy(mojInterfaces, 0, arr, 1, extra);
            MOD_CLASS_HIERARCHY.put(mojClass, arr);
        }

        public String mapMethod(String mojClass, String mojSuper, String[] mojInterfaces,
                                String mojMethod, String mojDesc) {
            String exactKey = mojMethod + (mojDesc != null ? mojDesc : "");
            String mojOwnerKey = reverseClassMap.getOrDefault(mojClass, mojClass);
            Map<String, String> methods = methodMap.get(mojOwnerKey);
            if (methods != null && methods.containsKey(exactKey)) {
                return methods.get(exactKey);
            }
            methods = methodMap.get(mojClass);
            if (methods != null && methods.containsKey(exactKey)) {
                return methods.get(exactKey);
            }

            String ber = tryMapBlockEntityRendererOverride(mojMethod, mojDesc);
            if (ber != null) {
                return ber;
            }

            if (mojDesc == null || looksLikeJdkMethod(mojMethod)) {
                return mojMethod;
            }

            boolean[] hierarchyComplete = {false};
            if (mojSuper != null || mojInterfaces != null) {
                hierarchyComplete[0] = true;
                String viaOwner = resolveViaOwnerHierarchy(mojSuper, mojInterfaces,
                        mojMethod, mojDesc, hierarchyComplete);
                if (viaOwner != null) {
                    logInheritedRemapOnce(mojClass, mojMethod, mojDesc, viaOwner,
                            "owner-hierarchy");
                    return viaOwner;
                }
            }

            boolean ownerIsMc = mojClass != null && mojClass.startsWith("net/minecraft/");
            if (!ownerIsMc && !descriptorReferencesMinecraft(mojDesc)) {
                return mojMethod;
            }

            if (hierarchyComplete[0]) {
                return mojMethod;
            }

            String inherited = resolveInheritedIntermediary(mojMethod, mojDesc);
            if (inherited != null) {
                logInheritedRemapOnce(mojClass, mojMethod, mojDesc, inherited,
                        "global-scan");
                return inherited;
            }

            logUnmappedSuspectOnce(mojClass, mojMethod, mojDesc);
            return mojMethod;
        }

        private static final java.util.Set<String> LOGGED_UNMAPPED_SUSPECTS =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

        private static final java.util.concurrent.ConcurrentHashMap<String, Boolean> MC_METHOD_EXISTS_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

        private boolean anyMinecraftClassHas(String mojMethod, String mojDesc) {
            String key = mojMethod + (mojDesc != null ? mojDesc : "");
            Boolean cached = MC_METHOD_EXISTS_CACHE.get(key);
            if (cached != null) return cached;
            boolean exists = false;
            for (Map.Entry<String, Map<String, String>> e : methodMap.entrySet()) {
                String owner = e.getKey();
                if (owner == null || !owner.startsWith("net/minecraft/")) continue;
                if (e.getValue().containsKey(key)) {
                    exists = true;
                    break;
                }
            }
            MC_METHOD_EXISTS_CACHE.put(key, exists);
            return exists;
        }

        private void logUnmappedSuspectOnce(String mojClass, String mojMethod,
                                                   String mojDesc) {
            if (mojClass == null
                    || mojClass.startsWith("net/minecraft/")
                    || mojClass.startsWith("java/")
                    || mojClass.startsWith("com/mojang/")
                    || mojClass.startsWith("net/fabricmc/")
                    || mojClass.startsWith("net/minecraftforge/")
                    || mojClass.startsWith("net/neoforged/")
                    || mojClass.startsWith("org/")
                    || mojClass.startsWith("it/unimi/")) {
                return;
            }

            if (mojMethod != null && (mojMethod.startsWith("method_")
                    || mojMethod.startsWith("field_") || mojMethod.startsWith("class_"))) {
                return;
            }

            if (mojMethod != null && mojMethod.indexOf('$') >= 0) {
                return;
            }

            if (mojDesc != null
                    && !anyMinecraftClassHas(mojMethod, mojDesc)) {
                return;
            }
            String key = mojClass + "#" + mojMethod + mojDesc;
            if (!LOGGED_UNMAPPED_SUSPECTS.add(key)) return;
            VyneLogger.debug("[MappingComposer] Unmapped Mojmap-shaped call: "
                + mojClass + "." + mojMethod + mojDesc
                + " — kept original name. Only relevant if the mod crashes with a"
                + " NoSuchMethodError pointing at this call.");
        }

        private String resolveViaOwnerHierarchy(String mojSuper, String[] mojInterfaces,
                                                String mojMethod, String mojDesc, boolean[] complete) {
            java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            if (mojSuper != null) queue.add(mojSuper);
            if (mojInterfaces != null) {
                for (String i : mojInterfaces) {
                    if (i != null) queue.add(i);
                }
            }
            while (!queue.isEmpty()) {
                String owner = queue.poll();
                if (owner == null || "java/lang/Object".equals(owner)) continue;
                if (!seen.add(owner)) continue;

                String direct = lookupExact(owner, mojMethod, mojDesc);
                if (direct != null) return direct;

                String[] modHier = MOD_CLASS_HIERARCHY.get(owner);
                if (modHier != null && modHier.length > 0) {
                    if (modHier[0] != null) queue.add(modHier[0]);
                    for (int i = 1; i < modHier.length; i++) {
                        if (modHier[i] != null) queue.add(modHier[i]);
                    }
                } else if (owner.startsWith("net/minecraft/")) {
                    if (!enqueueMinecraftSuperAndIfacesFromBytecode(owner, queue)) {
                        complete[0] = false;
                    }
                } else if (!owner.startsWith("java/")
                        && !owner.startsWith("jdk/")
                        && !owner.startsWith("javax/")) {
                    if (modHier != null && modHier.length == 0) {
                        complete[0] = false;
                        continue;
                    }
                    String[] inferred = discoverHierarchyFromBytecode(owner);
                    if (inferred != null && inferred.length > 0) {
                        if (inferred[0] != null) queue.add(inferred[0]);
                        for (int i = 1; i < inferred.length; i++) {
                            if (inferred[i] != null) queue.add(inferred[i]);
                        }
                    } else {
                        complete[0] = false;
                    }
                }
            }
            return null;
        }

        private boolean enqueueMinecraftSuperAndIfacesFromBytecode(String mojOwner,
                java.util.ArrayDeque<String> queue) {
            String inter = classMap.getOrDefault(mojOwner, mojOwner);
            byte[] raw = readIntermediaryClassBytes(inter);
            if (raw == null) return false;
            try {
                ClassReader cr = new ClassReader(raw);
                String interSuper = cr.getSuperName();
                if (interSuper != null && !"java/lang/Object".equals(interSuper)) {
                    String mojS = reverseClassMap.getOrDefault(interSuper, interSuper);
                    queue.add(mojS);
                }
                for (String itf : cr.getInterfaces()) {
                    if (itf != null) {
                        queue.add(reverseClassMap.getOrDefault(itf, itf));
                    }
                }
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static byte[] readIntermediaryClassBytes(String interInternal) {
            return MappingComposer.readIntermediaryClassBytesProbe(interInternal);
        }

        private String lookupExact(String mojOwner, String mojMethod, String mojDesc) {
            Map<String, String> methods = methodMap.get(mojOwner);
            if (methods == null) return null;
            String key = mojMethod + (mojDesc != null ? mojDesc : "");
            return methods.get(key);
        }

        private static final java.util.Set<String> LOGGED_INHERITED_REMAPS =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

        private static void logInheritedRemapOnce(String mojClass, String mojMethod,
                                                  String mojDesc, String inter,
                                                  String source) {
            if (mojClass == null || mojClass.startsWith("net/minecraft/")
                    || mojClass.startsWith("java/")) {
                return;
            }
            String key = source + "|" + mojClass + "#" + mojMethod + mojDesc;
            if (!LOGGED_INHERITED_REMAPS.add(key)) return;
            VyneLogger.debug("[MappingComposer] Inherited remap (" + source + "): "
                + mojClass + "." + mojMethod + mojDesc + " → " + inter);
        }

        private static final String BLOCK_ENTITY_RENDERER =
            "net/minecraft/client/renderer/blockentity/BlockEntityRenderer";

        private static final String BER_MAIN_RENDER_TAIL =
            "FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V";

        private String tryMapBlockEntityRendererOverride(String mojMethod, String mojDesc) {
            if (!"render".equals(mojMethod) || mojDesc == null) {
                return null;
            }
            if (!mojDesc.startsWith("(L") || !mojDesc.endsWith(BER_MAIN_RENDER_TAIL)) {
                return null;
            }
            int firstSemi = mojDesc.indexOf(';');
            if (firstSemi <= 2) {
                return null;
            }
            String firstType = mojDesc.substring(2, firstSemi);
            if (!looksLikeBlockEntityType(firstType)) {
                return null;
            }
            Map<String, String> ber = methodMap.get(BLOCK_ENTITY_RENDERER);
            if (ber == null) {
                return null;
            }
            String canon = null;
            for (Map.Entry<String, String> e : ber.entrySet()) {
                String k = e.getKey();
                if (!k.startsWith("render(") || !k.endsWith(BER_MAIN_RENDER_TAIL)) {
                    continue;
                }
                if (!k.contains("net/minecraft/world/level/block/entity/BlockEntity;F")) {
                    continue;
                }
                canon = e.getValue();
                break;
            }
            return canon;
        }

        private static boolean looksLikeBlockEntityType(String internal) {
            if (internal == null || internal.isEmpty()) {
                return false;
            }
            return internal.endsWith("BlockEntity")
                || internal.contains("/block/entity/")
                || internal.contains("/blockentity/");
        }

        private static boolean descriptorReferencesMinecraft(String desc) {
            return desc != null && (desc.contains("Lnet/minecraft/")
                    || desc.contains("Lcom/mojang/blaze3d/")
                    || desc.contains("Lcom/mojang/math/")
                    || desc.contains("Lcom/mojang/authlib/")
                    || desc.contains("Lcom/mojang/serialization/"));
        }

        private static boolean looksLikeJdkMethod(String name) {
            if (name == null) return true;
            return "equals".equals(name) || "hashCode".equals(name)
                    || "toString".equals(name) || "clone".equals(name)
                    || "finalize".equals(name) || "getClass".equals(name)
                    || "<init>".equals(name)                     || "<clinit>".equals(name);
        }

        private String resolveInheritedIntermediary(String mojMethod, String mojDesc) {
            String key = mojMethod + mojDesc;
            String cached = inheritedMethodCache.get(key);
            if (cached != null) return cached == INHERIT_MISS ? null : cached;

            String found = null;
            for (Map.Entry<String, Map<String, String>> e : methodMap.entrySet()) {
                String owner = e.getKey();
                if (owner == null || !owner.startsWith("net/minecraft/")) continue;
                String inter = e.getValue().get(key);
                if (inter == null) continue;
                if (found == null) {
                    found = inter;
                } else if (!found.equals(inter)) {
                    inheritedMethodCache.put(key, INHERIT_MISS);
                    return null;
                }
            }
            inheritedMethodCache.put(key, found != null ? found : INHERIT_MISS);
            return found;
        }

        private static final String INHERIT_MISS = "\0MISS\0";
        private static final java.util.concurrent.ConcurrentHashMap<String, String> inheritedMethodCache =
            new java.util.concurrent.ConcurrentHashMap<>();

        public String unmapClass(String interClass) {
            String in = normalizeClassMapKey(interClass);
            return reverseClassMap.getOrDefault(in, in);
        }

        public String unmapMethod(String intermediaryClass, String intermediaryMethod, String desc) {
            Map<String, String> methods = reverseMethodMap.get(intermediaryClass);
            if (methods == null) return intermediaryMethod;
            String exactKey = intermediaryMethod + (desc != null ? desc : "");
            if (methods.containsKey(exactKey)) return methods.get(exactKey);
            for (String key : methods.keySet()) {
                if (key.startsWith(intermediaryMethod + "(")) {
                    return methods.get(key);
                }
            }
            return intermediaryMethod;
        }

        public String mapField(String mojClass, String mojField) {
            String ownerKey = mojClass == null ? null : unmapClass(mojClass);

            Map<String, String> fields = fieldMap.get(ownerKey);
            if (fields != null) {
                String direct = fields.get(mojField);
                if (direct != null) {
                    return direct;
                }
            }
            if (ownerKey != null && !ownerKey.equals(mojClass)) {
                fields = fieldMap.get(mojClass);
                if (fields != null) {
                    String direct = fields.get(mojField);
                    if (direct != null) {
                        return direct;
                    }
                }
            }

            String[] cached = MOD_CLASS_HIERARCHY.get(ownerKey);
            if (cached == null) {
                cached = MOD_CLASS_HIERARCHY.get(mojClass);
            }
            if (cached == null) {
                cached = discoverHierarchyFromBytecode(ownerKey);
            }
            if (cached == null && ownerKey != null && !java.util.Objects.equals(ownerKey, mojClass)) {
                cached = discoverHierarchyFromBytecode(mojClass);
            }
            if (cached != null && cached.length > 0) {
                String[] ifaces = cached.length > 1
                    ? java.util.Arrays.copyOfRange(cached, 1, cached.length)
                    : null;
                String viaOwner = resolveFieldViaOwnerHierarchy(cached[0], ifaces, mojField, ownerKey);
                if (viaOwner != null) {
                    logFieldRemapOnce(mojClass, mojField, viaOwner, "hierarchy(cached)");
                    return viaOwner;
                }
            }

            if (ownerKey != null && ownerKey.startsWith("net/minecraft/")) {
                String viaSelf = resolveFieldViaOwnerHierarchy(ownerKey, null, mojField, ownerKey);
                if (viaSelf != null) {
                    logFieldRemapOnce(mojClass, mojField, viaSelf, "hierarchy(self)");
                    return viaSelf;
                }
            }
            logFieldMissOnce(mojClass, mojField);
            return mojField;
        }

        private static final java.util.Set<String> LOGGED_FIELD_REMAPS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
        private static final java.util.Set<String> LOGGED_FIELD_MISSES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

        private static void logFieldRemapOnce(String mojClass, String mojField,
                                              String inter, String via) {
            String key = mojClass + "#" + mojField;
            if (LOGGED_FIELD_REMAPS.add(key)) {
                try {
                    dev.stolym.vyne.util.VyneLogger.debug(
                        "[FieldRemap] " + mojClass + "." + mojField + " -> " + inter
                        + " [" + via + "]");
                } catch (Throwable ignored) {}
            }
        }

        private static void logFieldMissOnce(String mojClass, String mojField) {
            if (mojClass == null) return;
            if (!mojClass.startsWith("net/minecraft/")) return;
            if (mojClass.startsWith("net/minecraft/class_")) return;
            if (mojField == null || mojField.isEmpty()
                || mojField.startsWith("field_")
                || mojField.startsWith("f_")) {
                return;
            }
            String key = mojClass + "#" + mojField;
            if (LOGGED_FIELD_MISSES.add(key)) {
                try {
                    dev.stolym.vyne.util.VyneLogger.debug(
                        "[FieldRemap] MISS " + mojClass + "." + mojField
                        + " (no hierarchy match; left as Mojmap)");
                } catch (Throwable ignored) {}
            }
        }

        private static final java.util.Set<String> DO_NOT_INHERIT_VANILLA_FIELD_NAMES =
            java.util.Set.of("CODEC", "LOGGER");

        private String resolveFieldViaOwnerHierarchy(String mojSuper, String[] mojInterfaces,
                                                     String mojField, String declaringOwnerKey) {
            boolean[] complete = {true};
            boolean modOrigin = declaringOwnerKey != null
                && !declaringOwnerKey.startsWith("net/minecraft/");
            java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            if (mojSuper != null) queue.add(mojSuper);
            if (mojInterfaces != null) {
                for (String i : mojInterfaces) {
                    if (i != null) queue.add(i);
                }
            }
            while (!queue.isEmpty()) {
                String owner = queue.poll();
                if (owner == null || "java/lang/Object".equals(owner)) continue;
                if (!seen.add(owner)) continue;

                Map<String, String> fields = fieldMap.get(owner);
                if (fields != null) {
                    String hit = fields.get(mojField);
                    if (hit != null) {
                        if (modOrigin && owner.startsWith("net/minecraft/")
                                && DO_NOT_INHERIT_VANILLA_FIELD_NAMES.contains(mojField)) {
                            continue;
                        }
                        return hit;
                    }
                }

                String[] modHier = MOD_CLASS_HIERARCHY.get(owner);
                if (modHier != null && modHier.length > 0) {
                    if (modHier[0] != null) queue.add(modHier[0]);
                    for (int i = 1; i < modHier.length; i++) {
                        if (modHier[i] != null) queue.add(modHier[i]);
                    }
                } else if (owner.startsWith("net/minecraft/")) {
                    if (!enqueueMinecraftSuperAndIfacesFromBytecode(owner, queue)) {
                        complete[0] = false;
                    }
                } else if (!owner.startsWith("java/")
                        && !owner.startsWith("jdk/")
                        && !owner.startsWith("javax/")) {
                    if (modHier != null && modHier.length == 0) {
                        complete[0] = false;
                        continue;
                    }
                    String[] inferred = discoverHierarchyFromBytecode(owner);
                    if (inferred != null && inferred.length > 0) {
                        if (inferred[0] != null) queue.add(inferred[0]);
                        for (int i = 1; i < inferred.length; i++) {
                            if (inferred[i] != null) queue.add(inferred[i]);
                        }
                    } else {
                        complete[0] = false;
                    }
                }
            }
            return null;
        }
    }

    public static ComposedMappings compose(java.nio.file.Path clientTxt, java.nio.file.Path intermediaryTiny) throws Exception {
        MemoryMappingTree tree = new MemoryMappingTree();
        try (var reader = java.nio.file.Files.newBufferedReader(intermediaryTiny)) {
            Tiny2FileReader.read(reader, tree);
        }

        net.fabricmc.mappingio.MappingVisitor mojmapTarget =
            new net.fabricmc.mappingio.adapter.MappingSourceNsSwitch(tree, "official");
        try (var reader = new FileReader(clientTxt.toFile())) {
            ProGuardFileReader.read(reader, "named", "official", mojmapTarget);
        }

        int mojmapNs = tree.getNamespaceId("named");
        int officialNs = tree.getNamespaceId("official");
        int intermediaryNs = tree.getNamespaceId("intermediary");

        Map<String, String> classMap = new HashMap<>();
        Map<String, Map<String, String>> methodMap = new HashMap<>();
        Map<String, Map<String, String>> fieldMap = new HashMap<>();
        Map<String, String> interToOfficialMap = new HashMap<>();
        Map<String, String> officialToInterMap = new HashMap<>();

        for (ClassMapping cls : tree.getClasses()) {
            String mojClass = cls.getName(mojmapNs);
            String interClass = cls.getName(intermediaryNs);
            if (mojClass == null || interClass == null) continue;

            mojClass = mojClass.replace('.', '/');
            interClass = interClass.replace('.', '/');

            classMap.put(mojClass, interClass);

            String officialClass = cls.getName(officialNs);
            if (officialClass != null) {
                officialClass = officialClass.replace('.', '/');
                interToOfficialMap.put(interClass, officialClass);
                officialToInterMap.put(officialClass, interClass);
            }

            boolean debugMe = mojClass.contains("ServerLevel");

            for (MethodMapping m : cls.getMethods()) {
                String mojName = m.getName(mojmapNs);
                String mojDesc = m.getDesc(mojmapNs);
                String interName = m.getName(intermediaryNs);

                if (mojName != null && interName != null) {
                    if (mojDesc != null) mojDesc = mojDesc.replace('.', '/');
                    String key = mojName + (mojDesc != null ? mojDesc : "");
                    methodMap.computeIfAbsent(mojClass, k -> new HashMap<>())
                        .put(key, interName);

                    if (debugMe && mojName.equals("addPlayer")) {
                        VyneLogger.info("[MappingComposer] Loaded ServerLevel.addPlayer mapping: " + key + " -> " + interName);
                    }
                }
            }

            for (FieldMapping f : cls.getFields()) {
                String mojName = f.getName(mojmapNs);
                String interName = f.getName(intermediaryNs);
                if (mojName != null && interName != null) {
                    fieldMap.computeIfAbsent(mojClass, k -> new HashMap<>())
                        .put(mojName, interName);
                }
            }
        }

        applyManualClassOverrides(classMap);
        applyManualMethodOverrides(methodMap);
        applyManualFieldOverrides(fieldMap);

        VyneLogger.info("[MappingComposer] Composed " + classMap.size() + " classes with mapping-io.");
        Map<String, String> reverseClassMap = new HashMap<>();
        for (Map.Entry<String, String> e : classMap.entrySet()) {
            reverseClassMap.put(e.getValue(), e.getKey());
        }
        Map<String, Map<String, String>> reverseMethodMap = new HashMap<>();
        for (ClassMapping cls : tree.getClasses()) {
            String interClass = cls.getName(intermediaryNs);
            if (interClass == null) continue;
            for (MethodMapping m : cls.getMethods()) {
                String interName = m.getName(intermediaryNs);
                String interDesc = m.getDesc(intermediaryNs);
                String mojName = m.getName(mojmapNs);
                if (interName == null || mojName == null) continue;
                String key = interName + (interDesc != null ? interDesc : "");
                reverseMethodMap.computeIfAbsent(interClass, k -> new HashMap<>()).put(key, mojName);
            }
        }
        BUILT_INTER_TO_OFFICIAL = java.util.Collections.unmodifiableMap(interToOfficialMap);
        BUILT_OFFICIAL_TO_INTER = java.util.Collections.unmodifiableMap(officialToInterMap);

        return new ComposedMappings(
            java.util.Collections.unmodifiableMap(classMap),
            java.util.Collections.unmodifiableMap(methodMap.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                    java.util.Map.Entry::getKey,
                    e -> java.util.Collections.unmodifiableMap(e.getValue())))),
            java.util.Collections.unmodifiableMap(fieldMap.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                    java.util.Map.Entry::getKey,
                    e -> java.util.Collections.unmodifiableMap(e.getValue())))),
            java.util.Collections.unmodifiableMap(reverseClassMap),
            java.util.Collections.unmodifiableMap(reverseMethodMap.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                    java.util.Map.Entry::getKey,
                    e -> java.util.Collections.unmodifiableMap(e.getValue())))));
    }

    private static void applyManualClassOverrides(Map<String, String> classMap) {
        putClassOverride(classMap,
            "net/minecraft/world/entity/decoration/HangingEntity",
            "net/minecraft/class_9691");

        putClassOverride(classMap,
            "net/minecraft/world/item/CreativeModeTab$Builder",
            "net/minecraft/class_1761$class_7913");
        putClassOverride(classMap,
            "net/minecraft/world/item/CreativeModeTab$DisplayItemsGenerator",
            "net/minecraft/class_1761$class_7914");
        putClassOverride(classMap,
            "net/minecraft/world/item/CreativeModeTab$ItemDisplayBuilder",
            "net/minecraft/class_1761$class_7703");
        putClassOverride(classMap,
            "net/minecraft/world/item/CreativeModeTab$ItemDisplayParameters",
            "net/minecraft/class_1761$class_8128");
        putClassOverride(classMap,
            "net/minecraft/world/item/CreativeModeTab$Output",
            "net/minecraft/class_1761$class_7704");
        putClassOverride(classMap,
            "net/minecraft/world/item/CreativeModeTab$Row",
            "net/minecraft/class_1761$class_7915");
        putClassOverride(classMap,
            "net/minecraft/world/item/CreativeModeTab$TabVisibility",
            "net/minecraft/class_1761$class_7705");
        putClassOverride(classMap,
            "net/minecraft/world/item/CreativeModeTab$Type",
            "net/minecraft/class_1761$class_7916");
    }

    private static void putClassOverride(Map<String, String> classMap, String mojSlash, String interSlash) {
        classMap.put(mojSlash, interSlash);
    }

    private static void applyManualMethodOverrides(Map<String, Map<String, String>> methodMap) {
        String shape6Moj = "(DDDDDD)Lnet/minecraft/world/phys/shapes/VoxelShape;";
        String shape6Inter = "(DDDDDD)Lnet/minecraft/class_265;";
        for (String shape6 : new String[] {shape6Moj, shape6Inter}) {
            putMethodOverride(methodMap, "net/minecraft/world/level/block/Block",
                "createCuboidShape" + shape6, "method_9541");
            putMethodOverride(methodMap, "net/minecraft/world/level/block/Block",
                "box" + shape6, "method_9541");
        }
    }

    private static void putMethodOverride(Map<String, Map<String, String>> methodMap,
            String slashClass, String mojKey, String interName) {
        methodMap.computeIfAbsent(slashClass, k -> new HashMap<>()).put(mojKey, interName);
    }

    private static void applyManualFieldOverrides(Map<String, Map<String, String>> fieldMap) {
        putFieldOverride(fieldMap, "net/minecraft/world/level/block/Block",
            "stateDefinition", "field_10647");
        putFieldOverride(fieldMap, "net/minecraft/world/level/material/Fluid",
            "stateDefinition", "field_15905");

        putFieldOverride(fieldMap, "net/minecraft/world/level/block/LeavesBlock",
            "DISTANCE", "field_11199");
        putFieldOverride(fieldMap, "net/minecraft/world/level/block/LeavesBlock",
            "PERSISTENT", "field_11200");
        putFieldOverride(fieldMap, "net/minecraft/world/level/block/LeavesBlock",
            "WATERLOGGED", "field_38227");

        putFieldOverride(fieldMap,
            "net/minecraft/world/level/block/AbstractPlantStemBlock",
            "AGE", "field_22509");

        putFieldOverride(fieldMap,
            "net/minecraft/world/level/block/GrowingPlantHeadBlock",
            "AGE", "field_22509");
    }

    private static void putFieldOverride(Map<String, Map<String, String>> fieldMap,
            String slashClass, String mojName, String interName) {
        fieldMap.computeIfAbsent(slashClass, k -> new HashMap<>()).put(mojName, interName);
    }
}
