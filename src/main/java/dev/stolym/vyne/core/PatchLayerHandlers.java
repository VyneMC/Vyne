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
        if (node.name.startsWith("dev/stolym/vyne/hooks/mixin/")) {
            expandLambdaSelectors(node);
            return;
        }
        if (isFabricModMixin(node.name)) {
            for (MethodNode method : node.methods) {
                makeOptional(method.visibleAnnotations);
                makeOptional(method.invisibleAnnotations);
            }
            try {
                HookRetarget.retarget(node);
            } catch (RuntimeException | LinkageError e) {
                VyneLogger.debug("[PatchLayer] Could not retarget hooks of " + node.name + ": " + e);
            }
        }
        rewriteForNeoForge(node);
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

    private static void rewriteForNeoForge(ClassNode node) {
        switch (node.name) {
            case "net/fabricmc/fabric/mixin/client/rendering/BlockColorsMixin" ->
                    colorLookup(node, "field_1995", "blockColors", "(Lnet/minecraft/class_2248;)Lnet/minecraft/class_322;", "net/minecraft/class_322", false);
            case "net/fabricmc/fabric/mixin/client/rendering/ItemColorsMixin" ->
                    colorLookup(node, "field_1996", "itemColors", "(Lnet/minecraft/class_1935;)Lnet/minecraft/class_326;", "net/minecraft/class_326", true);
            case "net/fabricmc/fabric/mixin/client/particle/ParticleManagerAccessor" -> {
                for (MethodNode method : node.methods) {
                    if (!method.name.equals("getFactories") || method.visibleAnnotations == null) continue;
                    for (AnnotationNode annotation : method.visibleAnnotations) {
                        if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/gen/Accessor;")) continue;
                        annotation.values = new java.util.ArrayList<>(List.of("value", "vyne$fabricParticleFactories"));
                    }
                }
                VyneLogger.info("[PatchLayer] Fabric particle factories go into NeoForge's particle providers");
            }
            default -> {
            }
        }
    }

    private static void colorLookup(ClassNode node, String vanillaField, String neoForgeField, String getDesc, String provider, boolean item) {
        for (org.objectweb.asm.tree.FieldNode field : node.fields) {
            if (field.name.equals(vanillaField)) {
                field.name = neoForgeField;
                field.desc = "Ljava/util/Map;";
                field.signature = null;
            }
        }
        for (MethodNode method : node.methods) {
            if (!method.name.equals("get") || !method.desc.equals(getDesc)) continue;
            org.objectweb.asm.tree.InsnList code = new org.objectweb.asm.tree.InsnList();
            code.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 0));
            code.add(new org.objectweb.asm.tree.FieldInsnNode(org.objectweb.asm.Opcodes.GETFIELD, node.name, neoForgeField, "Ljava/util/Map;"));
            code.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 1));
            if (item) {
                code.add(new org.objectweb.asm.tree.MethodInsnNode(org.objectweb.asm.Opcodes.INVOKEINTERFACE, "net/minecraft/class_1935", "method_8389", "()Lnet/minecraft/class_1792;", true));
            }
            code.add(new org.objectweb.asm.tree.MethodInsnNode(org.objectweb.asm.Opcodes.INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true));
            code.add(new org.objectweb.asm.tree.TypeInsnNode(org.objectweb.asm.Opcodes.CHECKCAST, provider));
            code.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ARETURN));
            method.instructions = code;
            method.tryCatchBlocks = new java.util.ArrayList<>();
            method.localVariables = null;
            method.maxStack = 2;
            method.maxLocals = 2;
        }
        VyneLogger.info("[PatchLayer] Fabric " + (item ? "item" : "block") + " colour providers read NeoForge's " + neoForgeField);
    }

    private static void expandLambdaSelectors(ClassNode node) {
        for (MethodNode method : node.methods) {
            for (List<AnnotationNode> annotations : java.util.Arrays.asList(method.visibleAnnotations, method.invisibleAnnotations)) {
                if (annotations == null) continue;
                for (AnnotationNode annotation : annotations) {
                    if (!isInjector(annotation) || annotation.values == null) continue;
                    int index = annotation.values.indexOf("method");
                    if (index < 0 || index % 2 != 0 || !(annotation.values.get(index + 1) instanceof List<?> selectors)) continue;
                    List<Object> expanded = new java.util.ArrayList<>();
                    for (Object selector : selectors) {
                        if (selector instanceof String s && s.startsWith("lambda:")) {
                            expanded.add("/^lambda\\$" + s.substring("lambda:".length()) + "\\$\\d+$/");
                        } else {
                            expanded.add(selector);
                        }
                    }
                    annotation.values.set(index + 1, expanded);
                }
            }
        }
    }

    private static boolean isFabricModMixin(String internalName) {
        if (internalName.startsWith("net/fabricmc/fabric/mixin/")) return true;
        if (internalName.startsWith("dev/stolym/vyne/")) return false;
        dev.stolym.vyne.vcl.metadata.VyneMetadataIndex.ClassInfo info = dev.stolym.vyne.vcl.metadata.VyneMetadataIndex.get(internalName.replace('/', '.'));
        return info == null || info.loaderType() == DetectedMod.LoaderType.FABRIC;
    }

    private static final java.util.regex.Pattern HANDLER = java.util.regex.Pattern.compile(
            "^(handler|redirect|modify|args|localvar|constant|wrapOperation|modifyExpressionValue|modifyReturnValue|modifyReceiver|wrapWithCondition|wrapMethod)\\$[a-z0-9]+\\$(.+)$");
    private static final java.util.Set<String> REPORTED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void reportUnapplied(String className, byte[] bytes) {
        if (bytes == null || !VyneMode.patchedGame() || !className.startsWith("net.minecraft.")) return;
        try {
            ClassNode node = new ClassNode();
            new org.objectweb.asm.ClassReader(bytes).accept(node, org.objectweb.asm.ClassReader.SKIP_FRAMES | org.objectweb.asm.ClassReader.SKIP_DEBUG);
            java.util.Set<String> handlers = new java.util.LinkedHashSet<>();
            for (MethodNode method : node.methods) {
                if (HANDLER.matcher(method.name).matches()) handlers.add(method.name);
            }
            if (handlers.isEmpty()) return;
            for (MethodNode method : node.methods) {
                if (method.instructions == null) continue;
                for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof org.objectweb.asm.tree.MethodInsnNode call) {
                        handlers.remove(call.name);
                    } else if (insn instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode indy) {
                        for (Object arg : indy.bsmArgs) {
                            if (arg instanceof org.objectweb.asm.Handle handle) handlers.remove(handle.getName());
                        }
                    } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc && ldc.cst instanceof org.objectweb.asm.Handle handle) {
                        handlers.remove(handle.getName());
                    }
                }
            }
            for (String handler : handlers) {
                java.util.regex.Matcher m = HANDLER.matcher(handler);
                if (!m.matches() || !REPORTED.add(className + "#" + handler)) continue;
                String rest = m.group(2);
                int split = rest.indexOf('$');
                String mod = split > 0 ? rest.substring(0, split) : "?";
                String method = split > 0 ? rest.substring(split + 1) : rest;
                if ("fabricloader".equals(mod)) continue;
                if (covered().contains(mod + " " + method)) {
                    VyneLogger.info("[PatchLayer] " + mod + "#" + method + " is handled by Vyne's patch layer");
                    continue;
                }
                VyneLogger.warn("[PatchLayer] " + mod + ": hook " + method + " found nothing to attach to in " + className
                        + " on NeoForge's patched game and is skipped");
            }
        } catch (RuntimeException e) {
            VyneLogger.debug("[PatchLayer] Could not check hooks in " + className + ": " + e);
        }
    }

    private static volatile java.util.Set<String> covered;

    private static java.util.Set<String> covered() {
        java.util.Set<String> current = covered;
        if (current != null) return current;
        java.util.Set<String> loaded = new java.util.HashSet<>();
        try (InputStream in = PatchLayerHandlers.class.getResourceAsStream("/vyne/patch-layer/covered-hooks.txt")) {
            if (in != null) {
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                    if (!line.isBlank()) loaded.add(line.trim().replaceAll("\\s+", " "));
                }
            }
        } catch (Exception e) {
            VyneLogger.warn("[PatchLayer] Could not read covered-hooks.txt: " + e.getMessage());
        }
        covered = loaded;
        return loaded;
    }

    static void makeOptional(List<AnnotationNode> annotations) {
        if (annotations == null) return;
        for (AnnotationNode annotation : annotations) {
            if (!isInjector(annotation)) continue;
            if (annotation.values == null) annotation.values = new java.util.ArrayList<>();
            int locals = annotation.values.indexOf("locals");
            if (locals >= 0 && locals % 2 == 0 && annotation.values.get(locals + 1) instanceof String[] capture
                    && capture.length == 2 && "CAPTURE_FAILHARD".equals(capture[1])) {
                annotation.values.set(locals + 1, new String[] {capture[0], "CAPTURE_FAILSOFT"});
            }
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
