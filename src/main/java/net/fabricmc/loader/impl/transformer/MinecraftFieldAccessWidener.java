/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */
package net.fabricmc.loader.impl.transformer;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class MinecraftFieldAccessWidener extends ClassVisitor {
    private static final int VISIBILITY_MASK = Opcodes.ACC_PUBLIC
            | Opcodes.ACC_PRIVATE
            | Opcodes.ACC_PROTECTED;

    MinecraftFieldAccessWidener(int api, ClassVisitor cv) {
        super(api, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        access = (access & ~VISIBILITY_MASK) | Opcodes.ACC_PUBLIC;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor,
                                   String signature, Object value) {
        int widened = (access & ~VISIBILITY_MASK) | Opcodes.ACC_PUBLIC;
        return super.visitField(widened, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                     String signature, String[] exceptions) {
        if (!"<clinit>".equals(name)) {
            access = (access & ~VISIBILITY_MASK) | Opcodes.ACC_PUBLIC;
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }
}
