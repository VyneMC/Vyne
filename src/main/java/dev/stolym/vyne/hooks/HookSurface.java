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
                         String[] exceptions, boolean overrides) {}

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
                        p[5].isEmpty() ? null : p[5], p[6].isEmpty() ? null : p[6].split(" "), "override".equals(p[7]) || "default".equals(p[7]));
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
                    node.access = widenTo(node.access, member.access());
                    changed = true;
                }
                case "inner-access" -> {
                    for (InnerClassNode inner : node.innerClasses) {
                        if (inner.name.equals(member.name())) {
                            inner.access = widenTo(inner.access, member.access());
                            changed = true;
                        }
                    }
                }
                case "field-access" -> {
                    for (FieldNode field : node.fields) {
                        if (field.name.equals(member.name()) && field.desc.equals(member.desc())) {
                            field.access = widenTo(field.access, member.access());
                            changed = true;
                        }
                    }
                }
                case "method-access" -> {
                    for (MethodNode method : node.methods) {
                        if (method.name.equals(member.name()) && method.desc.equals(member.desc())) {
                            method.access = widenTo(method.access, member.access());
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
                            method.access = widenTo(method.access, member.access()) & ~Opcodes.ACC_ABSTRACT;
                            method.instructions = body.instructions;
                            method.maxLocals = body.maxLocals;
                            method.maxStack = body.maxStack;
                            changed = true;
                        }
                    }
                }
                case "bridge" -> {
                    if (node.methods.stream().noneMatch(m -> m.name.equals(member.name()) && m.desc.equals(member.desc()))) {
                        node.methods.add(bridge(node, member));
                        changed = true;
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
                    if (member.name().equals("<clinit>")) continue;
                    if (!stubMethods && member.overrides()) continue;
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
            int pending = 0;
            for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions) {
                if (insn.getOpcode() == Opcodes.NEW && ((TypeInsnNode) insn).desc.equals(from)) {
                    pending++;
                } else if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                        && call.name.equals("<init>") && call.owner.equals(from)) {
                    if (pending > 0) {
                        pending--;
                    } else {
                        call.owner = to;
                        break;
                    }
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

    private static int widenTo(int current, int target) {
        int mask = Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE;
        int out = (current & ~mask) | (target & mask);
        if ((target & Opcodes.ACC_FINAL) == 0) out &= ~Opcodes.ACC_FINAL;
        return out;
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

    private static MethodNode bridge(ClassNode owner, Member member) {
        String target = member.signature();
        int paren = target.indexOf('(');
        String name = target.substring(0, paren);
        String desc = target.substring(paren);
        boolean isInterface = (owner.access & Opcodes.ACC_INTERFACE) != 0;
        MethodNode method = new MethodNode(member.access(), member.name(), member.desc(), null, null);
        method.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
        Type[] from = Type.getArgumentTypes(member.desc());
        Type[] to = Type.getArgumentTypes(desc);
        int slot = 1;
        for (int i = 0; i < from.length; i++) {
            method.instructions.add(new org.objectweb.asm.tree.VarInsnNode(from[i].getOpcode(Opcodes.ILOAD), slot));
            if (to[i].getSort() >= Type.ARRAY && !to[i].equals(from[i])) {
                method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, to[i].getInternalName()));
            }
            slot += from[i].getSize();
        }
        method.instructions.add(new MethodInsnNode(isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL,
                owner.name, name, desc, isInterface));
        method.instructions.add(new InsnNode(Type.getReturnType(member.desc()).getOpcode(Opcodes.IRETURN)));
        method.maxLocals = slot;
        method.maxStack = slot + 1;
        return method;
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
