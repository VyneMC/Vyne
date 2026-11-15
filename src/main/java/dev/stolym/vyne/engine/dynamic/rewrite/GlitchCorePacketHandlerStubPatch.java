/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.engine.dynamic.rewrite;

import dev.stolym.vyne.engine.dynamic.asm.FrameSafeClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class GlitchCorePacketHandlerStubPatch {
    private static final String TARGET = "glitchcore/network/PacketHandler";
    private static final String REGISTER_DESC =
        "(Lnet/minecraft/resources/ResourceLocation;Lglitchcore/network/CustomPacket;)V";

    private GlitchCorePacketHandlerStubPatch() {}

    public static byte[] patch(byte[] classfile) {
        ClassReader reader = new ClassReader(classfile);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.SKIP_FRAMES);
        if (!TARGET.equals(node.name)) {
            return classfile;
        }
        boolean changed = false;
        for (MethodNode mn : node.methods) {
            if ("register".equals(mn.name) && REGISTER_DESC.equals(mn.desc)) {
                mn.instructions.clear();
                mn.instructions.add(new InsnNode(Opcodes.RETURN));
                mn.tryCatchBlocks = null;
                mn.localVariables = null;
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
}
