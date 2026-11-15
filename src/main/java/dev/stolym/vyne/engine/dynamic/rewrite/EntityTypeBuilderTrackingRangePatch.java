/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.engine.dynamic.rewrite;

import dev.stolym.vyne.engine.dynamic.asm.FrameSafeClassWriter;
import dev.stolym.vyne.mappings.MappingRegistry;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class EntityTypeBuilderTrackingRangePatch {
    private static final String NEO_TRACKING_RANGE = "setTrackingRange";
    private static final String NEO_UPDATE_INTERVAL = "setUpdateInterval";
    private static final String NEO_RECEIVE_VELOCITY = "setShouldReceiveVelocityUpdates";

    private EntityTypeBuilderTrackingRangePatch() {}

    public static byte[] patch(byte[] classfile) {
        if (classfile == null || classfile.length == 0) {
            return classfile;
        }
        ClassReader cr = new ClassReader(classfile);
        ClassWriter cw = new FrameSafeClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                return base == null ? null : new TrackingRangeMv(base);
            }
        }, ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }

    private static final class TrackingRangeMv extends MethodVisitor {
        TrackingRangeMv(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                boolean isInterface) {
            if (NEO_TRACKING_RANGE.equals(name) && isEntityBuilderInvoke(opcode, owner, name, descriptor)) {
                super.visitMethodInsn(opcode, owner, vanillaClientTrackingName(), descriptor, isInterface);
                return;
            }
            if (NEO_UPDATE_INTERVAL.equals(name) && isEntityBuilderInvoke(opcode, owner, name, descriptor)) {
                super.visitMethodInsn(opcode, owner, vanillaUpdateIntervalName(), descriptor, isInterface);
                return;
            }
            if (NEO_RECEIVE_VELOCITY.equals(name) && isEntityBuilderInvoke(opcode, owner, name, descriptor)) {
                super.visitInsn(Opcodes.POP);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }

    private static boolean isEntityBuilderInvoke(int opcode, String owner, String name, String desc) {
        if (opcode != Opcodes.INVOKEVIRTUAL && opcode != Opcodes.INVOKEINTERFACE) {
            return false;
        }
        if (owner == null || desc == null
                || (!NEO_TRACKING_RANGE.equals(name)
                    && !NEO_UPDATE_INTERVAL.equals(name)
                    && !NEO_RECEIVE_VELOCITY.equals(name))) {
            return false;
        }
        if ((NEO_TRACKING_RANGE.equals(name) || NEO_UPDATE_INTERVAL.equals(name)) && !desc.startsWith("(I)")) {
            return false;
        }
        if (NEO_RECEIVE_VELOCITY.equals(name) && !desc.startsWith("(Z)")) {
            return false;
        }
        return owner.endsWith("EntityType$Builder")
            || "net/minecraft/class_1299$class_1300".equals(owner);
    }

    private static String vanillaClientTrackingName() {
        if (!MappingRegistry.isReady()) {
            return "clientTrackingRange";
        }
        String mapped = MappingRegistry.get().mapMethod(
                "net/minecraft/world/entity/EntityType$Builder",
                "clientTrackingRange",
                "(I)Lnet/minecraft/world/entity/EntityType$Builder;");
        return mapped != null ? mapped : "clientTrackingRange";
    }

    private static String vanillaUpdateIntervalName() {
        if (!MappingRegistry.isReady()) {
            return "updateInterval";
        }
        String mapped = MappingRegistry.get().mapMethod(
                "net/minecraft/world/entity/EntityType$Builder",
                "updateInterval",
                "(I)Lnet/minecraft/world/entity/EntityType$Builder;");
        return mapped != null ? mapped : "updateInterval";
    }
}
