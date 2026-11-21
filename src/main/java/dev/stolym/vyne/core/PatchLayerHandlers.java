/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.core;

import dev.stolym.vyne.util.VyneLogger;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PatchLayerHandlers {
    public static final String RESOURCE = "/vyne/patch-layer/fabric-api-handlers.txt";
    private static volatile Map<String, Map<String, String>> table;

    private PatchLayerHandlers() {}

    public static void strip(ClassNode node) {
        if (node == null || !VyneMode.patchedGame()) return;
        if (node.name.startsWith("net/fabricmc/fabric/mixin/")) {
            for (MethodNode method : node.methods) {
                makeOptional(method.visibleAnnotations);
                makeOptional(method.invisibleAnnotations);
            }
        }
        Map<String, String> handlers = table().get(node.name);
        if (handlers == null) return;
        for (MethodNode method : node.methods) {
            String status = handlers.get(method.name);
            if (status == null) continue;
            boolean removed = removeInjectors(method.visibleAnnotations) | removeInjectors(method.invisibleAnnotations);
            if (!removed) continue;
            String handler = node.name.substring(node.name.lastIndexOf('/') + 1) + "#" + method.name;
            if (status.equals("replaced")) {
                VyneLogger.info("[PatchLayer] " + handler + " is handled by Vyne's patch layer");
            } else {
                VyneLogger.warn("[PatchLayer] " + handler + " is not available on Vyne's patch layer yet");
            }
        }
    }

    public static InputStream filterConfig(String name, InputStream in) {
        if (in == null || !VyneMode.patchedGame() || !name.endsWith(".mixins.json")) return in;
        try (InputStream source = in) {
            byte[] bytes = source.readAllBytes();
            com.google.gson.JsonObject config = com.google.gson.JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            String pkg = config.has("package") ? config.get("package").getAsString().replace('.', '/') + "/" : "";
            boolean changed = false;
            for (String side : new String[] {"mixins", "client", "server"}) {
                if (!config.has(side) || !config.get(side).isJsonArray()) continue;
                com.google.gson.JsonArray kept = new com.google.gson.JsonArray();
                for (com.google.gson.JsonElement entry : config.getAsJsonArray(side)) {
                    String mixin = pkg + entry.getAsString().replace('.', '/');
                    Map<String, String> handlers = table().get(mixin);
                    String status = handlers == null ? null : handlers.get("*");
                    if (status == null) {
                        kept.add(entry);
                        continue;
                    }
                    changed = true;
                    String simple = mixin.substring(mixin.lastIndexOf('/') + 1);
                    if (status.equals("replaced")) {
                        VyneLogger.info("[PatchLayer] " + simple + " is handled by Vyne's patch layer");
                    } else {
                        VyneLogger.warn("[PatchLayer] " + simple + " is not available on Vyne's patch layer yet");
                    }
                }
                config.add(side, kept);
            }
            byte[] out = changed ? config.toString().getBytes(StandardCharsets.UTF_8) : bytes;
            return new java.io.ByteArrayInputStream(out);
        } catch (Exception e) {
            VyneLogger.warn("[PatchLayer] Could not read mixin config " + name + ": " + e.getMessage());
            return null;
        }
    }

    static void makeOptional(List<AnnotationNode> annotations) {
        if (annotations == null) return;
        for (AnnotationNode annotation : annotations) {
            if (!isInjector(annotation)) continue;
            if (annotation.values == null) annotation.values = new java.util.ArrayList<>();
            int index = annotation.values.indexOf("require");
            if (index >= 0 && index % 2 == 0) {
                annotation.values.set(index + 1, 0);
            } else {
                annotation.values.add("require");
                annotation.values.add(0);
            }
        }
    }

    static boolean isInjector(AnnotationNode annotation) {
        return annotation.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/") && !annotation.desc.endsWith("/At;")
                && !annotation.desc.endsWith("/Slice;") && !annotation.desc.endsWith("/Desc;")
                || annotation.desc.startsWith("Lcom/llamalad7/mixinextras/injector/");
    }

    static boolean removeInjectors(List<AnnotationNode> annotations) {
        if (annotations == null) return false;
        return annotations.removeIf(PatchLayerHandlers::isInjector);
    }

    static Map<String, Map<String, String>> table() {
        Map<String, Map<String, String>> current = table;
        if (current != null) return current;
        Map<String, Map<String, String>> loaded = new HashMap<>();
        try (InputStream in = PatchLayerHandlers.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) {
                        loaded.computeIfAbsent(parts[0], k -> new HashMap<>()).put(parts[1], parts.length > 2 ? parts[2] : "replaced");
                    }
                }
            }
        } catch (Exception e) {
            VyneLogger.warn("[PatchLayer] Could not read " + RESOURCE + ": " + e.getMessage());
        }
        table = loaded;
        return loaded;
    }
}
