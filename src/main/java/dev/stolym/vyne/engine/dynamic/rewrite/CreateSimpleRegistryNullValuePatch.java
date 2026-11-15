/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.engine.dynamic.rewrite;

import dev.stolym.vyne.engine.dynamic.asm.FrameSafeClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;

public final class CreateSimpleRegistryNullValuePatch {
    private static final String MULTI_TARGET = "com/simibubi/create/impl/registry/SimpleRegistryImpl$MultiImpl";
    private static final String SINGLE_TARGET = "com/simibubi/create/impl/registry/SimpleRegistryImpl";

    private CreateSimpleRegistryNullValuePatch() {}

    public static byte[] patch(String internalName, byte[] classfile) {
        if (classfile == null || classfile.length == 0
                || (!MULTI_TARGET.equals(internalName) && !SINGLE_TARGET.equals(internalName))) {
            return classfile;
        }
        ClassReader reader = new ClassReader(classfile);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.SKIP_FRAMES);
        boolean changed = false;
        for (MethodNode mn : node.methods) {
            if (MULTI_TARGET.equals(internalName)
                    && "add".equals(mn.name)
                    && "(Ljava/lang/Object;Ljava/lang/Object;)V".equals(mn.desc)) {
                insertReturnIfSecondArgNull(mn);
                changed = true;
            } else if (SINGLE_TARGET.equals(internalName)
                    && "register".equals(mn.name)
                    && "(Ljava/lang/Object;Ljava/lang/Object;)V".equals(mn.desc)) {
                insertSingleRegistryLeniency(node.name, mn);
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

    private static void insertReturnIfSecondArgNull(MethodNode mn) {
        InsnList guard = new InsnList();
        LabelNode continueLabel = new LabelNode();
        guard.add(new VarInsnNode(Opcodes.ALOAD, 2));
        guard.add(new JumpInsnNode(Opcodes.IFNONNULL, continueLabel));
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(continueLabel);
        mn.instructions.insert(guard);
    }

    private static void insertSingleRegistryLeniency(String owner, MethodNode mn) {
        InsnList guard = new InsnList();
        LabelNode valuePresent = new LabelNode();
        LabelNode notDuplicate = new LabelNode();
        guard.add(new VarInsnNode(Opcodes.ALOAD, 2));
        guard.add(new JumpInsnNode(Opcodes.IFNONNULL, valuePresent));
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(valuePresent);
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new FieldInsnNode(Opcodes.GETFIELD, owner, "registrations", "Ljava/util/Map;"));
        guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
        guard.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/Map", "containsKey",
            "(Ljava/lang/Object;)Z", true));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, notDuplicate));
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(notDuplicate);
        mn.instructions.insert(guard);
    }
}
