/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.tools.hooks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

final class FabricApiTargets {
    record Target(String owner, String method, String kind, String at, String module, String mixin) {}

    private static final Map<String, String> INJECTORS = Map.ofEntries(
            Map.entry("Lorg/spongepowered/asm/mixin/injection/Inject;", "Inject"),
            Map.entry("Lorg/spongepowered/asm/mixin/injection/Redirect;", "Redirect"),
            Map.entry("Lorg/spongepowered/asm/mixin/injection/ModifyArg;", "ModifyArg"),
            Map.entry("Lorg/spongepowered/asm/mixin/injection/ModifyArgs;", "ModifyArgs"),
            Map.entry("Lorg/spongepowered/asm/mixin/injection/ModifyVariable;", "ModifyVariable"),
            Map.entry("Lorg/spongepowered/asm/mixin/injection/ModifyConstant;", "ModifyConstant"),
            Map.entry("Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;", "ModifyExpressionValue"),
            Map.entry("Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;", "ModifyReturnValue"),
            Map.entry("Lcom/llamalad7/mixinextras/injector/ModifyReceiver;", "ModifyReceiver"),
            Map.entry("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;", "WrapOperation"),
            Map.entry("Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;", "WrapWithCondition"),
            Map.entry("Lcom/llamalad7/mixinextras/injector/WrapWithCondition;", "WrapWithCondition"),
            Map.entry("Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;", "WrapMethod"));
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";

    static List<Target> collect(Path fabricApiJar, Names names) throws IOException {
        List<Target> out = new ArrayList<>();
        try (ZipFile zip = new ZipFile(fabricApiJar.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                String name = entry.getName();
                if (!name.startsWith("META-INF/jars/") || !name.endsWith(".jar")) continue;
                String module = name.substring("META-INF/jars/".length()).replaceAll("-[0-9].*", "");
                try (InputStream in = zip.getInputStream(entry)) {
                    collectModule(in.readAllBytes(), module, names, out);
                }
            }
        }
        out.sort((a, b) -> {
            int c = a.owner().compareTo(b.owner());
            if (c != 0) return c;
            c = a.method().compareTo(b.method());
            return c != 0 ? c : a.mixin().compareTo(b.mixin());
        });
        return out;
    }

    private static void collectModule(byte[] jar, String module, Names names, List<Target> out) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) files.put(entry.getName(), in.readAllBytes());
        }
        Map<String, Map<String, String>> refmap = new HashMap<>();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            if (!file.getKey().endsWith("refmap.json")) continue;
            JsonObject root = JsonParser.parseString(new String(file.getValue(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("mappings")) continue;
            for (Map.Entry<String, JsonElement> type : root.getAsJsonObject("mappings").entrySet()) {
                Map<String, String> entries = refmap.computeIfAbsent(type.getKey(), k -> new HashMap<>());
                for (Map.Entry<String, JsonElement> e : type.getValue().getAsJsonObject().entrySet()) {
                    entries.put(e.getKey(), e.getValue().getAsString());
                }
            }
        }
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            if (!file.getKey().endsWith(".class")) continue;
            ClassNode mixin = new ClassNode();
            new ClassReader(file.getValue()).accept(mixin, ClassReader.SKIP_CODE);
            Map<String, String> refs = refmap.getOrDefault(mixin.name, Map.of());
            List<String> targets = new ArrayList<>();
            for (String target : mixinTargets(mixin)) targets.add(Names.internal(refs.getOrDefault(target.replace('/', '.'), refs.getOrDefault(target, target))));
            targets.removeIf(type -> !type.startsWith("net/minecraft/") && !type.startsWith("com/mojang/"));
            if (targets.isEmpty()) continue;
            for (MethodNode method : mixin.methods) {
                for (AnnotationNode annotation : annotations(method)) {
                    if (annotation.desc.equals(OVERWRITE)) {
                        for (String target : targets) {
                            Names.Method mapped = names.method(target, method.name, method.desc);
                            out.add(new Target(names.type(target), mapped.name() + mapped.desc(), "Overwrite", "",
                                    module, mixin.name));
                        }
                        continue;
                    }
                    String kind = INJECTORS.get(annotation.desc);
                    if (kind == null) continue;
                    String at = at(annotation, refs, names);
                    for (String selector : stringList(value(annotation, "method"))) {
                        String resolved = refs.getOrDefault(selector, selector);
                        for (String target : targets) {
                            out.add(new Target(names.type(target), method(resolved, target, names), kind, at, module, mixin.name));
                        }
                    }
                }
            }
        }
    }

    private static String method(String selector, String target, Names names) {
        String owner = target;
        String rest = selector;
        if (rest.startsWith("L") && rest.contains(";")) {
            owner = rest.substring(1, rest.indexOf(';'));
            rest = rest.substring(rest.indexOf(';') + 1);
        }
        int paren = rest.indexOf('(');
        String name = paren >= 0 ? rest.substring(0, paren) : rest;
        String desc = paren >= 0 ? rest.substring(paren) : "";
        if (name.contains("*")) return name;
        Names.Method mapped = names.method(owner, name, desc);
        return mapped.name() + (mapped.desc() == null ? "" : mapped.desc());
    }

    private static String at(AnnotationNode injector, Map<String, String> refs, Names names) {
        Object at = value(injector, "at");
        if (at instanceof List<?> list && !list.isEmpty()) at = list.get(0);
        if (!(at instanceof AnnotationNode node)) return "";
        Object where = value(node, "value");
        Object target = value(node, "target");
        StringBuilder out = new StringBuilder(where == null ? "" : where.toString());
        if (target instanceof String selector && !selector.isEmpty()) {
            String resolved = refs.getOrDefault(selector, selector);
            out.append(' ').append(member(resolved, names));
        }
        return out.toString();
    }

    private static String member(String selector, Names names) {
        if (!selector.startsWith("L") || !selector.contains(";")) {
            if (selector.startsWith("(") || selector.startsWith("[")) return names.desc(selector);
            return names.type(selector.replace('.', '/'));
        }
        String owner = selector.substring(1, selector.indexOf(';'));
        String rest = selector.substring(selector.indexOf(';') + 1);
        int paren = rest.indexOf('(');
        int colon = rest.indexOf(':');
        if (paren >= 0) {
            Names.Method mapped = names.method(owner, rest.substring(0, paren), rest.substring(paren));
            return names.type(owner) + "." + mapped.name() + mapped.desc();
        }
        if (colon >= 0) return names.type(owner) + "." + rest.substring(0, colon) + ":" + names.desc(rest.substring(colon + 1));
        return names.type(owner) + "." + rest;
    }

    private static List<String> mixinTargets(ClassNode mixin) {
        List<String> out = new ArrayList<>();
        for (AnnotationNode annotation : annotations(mixin)) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
            Object types = value(annotation, "value");
            if (types instanceof List<?> list) for (Object type : list) out.add(((Type) type).getInternalName());
            for (String target : stringList(value(annotation, "targets"))) out.add(Names.internal(target));
        }
        return out;
    }

    private static List<AnnotationNode> annotations(ClassNode node) {
        List<AnnotationNode> out = new ArrayList<>();
        if (node.visibleAnnotations != null) out.addAll(node.visibleAnnotations);
        if (node.invisibleAnnotations != null) out.addAll(node.invisibleAnnotations);
        return out;
    }

    private static List<AnnotationNode> annotations(MethodNode node) {
        List<AnnotationNode> out = new ArrayList<>();
        if (node.visibleAnnotations != null) out.addAll(node.visibleAnnotations);
        if (node.invisibleAnnotations != null) out.addAll(node.invisibleAnnotations);
        return out;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static List<String> stringList(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s) out.add(s);
            }
        } else if (value instanceof String s) {
            out.add(s);
        }
        return out;
    }
}
