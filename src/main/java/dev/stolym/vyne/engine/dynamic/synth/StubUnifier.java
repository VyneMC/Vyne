/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic.synth;

import dev.stolym.vyne.util.VyneLogger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StubUnifier {
    private StubUnifier() {}

    public static Map<String, byte[]> unify(Map<String, List<byte[]>> perName) {
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<byte[]>> entry : perName.entrySet()) {
            String name = entry.getKey();
            List<byte[]> copies = entry.getValue();
            if (copies == null || copies.isEmpty()) continue;
            if (copies.size() == 1) {
                out.put(name, copies.get(0));
                continue;
            }
            try {
                out.put(name, mergeMany(name, copies));
            } catch (Throwable t) {
                VyneLogger.warn("[DynamicEngine] Union merge failed for " + name
                    + " (" + copies.size() + " copies, keeping first): "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
                out.put(name, copies.get(0));
            }
        }
        return out;
    }

    private static byte[] mergeMany(String internalName, List<byte[]> copies) {
        final Header header = readHeader(copies.get(0));

        for (byte[] b : copies) {
            Header h = readHeader(b);
            if ((h.access & Opcodes.ACC_INTERFACE) != 0) {
                header.access = (header.access & ~Opcodes.ACC_SUPER)
                    | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
                header.superName = "java/lang/Object";
                break;
            }
        }

        final boolean asInterface = (header.access & Opcodes.ACC_INTERFACE) != 0;
        final Map<String, byte[]> fieldSource = new LinkedHashMap<>();
        final Map<String, byte[]> methodSource = new LinkedHashMap<>();

        for (byte[] b : copies) {
            collectMembers(b, fieldSource, methodSource, asInterface);
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(header.version, header.access, header.name, header.signature,
            header.superName, header.interfaces);

        Set<String> emittedFields = new LinkedHashSet<>();
        for (Map.Entry<String, byte[]> fe : fieldSource.entrySet()) {
            if (!emittedFields.add(fe.getKey())) continue;
            projectField(fe.getValue(), fe.getKey(), cw, asInterface);
        }

        Set<String> emittedMethods = new LinkedHashSet<>();
        for (Map.Entry<String, byte[]> me : methodSource.entrySet()) {
            if (!emittedMethods.add(me.getKey())) continue;

            if (asInterface && me.getKey().startsWith("<init>")) continue;
            projectMethod(me.getValue(), me.getKey(), cw, asInterface);
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static final class Header {
        int version;
        int access;
        String name;
        String signature;
        String superName;
        String[] interfaces;
    }

    private static Header readHeader(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        Header h = new Header();
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                h.version = version;
                h.access = access;
                h.name = name;
                h.signature = signature;
                h.superName = superName;
                h.interfaces = interfaces;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return h;
    }

    private static void collectMembers(byte[] classBytes,
                                        Map<String, byte[]> fieldSource,
                                        Map<String, byte[]> methodSource,
                                        boolean asInterface) {
        ClassReader cr = new ClassReader(classBytes);
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String desc,
                                            String signature, Object value) {
                if (asInterface && (access & Opcodes.ACC_STATIC) == 0) return null;
                String key = name + ":" + desc;
                fieldSource.putIfAbsent(key, classBytes);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                              String signature, String[] exceptions) {
                String key = name + desc;
                methodSource.putIfAbsent(key, classBytes);
                return null;
            }
        }, ClassReader.SKIP_DEBUG);
    }

    private static void projectField(byte[] sourceBytes, String fieldKey,
                                      ClassWriter target, boolean asInterface) {
        int sep = fieldKey.indexOf(':');
        String wantedName = fieldKey.substring(0, sep);
        String wantedDesc = fieldKey.substring(sep + 1);

        ClassReader cr = new ClassReader(sourceBytes);
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String desc,
                                            String signature, Object value) {
                if (!name.equals(wantedName) || !desc.equals(wantedDesc)) return null;

                int effective = asInterface
                    ? (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)
                    : access;
                return target.visitField(effective, name, desc, signature, value);
            }
        }, 0);
    }

    private static void projectMethod(byte[] sourceBytes, String methodKey,
                                       ClassWriter target, boolean asInterface) {
        int paren = methodKey.indexOf('(');
        String wantedName = methodKey.substring(0, paren);
        String wantedDesc = methodKey.substring(paren);

        ClassReader cr = new ClassReader(sourceBytes);
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                              String signature, String[] exceptions) {
                if (!name.equals(wantedName) || !desc.equals(wantedDesc)) return null;
                if (!asInterface) {
                    return target.visitMethod(access, name, desc, signature, exceptions);
                }

                boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
                if (isStatic) {
                    return target.visitMethod(access, name, desc, signature, exceptions);
                }
                int absAccess = (access & ~Opcodes.ACC_FINAL & ~Opcodes.ACC_SYNCHRONIZED
                    & ~Opcodes.ACC_NATIVE & ~Opcodes.ACC_STRICT)
                    | Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT;
                MethodVisitor mv = target.visitMethod(absAccess, name, desc,
                    signature, exceptions);
                if (mv != null) mv.visitEnd();
                return null;
            }
        }, 0);
    }
}
