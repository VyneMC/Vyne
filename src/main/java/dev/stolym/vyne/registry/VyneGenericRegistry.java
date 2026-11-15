/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.registry;

import dev.stolym.vyne.util.ReflectionHelper;
import dev.stolym.vyne.util.VyneLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class VyneGenericRegistry {
    private static final Map<String, Map<String, Object>> REGISTRIES = new ConcurrentHashMap<>();

    public static void register(String registryPath, Object id, Object value) {
        String key = id != null ? id.toString() : "null";
        if (shouldRejectAirBlockPlaceholder(registryPath, key, value)) {
            return;
        }
        Map<String, Object> bucket = REGISTRIES.computeIfAbsent(registryPath, k -> new ConcurrentHashMap<>());
        Object prev = bucket.put(key, value);
        if (prev == null || !Objects.equals(prev, value)) {
            VyneLogger.debug("[VyneGenericRegistry] Registered in " + registryPath + ": " + key + " -> " + value);
        }
    }

    public static boolean rememberLiveEntry(String registryPath, String id, Object value) {
        if (registryPath == null || id == null || value == null) {
            return false;
        }
        if (shouldRejectAirBlockPlaceholder(registryPath, id, value)) {
            return false;
        }
        Map<String, Object> bucket = REGISTRIES.computeIfAbsent(registryPath, k -> new ConcurrentHashMap<>());
        return bucket.putIfAbsent(id, value) == null;
    }

    private static boolean shouldRejectAirBlockPlaceholder(String registryPath, String key, Object value) {
        if (value == null) {
            return false;
        }
        String norm = ReflectionHelper.normalizeRegistryKeyLocationString(registryPath);
        if (!"minecraft:block".equals(norm)) {
            return false;
        }
        if (key.startsWith("minecraft:")) {
            return false;
        }
        ClassLoader cl = value.getClass().getClassLoader();
        if (cl == null) {
            cl = Thread.currentThread().getContextClassLoader();
        }
        if (RegistryMirrorUtil.isVanillaAirBlock(value, cl)) {
            return true;
        }
        String bid = RegistryMirrorUtil.tryGetBlockIdString(value, cl);
        return "minecraft:air".equals(bid) || "minecraft:cave_air".equals(bid) || "minecraft:void_air".equals(bid);
    }

    public static Object get(String registryPath, Object id) {
        Map<String, Object> registry = REGISTRIES.get(registryPath);
        return registry != null ? registry.get(id != null ? id.toString() : "null") : null;
    }

    public static Object getLenient(String registryPath, Object id) {
        if (registryPath == null || id == null) {
            return null;
        }
        String idStr = id.toString();
        Object direct = get(registryPath, idStr);
        if (direct != null) {
            return direct;
        }
        String normPath = ReflectionHelper.normalizeRegistryKeyLocationString(registryPath);
        if (!normPath.equals(registryPath)) {
            direct = get(normPath, idStr);
            if (direct != null) {
                return direct;
            }
        }
        for (String path : new String[] { registryPath, normPath }) {
            Map<String, Object> reg = REGISTRIES.get(path);
            if (reg == null) {
                continue;
            }
            for (Map.Entry<String, Object> e : reg.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(idStr)) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    public static Map<String, Object> getRegistry(String registryPath) {
        return REGISTRIES.getOrDefault(registryPath, Collections.emptyMap());
    }

    public static Set<String> getKeys(String registryPath) {
        Map<String, Object> registry = REGISTRIES.get(registryPath);
        return registry != null ? registry.keySet() : Collections.emptySet();
    }

    public static Collection<Object> getValues(String registryPath) {
        Map<String, Object> registry = REGISTRIES.get(registryPath);
        return registry != null ? registry.values() : Collections.emptyList();
    }

    public static boolean containsKey(String registryPath, Object id) {
        Map<String, Object> registry = REGISTRIES.get(registryPath);
        return registry != null && registry.containsKey(id != null ? id.toString() : "null");
    }

    public static int size(String registryPath) {
        Map<String, Object> registry = REGISTRIES.get(registryPath);
        return registry != null ? registry.size() : 0;
    }

    public static Object findAnywhere(String id) {
        if (id == null) return null;
        for (Map<String, Object> bucket : REGISTRIES.values()) {
            Object val = bucket.get(id);
            if (val != null) return val;
        }
        for (Map<String, Object> bucket : REGISTRIES.values()) {
            for (Map.Entry<String, Object> e : bucket.entrySet()) {
                if (id.equalsIgnoreCase(e.getKey())) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    public static Set<String> getAllRegistryPaths() {
        return pathsWithEntries();
    }

    public static Set<String> pathsWithEntries() {
        Set<String> out = new HashSet<>();
        for (Map.Entry<String, Map<String, Object>> e : REGISTRIES.entrySet()) {
            Map<String, Object> m = e.getValue();
            if (m != null && !m.isEmpty()) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    public static void dump() {
        VyneLogger.debug("[VyneGenericRegistry] Registry dump:");
        REGISTRIES.forEach((path, registry) -> {
            VyneLogger.debug("  " + path + " (" + registry.size() + " entries):");
            registry.forEach((id, value) -> VyneLogger.debug("    " + id + " -> " + value));
        });
    }
}
