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
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class RegistrateLenientErrorsPatch {
    private static final String TARGET = "com/tterrag/registrate/AbstractRegistrate";

    private RegistrateLenientErrorsPatch() {}

    public static byte[] patch(String internalName, byte[] classfile) {
        if (!TARGET.equals(internalName) || classfile == null || classfile.length == 0) {
            return classfile;
        }
        ClassReader reader = new ClassReader(classfile);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.SKIP_FRAMES);
        boolean changed = false;
        for (MethodNode mn : node.methods) {
            if (!"onRegister".equals(mn.name)) {
                continue;
            }
            for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof FieldInsnNode fin
                        && fin.getOpcode() == Opcodes.GETFIELD
                        && "skipErrors".equals(fin.name)
                        && "Z".equals(fin.desc)) {
                    InsnList repl = new InsnList();
                    repl.add(new InsnNode(Opcodes.POP));
                    repl.add(new InsnNode(Opcodes.ICONST_1));
                    mn.instructions.insert(insn, repl);
                    mn.instructions.remove(insn);
                    changed = true;
                }
            }
        }
        if (!changed) {
            return classfile;
        }
        ClassWriter writer = new FrameSafeClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        node.accept(writer);
        return writer.toByteArray();
    }
}
