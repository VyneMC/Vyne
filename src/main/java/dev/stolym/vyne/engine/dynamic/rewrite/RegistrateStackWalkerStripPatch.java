/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.engine.dynamic.rewrite;

import dev.stolym.vyne.engine.dynamic.asm.FrameSafeClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class RegistrateStackWalkerStripPatch {
    private RegistrateStackWalkerStripPatch() {}

    public static byte[] patch(byte[] classfile) {
        ClassReader reader = new ClassReader(classfile);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.SKIP_FRAMES);
        boolean changed = false;
        for (MethodNode mn : node.methods) {
            if (patchRegistrateMethod(node, mn)) {
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

    private static boolean patchRegistrateMethod(ClassNode owner, MethodNode mn) {
        if (!"registrate".equals(mn.name) || mn.desc == null || mn.desc.length() < 4) {
            return false;
        }
        if (mn.desc.charAt(0) != '(' || mn.desc.charAt(1) != ')') {
            return false;
        }
        String ret = mn.desc.substring(mn.desc.indexOf(')') + 1);
        if (ret.length() < 3 || ret.charAt(0) != 'L') {
            return false;
        }
        if (!ret.contains("Registrate")) {
            return false;
        }
        if (!usesStackWalkerGetCaller(mn)) {
            return false;
        }
        FieldNode target = findRegistrateField(owner, ret);
        if (target == null) {
            return false;
        }
        mn.instructions.clear();
        if (mn.tryCatchBlocks != null) {
            mn.tryCatchBlocks.clear();
        }
        if (mn.localVariables != null) {
            mn.localVariables.clear();
        }
        InsnList il = new InsnList();
        il.add(new org.objectweb.asm.tree.FieldInsnNode(Opcodes.GETSTATIC,
            owner.name, target.name, target.desc));
        il.add(new InsnNode(Opcodes.ARETURN));
        mn.instructions.add(il);
        mn.maxStack = 0;
        mn.maxLocals = 0;
        return true;
    }

    private static boolean usesStackWalkerGetCaller(MethodNode mn) {
        for (AbstractInsnNode insn = mn.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode min
                && "getCallerClass".equals(min.name)
                && "java/lang/StackWalker".equals(min.owner)) {
                return true;
            }
        }
        return false;
    }

    private static FieldNode findRegistrateField(ClassNode owner, String returnDesc) {
        for (FieldNode fn : owner.fields) {
            if ((Opcodes.ACC_STATIC & fn.access) == 0) {
                continue;
            }
            if (!"REGISTRATE".equals(fn.name)) {
                continue;
            }
            if (fn.desc.equals(returnDesc)) {
                return fn;
            }
        }
        return null;
    }
}
