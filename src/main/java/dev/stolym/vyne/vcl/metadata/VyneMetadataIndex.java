/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl.metadata;

import dev.stolym.vyne.core.DetectedMod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VyneMetadataIndex {
    private static final Map<String, ClassInfo> INDEX = new ConcurrentHashMap<>();

    public record ClassInfo(String superName, String[] interfaces, boolean isInterface,
                            DetectedMod.LoaderType loaderType, String sourceJar) {
        public ClassInfo(String superName, String[] interfaces, boolean isInterface) {
            this(superName, interfaces, isInterface, null, null);
        }
    }

    public static void register(String className, String superName, String[] interfaces, boolean isInterface) {
        register(className, superName, interfaces, isInterface, null, null);
    }

    public static void register(String className, String superName, String[] interfaces, boolean isInterface,
                                DetectedMod.LoaderType loaderType, String sourceJar) {
        if (className == null) return;
        INDEX.put(className.replace('.', '/'), new ClassInfo(
            superName != null ? superName.replace('.', '/') : null,
            sanitizeInterfaces(interfaces),
            isInterface,
            loaderType,
            sourceJar
        ));
    }

    public static ClassInfo get(String className) {
        if (className == null) return null;
        return INDEX.get(className.replace('.', '/'));
    }

    public static boolean contains(String className) {
        if (className == null) return false;
        return INDEX.containsKey(className.replace('.', '/'));
    }

    private static String[] sanitizeInterfaces(String[] interfaces) {
        if (interfaces == null) return new String[0];
        String[] sanitized = new String[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            sanitized[i] = interfaces[i].replace('.', '/');
        }
        return sanitized;
    }

    public static int size() {
        return INDEX.size();
    }

    public static void clear() {
        INDEX.clear();
    }
}
