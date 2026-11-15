/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.conflict;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.util.VyneLogger;

import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public final class MixinConflictScanner {
    public static void scan(List<DetectedMod> mods) {
        Map<String, List<String>> targetToMods = new LinkedHashMap<>();

        for (DetectedMod mod : mods) {
            for (String mixinCfg : mod.getMixins()) {
                try (JarFile jar = new JarFile(mod.getJarPath().toFile())) {
                    ZipEntry entry = jar.getEntry(mixinCfg);
                    if (entry == null) continue;
                    com.google.gson.JsonObject json;
                    try (java.io.InputStream is = jar.getInputStream(entry)) {
                        json = com.google.gson.JsonParser.parseReader(
                            new java.io.InputStreamReader(is)).getAsJsonObject();
                    }
                    String pkg = json.has("package") ? json.get("package").getAsString() + "." : "";
                    for (String key : new String[]{"mixins", "client", "server"}) {
                        if (!json.has(key)) continue;
                        for (com.google.gson.JsonElement el : json.getAsJsonArray(key)) {
                            String mixinClass = pkg + el.getAsString();
                            resolveTargets(mixinClass, mod.getJarPath().toFile(), jar)
                                .forEach(t -> targetToMods
                                    .computeIfAbsent(t, k -> new ArrayList<>())
                                    .add(mod.getModId() + "/" + mixinClass));
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        for (Map.Entry<String, List<String>> e : targetToMods.entrySet()) {
            if (e.getValue().size() > 1) {
                VyneLogger.warn("[MixinConflict] Target '" + e.getKey() + "' is mixed into by multiple mods:");
                e.getValue().forEach(m -> VyneLogger.warn("  - " + m));
                VyneLogger.warn("  This may cause unexpected behaviour or crashes.");
            }
        }
    }

    private static List<String> resolveTargets(String mixinClass, java.io.File jar, JarFile jf) {
        List<String> targets = new ArrayList<>();
        String path = mixinClass.replace('.', '/') + ".class";
        try {
            ZipEntry entry = jf.getEntry(path);
            if (entry == null) return targets;
            try (java.io.InputStream is = jf.getInputStream(entry)) {
                org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(is);
                org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
                cr.accept(cn, org.objectweb.asm.ClassReader.SKIP_CODE);
                if (cn.visibleAnnotations == null) return targets;
                for (org.objectweb.asm.tree.AnnotationNode ann : cn.visibleAnnotations) {
                    if (!ann.desc.endsWith("Mixin;")) continue;
                    if (ann.values == null) continue;
                    for (int i = 0; i < ann.values.size() - 1; i += 2) {
                        Object key = ann.values.get(i);
                        Object val = ann.values.get(i + 1);
                        if (("value".equals(key) || "targets".equals(key)) && val instanceof List<?> list) {
                            list.forEach(v -> targets.add(v.toString()));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return targets;
    }
}
