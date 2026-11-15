/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.containment;

import dev.stolym.vyne.util.VyneLogger;

import java.util.concurrent.ConcurrentHashMap;

public final class VyneModIdentity {
    private VyneModIdentity() {}

    private static final ConcurrentHashMap<ClassLoader, String> LOADER_TO_MOD_ID =
            new ConcurrentHashMap<>();

    private static final ThreadLocal<String> CURRENT_MOD_ID = new ThreadLocal<>();

    private static final ConcurrentHashMap<String, String> PKG_TO_MOD_ID =
            new ConcurrentHashMap<>();

    public static void registerLoader(ClassLoader loader, String modId) {
        if (loader == null || modId == null || modId.isBlank()) return;
        LOADER_TO_MOD_ID.put(loader, modId);
        VyneLogger.debug("[VyneModIdentity] Registered loader " + loader.getClass().getSimpleName()
                + " → mod '" + modId + "'");
    }

    public static void registerPackage(String rootPkg, String modId) {
        if (rootPkg == null || modId == null) return;
        PKG_TO_MOD_ID.put(rootPkg, modId);
    }

    public static String resolveModId(Class<?> cls) {
        if (cls == null) return null;

        String ctx = CURRENT_MOD_ID.get();
        if (ctx != null) return ctx;

        ClassLoader cl = cls.getClassLoader();
        while (cl != null) {
            String id = LOADER_TO_MOD_ID.get(cl);
            if (id != null) return id;
            cl = cl.getParent();
        }

        return resolveFromPackage(cls.getPackageName());
    }

    public static String resolveModId(ClassLoader loader) {
        if (loader == null) return null;
        ClassLoader cl = loader;
        while (cl != null) {
            String id = LOADER_TO_MOD_ID.get(cl);
            if (id != null) return id;
            cl = cl.getParent();
        }
        return null;
    }

    private static String resolveFromPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        String bestMatch = null;
        String bestId = null;
        for (var entry : PKG_TO_MOD_ID.entrySet()) {
            String prefix = entry.getKey();
            if ((pkg.equals(prefix) || pkg.startsWith(prefix + "."))
                    && (bestMatch == null || prefix.length() > bestMatch.length())) {
                bestMatch = prefix;
                bestId = entry.getValue();
            }
        }
        return bestId;
    }

    public static void tagCurrentThread(String modId) {
        CURRENT_MOD_ID.set(modId);
    }

    public static void clearCurrentThread() {
        CURRENT_MOD_ID.remove();
    }

    public static String currentModId() {
        return CURRENT_MOD_ID.get();
    }
}
