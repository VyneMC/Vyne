/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.client;

import dev.stolym.vyne.util.VyneLogger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class ClientRegistrationDiag {
    private ClientRegistrationDiag() {}

    public static void logAfterVclClient(ClassLoader cl, String modNamespace) {
        if (cl == null || modNamespace == null || modNamespace.isBlank()) {
            return;
        }
        try {
            logAllRegistriesWithModEntries(cl, modNamespace);
        } catch (Throwable t) {
            VyneLogger.warn("[ClientDiag] registry snapshot failed: " + t);
        }
    }

    private static void logAllRegistriesWithModEntries(ClassLoader cl, String modNs) throws Exception {
        Class<?> registries = Class.forName("net.minecraft.class_7923", false, cl);
        int logged = 0;
        boolean anyHits = false;
        for (Field f : registries.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            if (!isProbablyRegistry(f.getType())) continue;
            f.setAccessible(true);
            Object registry;
            try {
                registry = f.get(null);
            } catch (Throwable ignored) {
                continue;
            }
            if (registry == null) continue;
            List<String> hits = collectIdsContaining(registry, modNs);
            if (hits.isEmpty()) continue;
            anyHits = true;
            List<String> sample = hits.size() > 10 ? hits.subList(0, 10) : hits;
            VyneLogger.info("[ClientDiag] Registries." + f.getName() + " → " + hits.size() + " `"
                + modNs + ":*` id(s): " + String.join(", ", sample) + (hits.size() > 10 ? " …" : ""));
            if (++logged >= 30) {
                VyneLogger.info("[ClientDiag] … truncated after 30 registry fields with hits");
                break;
            }
        }
        if (!anyHits) {
            int vyneEntries = countVyneRegistryEntries(modNs);
            if (vyneEntries > 0) {
                VyneLogger.info("[ClientDiag] " + modNs + " has " + vyneEntries
                        + " Vyne-registered id(s) across all registries; Mojang's static "
                        + "Registries snapshot did not mirror them (informational, not a failure).");
            } else {
                VyneLogger.warn("[ClientDiag] No `" + modNs + ":*` entries in any scanned Registries field — "
                    + "client registration (menus, creative tabs, models) likely did not run.");
            }
        }
    }

    private static int countVyneRegistryEntries(String modNs) {
        try {
            java.util.Set<String> paths = dev.stolym.vyne.registry.VyneGenericRegistry.getAllRegistryPaths();
            int total = 0;
            String prefix = modNs + ":";
            for (String path : paths) {
                for (String key : dev.stolym.vyne.registry.VyneGenericRegistry.getKeys(path)) {
                    if (key.startsWith(prefix)) total++;
                }
            }
            return total;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static boolean isProbablyRegistry(Class<?> t) {
        if (t == null) return false;
        for (Class<?> c = t; c != null; c = c.getSuperclass()) {
            if (c.getName().contains("Registry")) return true;
        }
        for (Class<?> i : t.getInterfaces()) {
            if (i.getName().contains("Registry")) return true;
        }
        return false;
    }

    private static List<String> collectIdsContaining(Object registry, String modNs) {
        List<String> out = new ArrayList<>();
        try {
            Method stream = null;
            for (Method m : registry.getClass().getMethods()) {
                if (!"stream".equals(m.getName()) || m.getParameterCount() != 0) continue;
                if (java.util.stream.Stream.class.isAssignableFrom(m.getReturnType())) {
                    stream = m;
                    break;
                }
            }
            if (stream != null) {
                java.util.stream.Stream<?> s = (java.util.stream.Stream<?>) stream.invoke(registry);
                if (s != null) {
                    s.forEach(entry -> {
                        String id = extractIdString(entry);
                        if (id != null && id.startsWith(modNs + ":")) {
                            out.add(id);
                        }
                    });
                }
                return out;
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static String extractIdString(Object holderOrEntry) {
        if (holderOrEntry == null) return null;
        try {
            for (Method m : holderOrEntry.getClass().getMethods()) {
                if (!"getId".equals(m.getName()) || m.getParameterCount() != 0) continue;
                Object id = m.invoke(holderOrEntry);
                return id == null ? null : id.toString();
            }
        } catch (Throwable ignored) {
        }
        try {
            for (Method m : holderOrEntry.getClass().getMethods()) {
                if (!"value".equals(m.getName()) || m.getParameterCount() != 0) continue;
                Object inner = m.invoke(holderOrEntry);
                return extractIdString(inner);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
