/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HookSurface {
    public record Member(String owner, String kind, String name, String desc, int access, String signature,
                         String[] exceptions, boolean overridesDefault) {}

    private record AccessRule(String access, boolean unfinal, String member) {}

    private final Map<String, List<Member>> members = new HashMap<>();
    private final Map<String, List<AccessRule>> access = new HashMap<>();
    private final java.util.Set<String> outers = new java.util.HashSet<>();

    public static HookSurface load(InputStream surface, InputStream accessTransformers) throws IOException {
        HookSurface out = new HookSurface();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(surface, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("class\t")) continue;
                String[] p = line.split("\t", -1);
                Member member = new Member(p[0], p[1], p[2], p[3], Integer.parseInt(p[4]),
                        p[5].isEmpty() ? null : p[5], p[6].isEmpty() ? null : p[6].split(" "), "default".equals(p[7]));
                out.members.computeIfAbsent(member.owner(), k -> new ArrayList<>()).add(member);
            }
        }
        if (accessTransformers != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(accessTransformers, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int hash = line.indexOf('#');
                    if (hash >= 0) line = line.substring(0, hash);
                    String[] p = line.trim().split("\\s+");
                    if (p.length < 2) continue;
                    String member = p.length > 2 ? p[2] : null;
                    String owner = p[1].replace('.', '/');
                    out.access.computeIfAbsent(owner, k -> new ArrayList<>())
                            .add(new AccessRule(p[0].replace("-f", "").replace("+f", ""), p[0].endsWith("-f"), member));
                    for (int i = owner.indexOf('$'); i > 0; i = owner.indexOf('$', i + 1)) out.outers.add(owner.substring(0, i));
                }
            }
        }
        return out;
    }

    public boolean touches(String internalName) {
        return members.containsKey(internalName) || access.containsKey(internalName) || outers.contains(internalName);
    }

    public List<Member> members(String internalName) {
        return members.getOrDefault(internalName, List.of());
    }

    public int memberCount() {
        int count = 0;
        for (List<Member> list : members.values()) count += list.size();
        return count;
    }

    public boolean apply(ClassNode node, boolean stubMethods) {
        boolean changed = applyAccess(node);
        for (Member member : members(node.name)) {
            switch (member.kind()) {
                case "extends" -> {
                    if (member.desc().equals(node.superName)) {
                        changeSuperclass(node, member.desc(), member.name());
                        if (member.signature() != null) node.signature = member.signature();
                        changed = true;
                    }
                }
                case "access" -> {
                    node.access = member.access();
                    changed = true;
                }
                case "inner-access" -> {
                    for (InnerClassNode inner : node.innerClasses) {
                        if (inner.name.equals(member.name())) {
                            inner.access = member.access();
                            changed = true;
                        }
                    }
                }
                case "field-access" -> {
                    for (FieldNode field : node.fields) {
                        if (field.name.equals(member.name()) && field.desc.equals(member.desc())) {
                            field.access = member.access();
                            changed = true;
                        }
                    }
                }
                case "method-access" -> {
                    for (MethodNode method : node.methods) {
                        if (method.name.equals(member.name()) && method.desc.equals(member.desc())) {
                            method.access = member.access();
                            changed = true;
                        }
                    }
                }
                case "method-concrete" -> {
                    for (MethodNode method : node.methods) {
                        if (method.name.equals(member.name()) && method.desc.equals(member.desc())
                                && (method.access & Opcodes.ACC_ABSTRACT) != 0) {
                            MethodNode body = stub(node, new Member(member.owner(), "method", member.name(), member.desc(),
                                    member.access(), method.signature, null, false));
                            method.access = member.access();
                            method.instructions = body.instructions;
                            method.maxLocals = body.maxLocals;
                            method.maxStack = body.maxStack;
                            changed = true;
                        }
                    }
                }
                case "implements" -> {
                    if (!node.interfaces.contains(member.name())) {
                        node.interfaces.add(member.name());
                        if (member.signature() != null) node.signature = member.signature();
                        changed = true;
                    }
                }
                case "field" -> {
                    if (node.fields.stream().noneMatch(f -> f.name.equals(member.name()))) {
                        node.fields.add(new FieldNode(member.access(), member.name(), member.desc(), member.signature(), null));
                        changed = true;
                    }
                }
                case "method" -> {
                    if (!stubMethods && member.overridesDefault()) continue;
                    if (node.methods.stream().noneMatch(m -> m.name.equals(member.name()) && m.desc.equals(member.desc()))) {
                        node.methods.add(stub(node, member));
                        changed = true;
                    }
                }
                default -> {
                }
            }
        }
        return changed;
    }

    private static void changeSuperclass(ClassNode node, String from, String to) {
        node.superName = to;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("<init>")) continue;
            for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                        && call.name.equals("<init>") && call.owner.equals(from)) {
                    call.owner = to;
                }
            }
        }
    }

    private boolean applyAccess(ClassNode node) {
        boolean changed = false;
        for (AccessRule rule : access.getOrDefault(node.name, List.of())) {
            if (rule.member() == null) {
                node.access = widen(node.access, rule);
                changed = true;
            } else if (rule.member().equals("*")) {
                for (FieldNode field : node.fields) field.access = widen(field.access, rule);
                changed = true;
            } else if (rule.member().equals("*()")) {
                for (MethodNode method : node.methods) {
                    if (!method.name.startsWith("<")) method.access = widen(method.access, rule);
                }
                changed = true;
            } else if (rule.member().contains("(")) {
                int paren = rule.member().indexOf('(');
                String name = rule.member().substring(0, paren);
                String desc = rule.member().substring(paren);
                for (MethodNode method : node.methods) {
                    if (method.name.equals(name) && method.desc.equals(desc)) {
                        method.access = widen(method.access, rule);
                        changed = true;
                    }
                }
            } else {
                for (FieldNode field : node.fields) {
                    if (field.name.equals(rule.member())) {
                        field.access = widen(field.access, rule);
                        changed = true;
                    }
                }
            }
        }
        for (InnerClassNode inner : node.innerClasses) {
            for (AccessRule rule : access.getOrDefault(inner.name, List.of())) {
                if (rule.member() == null) {
                    inner.access = widen(inner.access, rule);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static int widen(int current, AccessRule rule) {
        int visibility = current & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
        int wanted = switch (rule.access()) {
            case "public" -> Opcodes.ACC_PUBLIC;
            case "protected" -> visibility == Opcodes.ACC_PUBLIC ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PROTECTED;
            default -> visibility == Opcodes.ACC_PRIVATE ? 0 : visibility;
        };
        int out = (current & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE)) | wanted;
        if (rule.unfinal()) out &= ~Opcodes.ACC_FINAL;
        return out;
    }

    private static MethodNode stub(ClassNode owner, Member member) {
        String[] exceptions = member.exceptions() == null ? null : Arrays.copyOf(member.exceptions(), member.exceptions().length);
        MethodNode method = new MethodNode(member.access(), member.name(), member.desc(), member.signature(), exceptions);
        if ((member.access() & Opcodes.ACC_ABSTRACT) != 0 || (member.access() & Opcodes.ACC_NATIVE) != 0) return method;
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/lang/UnsupportedOperationException"));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new LdcInsnNode("Vyne hook not implemented yet: " + owner.name.replace('/', '.') + "."
                + member.name() + member.desc()));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/UnsupportedOperationException",
                "<init>", "(Ljava/lang/String;)V", false));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));
        int locals = Type.getArgumentsAndReturnSizes(member.desc()) >> 2;
        if ((member.access() & Opcodes.ACC_STATIC) != 0) locals--;
        method.maxLocals = Math.max(locals, 1);
        method.maxStack = 3;
        return method;
    }
}
