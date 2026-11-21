/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.world;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.core.VyneLauncher;
import dev.stolym.vyne.util.VyneLogger;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RegistryHealthPolicy {
    private static final Set<String> SESSION_DATAPACK_QUARANTINE = ConcurrentHashMap.newKeySet();

    private RegistryHealthPolicy() {}

    public static void sessionQuarantineModDataPacks(Collection<String> modIds) {
        if (modIds == null || modIds.isEmpty()) {
            return;
        }
        for (String id : modIds) {
            if (id != null && !id.isBlank()) {
                SESSION_DATAPACK_QUARANTINE.add(id.trim().toLowerCase(Locale.ROOT));
            }
        }
    }

    public static void clearSessionQuarantineForTests() {
        SESSION_DATAPACK_QUARANTINE.clear();
    }

    public static void loadPersistedFailuresIntoSession() {
    }

    public static void recordSessionSuppression(String modId) {
    }

    public static boolean shouldDropPack(String packId) {
        if (packId == null || packId.isEmpty()) {
            return false;
        }
        for (String modId : VyneLauncher.getHardDisabledModIds()) {
            if (packMatchesMod(packId, modId)) {
                return true;
            }
        }
        for (String q : SESSION_DATAPACK_QUARANTINE) {
            if (packMatchesMod(packId, q)) {
                return true;
            }
        }
        return false;
    }

    public static void stripSuppressedPacks(Object packRepository) {
        try {
            Class<?> c = packRepository.getClass();
            for (Field f : c.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                if (!stringKeyedMap(f)) {
                    continue;
                }
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, ?> map = (Map<String, ?>) f.get(packRepository);
                if (map == null || map.isEmpty()) {
                    continue;
                }
                boolean anyDrop = false;
                for (String k : map.keySet()) {
                    if (shouldDropPack(k)) {
                        anyDrop = true;
                        break;
                    }
                }
                if (!anyDrop) {
                    continue;
                }
                try {
                    Map<String, Object> copy = new HashMap<>();
                    for (Map.Entry<String, ?> e : map.entrySet()) {
                        if (!shouldDropPack(e.getKey())) {
                            copy.put(e.getKey(), e.getValue());
                        }
                    }
                    f.set(packRepository, copy);
                } catch (Throwable replaceFailed) {
                    try {
                        map.keySet().removeIf(RegistryHealthPolicy::shouldDropPack);
                    } catch (UnsupportedOperationException uoe) {
                        VyneLogger.debug("[RegistryHealth] stripSuppressedPacks: could not strip field "
                            + f.getName() + " — " + replaceFailed + " / " + uoe.getMessage());
                    }
                }
            }
        } catch (Throwable t) {
            String msg = t.getMessage();
            VyneLogger.warn("[RegistryHealth] stripSuppressedPacks failed: " + (msg != null ? msg : String.valueOf(t)));
        }
    }

    private static boolean stringKeyedMap(Field f) {
        Type gt = f.getGenericType();
        if (!(gt instanceof ParameterizedType pt)) {
            return false;
        }
        Type[] args = pt.getActualTypeArguments();
        return args.length >= 1 && args[0] == String.class;
    }

    public static boolean packMatchesMod(String packId, String modId) {
        if (modId == null || modId.isEmpty()) {
            return false;
        }
        if (packId.equals(modId)) {
            return true;
        }
        if (packId.startsWith(modId + ":")) {
            return true;
        }
        if (packId.startsWith("mod/" + modId) || packId.startsWith("mod:" + modId)) {
            return true;
        }
        if (packId.startsWith("file/")) {
            String tail = packId.substring("file/".length());
            if (tail.equalsIgnoreCase(modId)) {
                return true;
            }
            String lower = tail.toLowerCase(Locale.ROOT);
            String m = modId.toLowerCase(Locale.ROOT);
            if (lower.startsWith(m) && tail.length() > modId.length()) {
                char d = tail.charAt(modId.length());
                if (d == '-' || d == '_' || d == '.') {
                    return true;
                }
            }
        }
        return false;
    }
}
