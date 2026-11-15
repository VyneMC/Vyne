/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic.scanner;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ModReferenceScanner {
    private static final int ASM_API = Opcodes.ASM9;

    private ModReferenceScanner() {}

    public static ReferenceReport scan(Path modJar) throws IOException {
        ReferenceReport report = new ReferenceReport();
        report.jarPathHint = modJar.toString();
        report.jarSha256 = sha256(modJar);

        try (ZipFile zf = new ZipFile(modJar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                if (!name.endsWith(".class")) continue;

                String simple = name.substring(name.lastIndexOf('/') + 1);
                if ("module-info.class".equals(simple) || "package-info.class".equals(simple)) {
                    continue;
                }
                String internal = name.substring(0, name.length() - ".class".length());
                report.ownedClasses.add(internal);
                report.totalClassCount++;
                try (InputStream is = zf.getInputStream(e)) {
                    new ClassReader(is).accept(new ScanVisitor(report, internal),
                        ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
            }
        }
        return report;
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(file);
                 DigestInputStream dis = new DigestInputStream(is, md)) {
                byte[] buf = new byte[8192];
                while (dis.read(buf) != -1) {  }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException nse) {
            throw new IOException("SHA-256 not available", nse);
        }
    }

    private static final class ScanVisitor extends ClassVisitor {
        private final ReferenceReport report;
        private final String selfInternal;

        ScanVisitor(ReferenceReport report, String selfInternal) {
            super(ASM_API);
            this.report = report;
            this.selfInternal = selfInternal;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            if (superName != null && !superName.equals(selfInternal)) {
                ReferenceReport.ClassUsage u = report.usageFor(superName);
                u.usedAsSupertype = true;
            }
            if (interfaces != null) {
                for (String itf : interfaces) {
                    ReferenceReport.ClassUsage u = report.usageFor(itf);
                    u.usedAsInterface = true;
                }
            }
            int extra = interfaces != null ? interfaces.length : 0;
            String[] arr = new String[1 + extra];
            arr[0] = superName;
            if (extra > 0) System.arraycopy(interfaces, 0, arr, 1, extra);
            report.ownedClassHierarchy.put(selfInternal, arr);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                                         String signature, String[] exceptions) {
            recordDescriptorReferences(desc);
            if (exceptions != null) for (String ex : exceptions) report.usageFor(ex);
            return new ScanMethodVisitor();
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc,
                                       String signature, Object value) {
            recordDescriptorReferences(desc);
            return null;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            Type t = Type.getType(desc);
            if (t.getSort() == Type.OBJECT) report.usageFor(t.getInternalName());
            return null;
        }

        private void recordDescriptorReferences(String desc) {
            if (desc == null || desc.isEmpty()) return;
            if (desc.charAt(0) == '(') {
                Type mt = Type.getMethodType(desc);
                for (Type arg : mt.getArgumentTypes()) noteSigType(arg);
                noteSigType(mt.getReturnType());
            } else {
                noteSigType(Type.getType(desc));
            }
        }

        private void noteSigType(Type t) {
            while (t.getSort() == Type.ARRAY) t = t.getElementType();
            if (t.getSort() != Type.OBJECT) return;
            String owner = t.getInternalName();
            if (owner.equals(selfInternal)) return;
            ReferenceReport.ClassUsage u = report.usageFor(owner);
            u.usedInSignature = true;
        }

        private final class ScanMethodVisitor extends MethodVisitor {
            ScanMethodVisitor() { super(ASM_API); }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name,
                                        String desc, boolean itf) {
                if (owner == null || owner.startsWith("[")) return; 
                if (owner.equals(selfInternal)) return;
                ReferenceReport.ClassUsage u = report.usageFor(owner);
                ReferenceReport.MethodRef ref =
                    new ReferenceReport.MethodRef(owner, name, desc, itf, opcode);
                if (u.methods.add(ref)) {
                    ref.callSites = 1;
                } else {
                    for (ReferenceReport.MethodRef existing : u.methods) {
                        if (existing.equals(ref)) {
                            existing.callSites++;
                            break;
                        }
                    }
                }
                if ("<init>".equals(name)) u.newCtorDescs.add(desc);
                recordDescriptorReferences(desc);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                if (owner == null || owner.equals(selfInternal)) return;
                ReferenceReport.ClassUsage u = report.usageFor(owner);
                boolean isStatic = opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC;
                u.fields.add(new ReferenceReport.FieldRef(owner, name, desc, isStatic));
                recordDescriptorReferences(desc);
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                if (type == null || type.startsWith("[")) return;
                if (type.equals(selfInternal)) return;
                ReferenceReport.ClassUsage u = report.usageFor(type);
                if (opcode == Opcodes.NEW) u.newCtorDescs.add("()V"); 
                else if (opcode == Opcodes.CHECKCAST) u.usedAsCheckcast = true;
                else if (opcode == Opcodes.INSTANCEOF) u.usedAsInstanceof = true;
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String desc, Handle bsm,
                                               Object... bsmArgs) {
                recordDescriptorReferences(desc);
                if (bsm != null && bsm.getOwner() != null && !bsm.getOwner().equals(selfInternal)) {
                    report.usageFor(bsm.getOwner());
                }

                if (bsmArgs != null) {
                    for (Object arg : bsmArgs) {
                        if (arg instanceof Handle h
                                && h.getOwner() != null
                                && !h.getOwner().equals(selfInternal)) {
                            ReferenceReport.ClassUsage u = report.usageFor(h.getOwner());
                            ReferenceReport.MethodRef mr = new ReferenceReport.MethodRef(
                                h.getOwner(), h.getName(), h.getDesc(),
                                h.getTag() == Opcodes.H_INVOKEINTERFACE,
                                tagToOpcode(h.getTag()));
                            u.methods.add(mr);
                            recordDescriptorReferences(h.getDesc());
                        } else if (arg instanceof Type t) {
                            noteSigType(t);
                        }
                    }
                }
            }

            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof Type t) noteSigType(t);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                Type t = Type.getType(desc);
                if (t.getSort() == Type.OBJECT) report.usageFor(t.getInternalName());
                return null;
            }

            private int tagToOpcode(int tag) {
                return switch (tag) {
                    case Opcodes.H_INVOKEVIRTUAL -> Opcodes.INVOKEVIRTUAL;
                    case Opcodes.H_INVOKESTATIC -> Opcodes.INVOKESTATIC;
                    case Opcodes.H_INVOKESPECIAL -> Opcodes.INVOKESPECIAL;
                    case Opcodes.H_INVOKEINTERFACE -> Opcodes.INVOKEINTERFACE;
                    case Opcodes.H_NEWINVOKESPECIAL -> Opcodes.INVOKESPECIAL;
                    default -> Opcodes.INVOKEVIRTUAL;
                };
            }
        }
    }
}
