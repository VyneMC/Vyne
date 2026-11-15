/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.engine.dynamic.rewrite;

import dev.stolym.vyne.engine.dynamic.MojmapTranslator;
import java.util.Objects;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

public final class MojmapClassHeaderRemapper {
    private MojmapClassHeaderRemapper() {}

    public static byte[] remap(byte[] classfile) {
        if (classfile == null || classfile.length == 0) {
            return classfile;
        }
        ClassReader probe = new ClassReader(classfile);
        String superName = probe.getSuperName();
        String[] interfaces = probe.getInterfaces();
        String mappedSuper = mapMc(superName);
        String[] mappedIfaces = mapMcIfaces(interfaces);
        if (Objects.equals(mappedSuper, superName) && mappedIfaces == interfaces) {
            return classfile;
        }
        ClassReader cr = new ClassReader(classfile);
        ClassWriter cw = new ClassWriter(cr, 0);
        cr.accept(new HeaderVisitor(Opcodes.ASM9, cw), ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }

    private static String mapMc(String internal) {
        if (internal == null || !internal.startsWith("net/minecraft/")) {
            return internal;
        }
        return MojmapTranslator.classInter(internal);
    }

    private static String[] mapMcIfaces(String[] interfaces) {
        if (interfaces == null || interfaces.length == 0) {
            return interfaces;
        }
        String[] out = new String[interfaces.length];
        boolean ch = false;
        for (int i = 0; i < interfaces.length; i++) {
            String m = mapMc(interfaces[i]);
            if (!m.equals(interfaces[i])) {
                ch = true;
            }
            out[i] = m;
        }
        return ch ? out : interfaces;
    }

    private static final class HeaderVisitor extends ClassVisitor {
        HeaderVisitor(int api, ClassVisitor next) {
            super(api, next);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            String sn = mapMc(superName);
            String[] itf = mapMcIfaces(interfaces);
            if (sn == superName && itf == interfaces) {
                super.visit(version, access, name, signature, superName, interfaces);
                return;
            }
            super.visit(version, access, name, signature, sn, itf);
        }
    }
}
