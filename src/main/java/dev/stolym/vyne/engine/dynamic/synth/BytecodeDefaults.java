/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic.synth;

import dev.stolym.vyne.engine.dynamic.catalog.BridgeCatalog;
import dev.stolym.vyne.engine.dynamic.catalog.BridgeRule;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.Map;
import java.util.function.Predicate;

public final class BytecodeDefaults {
    private BytecodeDefaults() {}

    public static void emitDefaultReturn(MethodVisitor mv, Type returnType) {
        switch (returnType.getSort()) {
            case Type.VOID -> mv.visitInsn(Opcodes.RETURN);
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> {
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitInsn(Opcodes.IRETURN);
            }
            case Type.LONG -> {
                mv.visitInsn(Opcodes.LCONST_0);
                mv.visitInsn(Opcodes.LRETURN);
            }
            case Type.FLOAT -> {
                mv.visitInsn(Opcodes.FCONST_0);
                mv.visitInsn(Opcodes.FRETURN);
            }
            case Type.DOUBLE -> {
                mv.visitInsn(Opcodes.DCONST_0);
                mv.visitInsn(Opcodes.DRETURN);
            }
            case Type.ARRAY, Type.OBJECT -> {
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitInsn(Opcodes.ARETURN);
            }
            default -> {
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitInsn(Opcodes.ARETURN);
            }
        }
    }

    public static void emitSmartReturn(MethodVisitor mv, Type returnType,
                                       BridgeCatalog catalog) {
        emitSmartReturn(mv, returnType, catalog, null);
    }

    public static void emitSmartReturn(MethodVisitor mv, Type returnType,
                                       BridgeCatalog catalog,
                                       Predicate<String> isSynthesizedStub) {
        switch (returnType.getSort()) {
            case Type.OBJECT -> {
                String internal = returnType.getInternalName();
                if ("java/util/Optional".equals(internal)) {
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Optional",
                        "empty", "()Ljava/util/Optional;", false);
                    mv.visitInsn(Opcodes.ARETURN);
                    return;
                }
                if ("java/util/List".equals(internal)
                        || "java/util/Collection".equals(internal)
                        || "java/lang/Iterable".equals(internal)) {
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Collections",
                        "emptyList", "()Ljava/util/List;", false);
                    mv.visitInsn(Opcodes.ARETURN);
                    return;
                }
                if ("java/util/Set".equals(internal)) {
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Collections",
                        "emptySet", "()Ljava/util/Set;", false);
                    mv.visitInsn(Opcodes.ARETURN);
                    return;
                }
                if ("java/util/Map".equals(internal)) {
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Collections",
                        "emptyMap", "()Ljava/util/Map;", false);
                    mv.visitInsn(Opcodes.ARETURN);
                    return;
                }
                if ("java/lang/String".equals(internal)) {
                    mv.visitLdcInsn("");
                    mv.visitInsn(Opcodes.ARETURN);
                    return;
                }
                if (catalog != null) {
                    BridgeRule.Cls cls = catalog.classRule(internal);
                    if (cls != null) {
                        for (Map.Entry<String, String> e : cls.singletons.entrySet()) {
                            if ("self-ctor".equals(e.getValue())) {
                                mv.visitFieldInsn(Opcodes.GETSTATIC, internal,
                                    e.getKey(), "L" + internal + ";");
                                mv.visitInsn(Opcodes.ARETURN);
                                return;
                            }
                        }
                    }
                }

                if (isSynthesizedStub != null && isSynthesizedStub.test(internal)) {
                    mv.visitTypeInsn(Opcodes.NEW, internal);
                    mv.visitInsn(Opcodes.DUP);
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, internal,
                        "<init>", "()V", false);
                    mv.visitInsn(Opcodes.ARETURN);
                    return;
                }
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitInsn(Opcodes.ARETURN);
            }
            default -> emitDefaultReturn(mv, returnType);
        }
    }

    public static int defaultReturnStackHint(Type returnType) {
        return switch (returnType.getSort()) {
            case Type.LONG, Type.DOUBLE -> 2;
            case Type.VOID -> 0;
            default -> 1;
        };
    }

    public static void emitObjectSuperCall(MethodVisitor mv) {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object",
            "<init>", "()V", false);
    }
}
