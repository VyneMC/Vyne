/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic.rewrite;

import dev.stolym.vyne.engine.dynamic.asm.FrameSafeClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class ServiceLoaderClassLoaderGuardPatch {
    private static final String SERVICE_LOADER = "java/util/ServiceLoader";
    private static final String LOAD_NAME = "load";
    private static final String LOAD_DESC = "(Ljava/lang/Class;Ljava/lang/ClassLoader;)Ljava/util/ServiceLoader;";
    private static final String CLASSLOADER_DESC = "Ljava/lang/ClassLoader;";

    private ServiceLoaderClassLoaderGuardPatch() {}

    public static byte[] patch(byte[] classfile) {
        ClassReader reader = new ClassReader(classfile);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.SKIP_FRAMES);
        boolean changed = false;
        for (MethodNode mn : node.methods) {
            if (patchMethod(mn)) {
                changed = true;
            }
        }
        if (!changed) {
            return classfile;
        }
        ClassWriter writer = new FrameSafeClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean isServiceLoaderLoad(MethodInsnNode min) {
        return min.getOpcode() == Opcodes.INVOKESTATIC
            && SERVICE_LOADER.equals(min.owner)
            && LOAD_NAME.equals(min.name)
            && LOAD_DESC.equals(min.desc);
    }

    private static AbstractInsnNode skipNoise(AbstractInsnNode insn) {
        AbstractInsnNode cur = insn;
        while (cur != null) {
            if (cur instanceof LabelNode || cur instanceof LineNumberNode) {
                cur = cur.getNext();
                continue;
            }
            if (cur instanceof FrameNode) {
                cur = cur.getNext();
                continue;
            }
            if (cur instanceof InsnNode in && in.getOpcode() == Opcodes.NOP) {
                cur = cur.getNext();
                continue;
            }
            break;
        }
        return cur;
    }

    private static boolean patchMethod(MethodNode mn) {
        InsnList insns = mn.instructions;
        boolean changed = false;
        AbstractInsnNode insn = insns.getFirst();
        while (insn != null) {
            AbstractInsnNode next = insn.getNext();
            if (insn instanceof FieldInsnNode fin
                && fin.getOpcode() == Opcodes.GETSTATIC
                && CLASSLOADER_DESC.equals(fin.desc)) {
                AbstractInsnNode afterNoise = skipNoise(fin.getNext());
                if (afterNoise instanceof MethodInsnNode min && isServiceLoaderLoad(min)) {
                    AbstractInsnNode resume = min.getNext();
                    replaceWithGuardedCall(insns, fin, min);
                    changed = true;
                    insn = resume;
                    continue;
                }
            }
            insn = next;
        }
        return changed;
    }

    private static void replaceWithGuardedCall(InsnList insns,
                                               FieldInsnNode getStatic,
                                               MethodInsnNode load) {
        String owner = getStatic.owner;
        LabelNode ok = new LabelNode();
        InsnList inject = new InsnList();
        inject.add(new FieldInsnNode(Opcodes.GETSTATIC, getStatic.owner, getStatic.name, getStatic.desc));
        inject.add(new InsnNode(Opcodes.DUP));
        inject.add(new JumpInsnNode(Opcodes.IFNONNULL, ok));
        inject.add(new InsnNode(Opcodes.POP));
        inject.add(new LdcInsnNode(Type.getObjectType(owner)));
        inject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class",
            "getClassLoader", "()Ljava/lang/ClassLoader;", false));
        inject.add(ok);
        inject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SERVICE_LOADER,
            LOAD_NAME, LOAD_DESC, false));

        AbstractInsnNode anchorAfter = load.getNext();
        AbstractInsnNode cur = getStatic;
        while (true) {
            AbstractInsnNode n = cur.getNext();
            insns.remove(cur);
            if (cur == load) {
                break;
            }
            cur = n;
        }
        if (anchorAfter != null) {
            insns.insertBefore(anchorAfter, inject);
        } else {
            insns.add(inject);
        }
    }
}
