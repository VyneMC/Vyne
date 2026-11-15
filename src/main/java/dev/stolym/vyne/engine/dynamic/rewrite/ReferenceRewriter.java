/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic.rewrite;

import dev.stolym.vyne.engine.dynamic.scanner.ReferenceReport;
import dev.stolym.vyne.engine.dynamic.synth.AutoShimSynthesizer;
import dev.stolym.vyne.util.VyneLogger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.Map;

public final class ReferenceRewriter {
    public record ShimTarget(String interOwner, String interName, String shimDesc) {}

    public static byte[] rewrite(byte[] original,
                                 Map<MojmapKey, ShimTarget> redirects) {
        if (redirects.isEmpty()) return original;
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new RewriteVisitor(writer, redirects), 0);
        return writer.toByteArray();
    }

    public record MojmapKey(String owner, String name, String desc, int opcode) {
        public static MojmapKey of(ReferenceReport.MethodRef mr) {
            return new MojmapKey(mr.owner, mr.name, mr.desc, mr.opcode);
        }
    }

    private static final class RewriteVisitor extends ClassVisitor {
        private final Map<MojmapKey, ShimTarget> redirects;
        private int rewriteCount;

        RewriteVisitor(ClassVisitor delegate, Map<MojmapKey, ShimTarget> redirects) {
            super(Opcodes.ASM9, delegate);
            this.redirects = redirects;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);

            try {
                dev.stolym.vyne.mappings.MappingComposer.ComposedMappings
                    .recordClassHierarchy(name, superName, interfaces);
            } catch (Throwable ignored) {}
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                                         String signature, String[] exceptions) {
            MethodVisitor base = super.visitMethod(access, name, desc, signature, exceptions);
            return base == null ? null : new RewriteMethod(base);
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
            if (rewriteCount > 0) {
                VyneLogger.debug("[DynamicEngine] Rewrote " + rewriteCount
                    + " extension-call site(s) in one class");
            }
        }

        private final class RewriteMethod extends MethodVisitor {
            RewriteMethod(MethodVisitor delegate) { super(Opcodes.ASM9, delegate); }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name,
                                        String desc, boolean itf) {
                ShimTarget tgt = redirects.get(new MojmapKey(owner, name, desc, opcode));
                if (tgt == null) {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                    return;
                }

                String shimOwner = AutoShimSynthesizer.shimInternalFor(tgt.interOwner);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, shimOwner,
                    tgt.interName, tgt.shimDesc, false);
                rewriteCount++;
            }
        }
    }

    public static Map<MojmapKey, ShimTarget> emptyMap() {
        return new HashMap<>();
    }
}
