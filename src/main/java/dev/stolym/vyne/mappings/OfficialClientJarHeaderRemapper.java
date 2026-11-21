/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.mappings;

import java.util.Map;
import java.util.Objects;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

public final class OfficialClientJarHeaderRemapper {
    private OfficialClientJarHeaderRemapper() {}

    public static byte[] remap(byte[] classfile, String interInternalName,
            Map<String, String> officialToInter) {
        if (classfile == null || classfile.length == 0
                || interInternalName == null || interInternalName.isEmpty()
                || officialToInter == null || officialToInter.isEmpty()) {
            return classfile;
        }
        ClassReader cr = new ClassReader(classfile);
        ClassWriter cw = new ClassWriter(cr, 0);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public void visit(int version, int access, String name, String signature,
                    String superName, String[] interfaces) {
                String sn = mapOfficial(superName, officialToInter);
                String[] itf = mapOfficialInterfaces(interfaces, officialToInter);
                super.visit(version, access, interInternalName, null, sn, itf);
            }
        }, ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }

    private static String mapOfficial(String internal, Map<String, String> o2i) {
        if (internal == null) {
            return null;
        }
        if (internal.startsWith("java/")
                || internal.startsWith("javax/")
                || internal.startsWith("jdk/")
                || internal.startsWith("sun/")
                || internal.startsWith("com/sun/")) {
            return internal;
        }
        String hit = o2i.get(internal);
        if (hit != null) {
            return hit;
        }
        if (internal.startsWith("net/minecraft/class_")) {
            return internal;
        }
        return internal;
    }

    private static String[] mapOfficialInterfaces(String[] interfaces,
            Map<String, String> o2i) {
        if (interfaces == null || interfaces.length == 0) {
            return interfaces;
        }
        String[] out = new String[interfaces.length];
        boolean changed = false;
        for (int i = 0; i < interfaces.length; i++) {
            String orig = interfaces[i];
            String m = mapOfficial(orig, o2i);
            out[i] = m;
            if (!Objects.equals(orig, m)) {
                changed = true;
            }
        }
        return changed ? out : interfaces;
    }
}
