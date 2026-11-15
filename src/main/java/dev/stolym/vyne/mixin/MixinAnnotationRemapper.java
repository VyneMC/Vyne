/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.mappings.MappingComposer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class MixinAnnotationRemapper {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    private static final String[] INJECTOR_PACKAGES = {
            "Lorg/spongepowered/asm/mixin/injection/",
            "Lcom/llamalad7/mixinextras/injector/",
    };

    private final MappingComposer.ComposedMappings mappings;
    private final Function<String, List<String>> superTypes;
    private final Remapper descRemapper;
    private List<String> targets;

    private MixinAnnotationRemapper(MappingComposer.ComposedMappings mappings, Function<String, List<String>> superTypes) {
        this.mappings = mappings;
        this.superTypes = superTypes;
        this.descRemapper = new Remapper() {
            @Override
            public String map(String internalName) {
                return mappings.mapClass(internalName);
            }
        };
    }

    public static boolean isMixin(byte[] bytes) {
        boolean[] found = {false};
        new ClassReader(bytes).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (MIXIN.equals(descriptor)) found[0] = true;
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[0];
    }

    public static byte[] remap(byte[] bytes, MappingComposer.ComposedMappings mappings) {
        return remap(bytes, mappings, owner -> superTypesFromGame(mappings, owner));
    }

    static byte[] remap(byte[] bytes, MappingComposer.ComposedMappings mappings, Function<String, List<String>> superTypes) {
        if (!isMixin(bytes)) return bytes;
        return new MixinAnnotationRemapper(mappings, superTypes).apply(bytes);
    }

    private static List<String> superTypesFromGame(MappingComposer.ComposedMappings mappings, String mojOwner) {
        byte[] raw = MappingComposer.readIntermediaryClassBytesProbe(mappings.mapClass(mojOwner));
        if (raw == null) return List.of();
        ClassReader reader = new ClassReader(raw);
        List<String> out = new ArrayList<>();
        if (reader.getSuperName() != null) out.add(reader.getSuperName());
        out.addAll(List.of(reader.getInterfaces()));
        List<String> moj = new ArrayList<>();
        for (String inter : out) moj.add(mappings.reverseClassMap().getOrDefault(inter, inter));
        return moj;
    }

    private byte[] apply(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        targets = readTargets(node);
        if (targets.isEmpty()) return bytes;

        Map<String, String> renames = new HashMap<>();
        if (node.fields != null) {
            for (FieldNode field : node.fields) {
                remapAnnotations(field.visibleAnnotations);
                remapAnnotations(field.invisibleAnnotations);
                if (has(field, SHADOW)) {
                    String prefix = shadowPrefix(field.visibleAnnotations, field.invisibleAnnotations);
                    String bare = stripPrefix(field.name, prefix);
                    String mapped = mapFieldOnTargets(bare);
                    if (mapped != null) renames.put(node.name + "." + field.name, prefixed(mapped, field.name, prefix));
                }
            }
        }
        if (node.methods != null) {
            for (MethodNode method : node.methods) {
                remapAnnotations(method.visibleAnnotations);
                remapAnnotations(method.invisibleAnnotations);
                remapAccessor(method, method.visibleAnnotations);
                remapAccessor(method, method.invisibleAnnotations);
                if (has(method, SHADOW) || has(method, OVERWRITE)) {
                    String prefix = shadowPrefix(method.visibleAnnotations, method.invisibleAnnotations);
                    String bare = stripPrefix(method.name, prefix);
                    String mapped = mapMethodOnTargets(bare, method.desc);
                    if (mapped != null) renames.put(node.name + "." + method.name + method.desc, prefixed(mapped, method.name, prefix));
                }
            }
        }
        remapMixinTargetStrings(node.visibleAnnotations);
        remapMixinTargetStrings(node.invisibleAnnotations);

        ClassWriter writer = new ClassWriter(0);
        if (renames.isEmpty()) {
            node.accept(writer);
        } else {
            node.accept(new ClassRemapper(writer, new SimpleRemapper(renames)));
        }
        return writer.toByteArray();
    }

    private List<String> readTargets(ClassNode node) {
        List<String> out = new ArrayList<>();
        for (AnnotationNode ann : all(node.visibleAnnotations, node.invisibleAnnotations)) {
            if (!MIXIN.equals(ann.desc) || ann.values == null) continue;
            for (int i = 0; i + 1 < ann.values.size(); i += 2) {
                Object key = ann.values.get(i);
                Object value = ann.values.get(i + 1);
                if (!(value instanceof List<?> list)) continue;
                for (Object entry : list) {
                    if ("value".equals(key) && entry instanceof Type type) out.add(type.getInternalName());
                    if ("targets".equals(key) && entry instanceof String s) out.add(s.replace('.', '/'));
                }
            }
        }
        return out;
    }

    private void remapMixinTargetStrings(List<AnnotationNode> annotations) {
        if (annotations == null) return;
        for (AnnotationNode ann : annotations) {
            if (!MIXIN.equals(ann.desc) || ann.values == null) continue;
            for (int i = 0; i + 1 < ann.values.size(); i += 2) {
                if (!"targets".equals(ann.values.get(i)) || !(ann.values.get(i + 1) instanceof List<?> list)) continue;
                List<Object> mapped = new ArrayList<>();
                for (Object entry : list) {
                    mapped.add(entry instanceof String s ? mappings.mapClass(s.replace('.', '/')) : entry);
                }
                ann.values.set(i + 1, mapped);
            }
        }
    }

    private void remapAnnotations(List<AnnotationNode> annotations) {
        if (annotations == null) return;
        for (AnnotationNode ann : annotations) remapAnnotation(ann);
    }

    private void remapAnnotation(AnnotationNode ann) {
        if (ann.values == null) return;
        boolean injector = isInjector(ann.desc);
        for (int i = 0; i + 1 < ann.values.size(); i += 2) {
            String key = (String) ann.values.get(i);
            Object value = ann.values.get(i + 1);
            if (injector && "method".equals(key)) {
                ann.values.set(i + 1, remapSelectorList(value));
            } else if (AT.equals(ann.desc) && "target".equals(key) && value instanceof String s) {
                ann.values.set(i + 1, remapQualifiedReference(s));
            } else if (AT.equals(ann.desc) && "args".equals(key) && value instanceof List<?> list) {
                ann.values.set(i + 1, remapAtArgs(list));
            } else {
                remapNested(value);
            }
        }
    }

    private void remapNested(Object value) {
        if (value instanceof AnnotationNode nested) {
            remapAnnotation(nested);
        } else if (value instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof AnnotationNode nested) remapAnnotation(nested);
            }
        }
    }

    private Object remapSelectorList(Object value) {
        List<String> selectors = new ArrayList<>();
        if (value instanceof String s) {
            selectors.add(s);
        } else if (value instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s) selectors.add(s);
            }
        } else {
            return value;
        }

        Set<String> out = new LinkedHashSet<>();
        for (String selector : selectors) out.addAll(remapTargetSelector(selector));
        return new ArrayList<>(out);
    }

    private List<Object> remapAtArgs(List<?> args) {
        List<Object> out = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof String s && s.startsWith("classValue=")) {
                out.add("classValue=" + mappings.mapClass(s.substring("classValue=".length()).replace('.', '/')));
            } else {
                out.add(arg);
            }
        }
        return out;
    }

    private List<String> remapTargetSelector(String raw) {
        Selector sel = Selector.parse(raw);
        if (sel == null || sel.name.isEmpty() || sel.name.startsWith("<")) {
            return List.of(sel == null ? raw : sel.withDesc(sel.desc == null ? null : remapDesc(sel.desc)).toString());
        }
        List<String> owners = sel.owner != null ? List.of(sel.owner) : targets;
        if (sel.desc != null) {
            for (String owner : owners) {
                String mapped = mappings.mapMethod(owner, sel.name, sel.desc);
                if (mapped != null && !mapped.equals(sel.name)) {
                    return List.of(sel.withName(mapped).withDesc(remapDesc(sel.desc)).withOwner(mapOwner(sel.owner)).toString());
                }
            }
            return List.of(sel.withDesc(remapDesc(sel.desc)).withOwner(mapOwner(sel.owner)).toString());
        }
        for (String owner : owners) {
            List<String> overloads = new ArrayList<>();
            Set<String> seenDescs = new java.util.HashSet<>();
            java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>(List.of(owner));
            Set<String> visited = new java.util.HashSet<>();
            while (!queue.isEmpty()) {
                String current = queue.poll();
                if (current == null || current.startsWith("java/") || !visited.add(current)) continue;
                Map<String, String> methods = mappings.methodMap().get(current);
                if (methods != null) {
                    for (Map.Entry<String, String> e : methods.entrySet()) {
                        String key = e.getKey();
                        int paren = key.indexOf('(');
                        if (paren < 0 || !key.substring(0, paren).equals(sel.name)) continue;
                        String desc = key.substring(paren);
                        if (!seenDescs.add(desc)) continue;
                        overloads.add(sel.withName(e.getValue()).withDesc(remapDesc(desc)).withOwner(mapOwner(sel.owner)).toString());
                    }
                }
                queue.addAll(superTypes.apply(current));
            }
            if (!overloads.isEmpty()) return overloads;
        }
        return List.of(raw);
    }

    private String remapQualifiedReference(String raw) {
        Selector sel = Selector.parse(raw);
        if (sel == null) return raw;
        if (sel.owner == null) {
            return sel.desc == null && !sel.name.isEmpty() && sel.name.indexOf('/') >= 0
                    ? mappings.mapClass(sel.name) : raw;
        }
        String owner = sel.owner;
        if (sel.name.isEmpty()) {
            return sel.withOwner(mappings.mapClass(owner)).toString();
        }
        boolean field = sel.desc != null && !sel.desc.startsWith("(");
        String name = sel.name;
        if (!name.startsWith("<")) {
            String mapped = field ? mappings.mapField(owner, name) : mappings.mapMethod(owner, name, sel.desc);
            if (mapped != null) name = mapped;
        }
        String desc = sel.desc == null ? null : remapDesc(sel.desc);
        return sel.withOwner(mappings.mapClass(owner)).withName(name).withDesc(desc).toString();
    }

    private void remapAccessor(MethodNode method, List<AnnotationNode> annotations) {
        if (annotations == null) return;
        for (AnnotationNode ann : annotations) {
            boolean accessor = ACCESSOR.equals(ann.desc);
            boolean invoker = INVOKER.equals(ann.desc);
            if (!accessor && !invoker) continue;

            String explicit = null;
            int valueIndex = -1;
            if (ann.values != null) {
                for (int i = 0; i + 1 < ann.values.size(); i += 2) {
                    if ("value".equals(ann.values.get(i)) && ann.values.get(i + 1) instanceof String s && !s.isEmpty()) {
                        explicit = s;
                        valueIndex = i + 1;
                    }
                }
            }
            List<String> candidates = explicit != null ? List.of(explicit) : inferAccessorTargets(method.name, accessor);
            for (String candidate : candidates) {
                String mapped = accessor
                        ? mapFieldOnTargets(candidate)
                        : candidate.startsWith("<") ? null : mapMethodOnTargets(candidate, method.desc);
                if (mapped == null) continue;
                if (valueIndex >= 0) {
                    ann.values.set(valueIndex, mapped);
                } else {
                    if (ann.values == null) ann.values = new ArrayList<>();
                    ann.values.add("value");
                    ann.values.add(mapped);
                }
                break;
            }
        }
    }

    private static List<String> inferAccessorTargets(String methodName, boolean accessor) {
        String[] prefixes = accessor ? new String[] {"get", "set", "is"} : new String[] {"call", "invoke"};
        for (String prefix : prefixes) {
            if (methodName.length() > prefix.length() && methodName.startsWith(prefix)
                    && Character.isUpperCase(methodName.charAt(prefix.length()))) {
                String bare = methodName.substring(prefix.length());
                String decap = Character.toLowerCase(bare.charAt(0)) + bare.substring(1);
                return List.of(decap, bare, bare.toUpperCase(java.util.Locale.ROOT));
            }
        }
        return List.of(methodName);
    }

    private String mapFieldOnTargets(String name) {
        for (String owner : targets) {
            String mapped = mappings.mapField(owner, name);
            if (mapped != null && !mapped.equals(name)) return mapped;
        }
        return null;
    }

    private String mapMethodOnTargets(String name, String desc) {
        for (String owner : targets) {
            String mapped = mappings.mapMethod(owner, name, desc);
            if (mapped != null && !mapped.equals(name)) return mapped;
        }
        return null;
    }

    private String mapOwner(String owner) {
        return owner == null ? null : mappings.mapClass(owner);
    }

    private String remapDesc(String desc) {
        try {
            return desc.startsWith("(") ? descRemapper.mapMethodDesc(desc) : descRemapper.mapDesc(desc);
        } catch (RuntimeException e) {
            return desc;
        }
    }

    private static boolean isInjector(String desc) {
        for (String pkg : INJECTOR_PACKAGES) if (desc.startsWith(pkg)) return !AT.equals(desc);
        return false;
    }

    private static boolean has(FieldNode field, String desc) {
        return contains(field.visibleAnnotations, desc) || contains(field.invisibleAnnotations, desc);
    }

    private static boolean has(MethodNode method, String desc) {
        return contains(method.visibleAnnotations, desc) || contains(method.invisibleAnnotations, desc);
    }

    private static boolean contains(List<AnnotationNode> annotations, String desc) {
        if (annotations == null) return false;
        for (AnnotationNode ann : annotations) if (desc.equals(ann.desc)) return true;
        return false;
    }

    private static String shadowPrefix(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        for (AnnotationNode ann : all(visible, invisible)) {
            if (!SHADOW.equals(ann.desc) || ann.values == null) continue;
            for (int i = 0; i + 1 < ann.values.size(); i += 2) {
                if ("prefix".equals(ann.values.get(i)) && ann.values.get(i + 1) instanceof String s) return s;
            }
        }
        return "shadow$";
    }

    private static String stripPrefix(String name, String prefix) {
        return name.startsWith(prefix) ? name.substring(prefix.length()) : name;
    }

    private static String prefixed(String mapped, String original, String prefix) {
        return original.startsWith(prefix) ? prefix + mapped : mapped;
    }

    private static List<AnnotationNode> all(List<AnnotationNode> a, List<AnnotationNode> b) {
        List<AnnotationNode> out = new ArrayList<>();
        if (a != null) out.addAll(a);
        if (b != null) out.addAll(b);
        return out;
    }

    record Selector(String owner, String name, String quantifier, String desc) {
        static Selector parse(String input) {
            if (input == null) return null;
            String s = input.replaceAll("\\s", "");
            if (s.isEmpty() || s.startsWith("/") || s.contains("->")) return null;
            String desc = null;
            int paren = s.indexOf('(');
            int colon = s.indexOf(':');
            if (paren >= 0) {
                desc = s.substring(paren);
                s = s.substring(0, paren);
            } else if (colon >= 0) {
                desc = s.substring(colon + 1);
                s = s.substring(0, colon);
            }
            String owner = null;
            int semi = s.indexOf(';');
            if (s.startsWith("L") && semi > 0) {
                owner = s.substring(1, semi).replace('.', '/');
                s = s.substring(semi + 1);
            }
            String quantifier = "";
            if (s.endsWith("*") || s.endsWith("+")) {
                quantifier = s.substring(s.length() - 1);
                s = s.substring(0, s.length() - 1);
            } else if (s.endsWith("}") && s.indexOf('{') >= 0) {
                quantifier = s.substring(s.indexOf('{'));
                s = s.substring(0, s.indexOf('{'));
            }
            return new Selector(owner, s, quantifier, desc);
        }

        Selector withOwner(String o) {
            return new Selector(o, name, quantifier, desc);
        }

        Selector withName(String n) {
            return new Selector(owner, n, quantifier, desc);
        }

        Selector withDesc(String d) {
            return new Selector(owner, name, quantifier, d);
        }

        @Override
        public String toString() {
            String o = owner != null ? "L" + owner + ";" : "";
            String d = desc == null ? "" : (desc.startsWith("(") ? desc : ":" + desc);
            return o + name + quantifier + d;
        }
    }
}
