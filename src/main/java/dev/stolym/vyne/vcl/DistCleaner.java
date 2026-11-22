/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class DistCleaner {
    private static final String ONLY_IN = "Lnet/neoforged/api/distmarker/OnlyIn;";
    private static final String ONLY_INS = "Lnet/neoforged/api/distmarker/OnlyIns;";
    private static final byte[] MARKER = "Lnet/neoforged/api/distmarker/OnlyIn".getBytes(StandardCharsets.UTF_8);

    private DistCleaner() {}

    static byte[] clean(String dottedName, byte[] bytes, boolean dedicatedServer) {
        if (!contains(bytes)) return bytes;
        String other = dedicatedServer ? "CLIENT" : "DEDICATED_SERVER";
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (other.equals(dist(node.visibleAnnotations)) || other.equals(dist(node.invisibleAnnotations))) {
            throw new NoClassDefFoundError(dottedName + " is @OnlyIn(" + other + ") and cannot be loaded on the "
                    + (dedicatedServer ? "dedicated server" : "client") + "; the mod must guard it with a dist check");
        }
        boolean changed = removeInterfaces(node, other);
        changed |= node.fields.removeIf(field -> onlyOn(other, field.visibleAnnotations, field.invisibleAnnotations));
        Set<MethodNode> removed = new HashSet<>();
        for (MethodNode method : node.methods) {
            if (onlyOn(other, method.visibleAnnotations, method.invisibleAnnotations)) removed.add(method);
        }
        if (removed.isEmpty() && !changed) return bytes;
        List<MethodNode> pending = new ArrayList<>(removed);
        while (!pending.isEmpty()) {
            MethodNode method = pending.remove(pending.size() - 1);
            for (MethodNode lambda : lambdasUsedBy(node, method)) {
                if (!removed.contains(lambda) && !usedElsewhere(node, lambda, removed)) {
                    removed.add(lambda);
                    pending.add(lambda);
                }
            }
        }
        node.methods.removeAll(removed);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean removeInterfaces(ClassNode node, String other) {
        Set<String> drop = new HashSet<>();
        for (List<AnnotationNode> annotations : List.of(nonNull(node.visibleAnnotations), nonNull(node.invisibleAnnotations))) {
            for (AnnotationNode annotation : annotations) {
                if (ONLY_INS.equals(annotation.desc)) {
                    for (Object entry : values(annotation, "value")) {
                        if (entry instanceof AnnotationNode inner) addInterface(inner, other, drop);
                    }
                } else if ("Lnet/neoforged/api/distmarker/OnlyIn$Interface;".equals(annotation.desc)) {
                    addInterface(annotation, other, drop);
                }
            }
        }
        if (drop.isEmpty()) return false;
        return node.interfaces.removeIf(drop::contains);
    }

    private static void addInterface(AnnotationNode annotation, String other, Set<String> drop) {
        Object dist = value(annotation, "value");
        Object type = value(annotation, "_interface");
        if (dist instanceof String[] e && other.equals(e[1]) && type instanceof Type t) drop.add(t.getInternalName());
    }

    private static List<MethodNode> lambdasUsedBy(ClassNode node, MethodNode method) {
        List<MethodNode> lambdas = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (!(insn instanceof InvokeDynamicInsnNode indy)) continue;
            for (Object arg : indy.bsmArgs) {
                if (!(arg instanceof Handle handle) || !handle.getOwner().equals(node.name)) continue;
                for (MethodNode candidate : node.methods) {
                    if ((candidate.access & Opcodes.ACC_SYNTHETIC) != 0 && candidate.name.equals(handle.getName())
                            && candidate.desc.equals(handle.getDesc())) {
                        lambdas.add(candidate);
                    }
                }
            }
        }
        return lambdas;
    }

    private static boolean usedElsewhere(ClassNode node, MethodNode lambda, Set<MethodNode> removed) {
        for (MethodNode method : node.methods) {
            if (method == lambda || removed.contains(method)) continue;
            if (lambdasUsedBy(node, method).contains(lambda)) return true;
        }
        return false;
    }

    private static boolean onlyOn(String other, List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        return other.equals(dist(visible)) || other.equals(dist(invisible));
    }

    private static String dist(List<AnnotationNode> annotations) {
        if (annotations == null) return null;
        for (AnnotationNode annotation : annotations) {
            if (ONLY_IN.equals(annotation.desc) && value(annotation, "value") instanceof String[] e) return e[1];
        }
        return null;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) return null;
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static List<?> values(AnnotationNode annotation, String key) {
        return value(annotation, key) instanceof List<?> list ? list : List.of();
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list != null ? list : List.of();
    }

    private static boolean contains(byte[] bytes) {
        outer:
        for (int i = 0; i <= bytes.length - MARKER.length; i++) {
            for (int j = 0; j < MARKER.length; j++) {
                if (bytes[i + j] != MARKER[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
