/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.util;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MixinExtrasCompat {
    private static final String GENERATED_BASE =
        "com/llamalad7/mixinextras/sugar/impl/ref/generated/";
    private static final String REF_IFACE_BASE =
        "com.llamalad7.mixinextras.sugar.ref.";
    private static final String[] REF_INTERFACE_SIMPLE_NAMES = {
        "LocalRef",
        "LocalBooleanRef",
        "LocalByteRef",
        "LocalCharRef",
        "LocalShortRef",
        "LocalIntRef",
        "LocalLongRef",
        "LocalFloatRef",
        "LocalDoubleRef"
    };

    private MixinExtrasCompat() {}

    public static void prepare(ClassLoader loader) {
        VyneLogger.info("[Compat] Preparing MixinExtras LocalRef bridge with loader=" + loader);
        try {
            LinkedHashSet<ClassLoader> targets = new LinkedHashSet<>();
            for (ClassLoader cl = loader; cl != null; cl = cl.getParent()) {
                targets.add(cl);
            }
            targets.add(ClassLoader.getSystemClassLoader());

            List<String> attempts = new ArrayList<>();
            boolean preparedAny = false;
            for (ClassLoader target : targets) {
                boolean prepared = prepareLoader(target, attempts);
                preparedAny |= prepared;
            }
            if (!preparedAny) {
                throw new ClassNotFoundException("No loader in chain had MixinExtras classes. Attempts=" + attempts);
            }
            logVisibility("requested-loader", loader);
            logVisibility("thread-context-loader", Thread.currentThread().getContextClassLoader());
            logVisibility("compat-class-loader", MixinExtrasCompat.class.getClassLoader());
            VyneLogger.success("[Compat] MixinExtras LocalRef bridge ready.");
        } catch (Throwable t) {
            VyneLogger.warn("[Compat] MixinExtras LocalRef prep failed: " + t);
        }
    }

    private static boolean prepareLoader(ClassLoader loader, List<String> attempts) throws Exception {
        if (loader == null) {
            return false;
        }
        try {
            attempts.add("hit:" + loader);
            Class.forName("com.llamalad7.mixinextras.sugar.impl.ref.LocalRefClassGenerator", true, loader);
            Map<Class<?>, String> cache = getGeneratorCache(loader);
            boolean preparedAny = false;
            for (String simple : REF_INTERFACE_SIMPLE_NAMES) {
                String ifaceName = REF_IFACE_BASE + simple;
                Class<?> iface;
                try {
                    iface = Class.forName(ifaceName, true, loader);
                } catch (ClassNotFoundException ignored) {
                    continue;
                }
                String implOwner = GENERATED_BASE + simple + "Impl";
                ensureRefImplDefined(loader, iface, implOwner);
                cache.put(iface, implOwner);
                preparedAny = true;
                VyneLogger.debug("[Compat] Seeded LocalRefClassGenerator cache: " + iface.getName()
                    + " -> " + implOwner);
            }
            if (preparedAny) {
                VyneLogger.debug("[Compat] LocalRef-family prepared in loader=" + loader);
            }
            return preparedAny;
        } catch (ClassNotFoundException ignored) {
            attempts.add("miss:" + loader);
            return false;
        } catch (Throwable t) {
            attempts.add("error:" + loader + ":" + t.getClass().getSimpleName());
            VyneLogger.debug("[Compat] LocalRef prep error in loader=" + loader + " cause=" + t);
            return false;
        }
    }

    private static void ensureRefImplDefined(ClassLoader loader, Class<?> iface, String implOwner) throws Exception {
        String implName = implOwner.replace('/', '.');
        try {
            Class<?> existing = Class.forName(implName, false, loader);
            VyneLogger.debug("[Compat] " + existing.getSimpleName() + " already present in loader=" + loader
                + " definedBy=" + existing.getClassLoader());
            return;
        } catch (ClassNotFoundException ignored) {
        }

        Class<?> dummy = Class.forName(
            "com.llamalad7.mixinextras.sugar.impl.ref.generated.GeneratedImplDummy",
            true, loader);
        Method getLookup = dummy.getMethod("getLookup");
        Object lookup = getLookup.invoke(null);
        Method defineClass = lookup.getClass().getMethod("defineClass", byte[].class);
        defineClass.invoke(lookup, generateRefImplBytes(iface, implOwner));
        VyneLogger.info("[Compat] Defined fallback " + implName + " in loader=" + loader);
    }

    private static void logVisibility(String source, ClassLoader loader) {
        if (loader == null) {
            VyneLogger.debug("[Compat] LocalRef visibility " + source + ": loader=null");
            return;
        }
        int ok = 0;
        for (String simple : REF_INTERFACE_SIMPLE_NAMES) {
            String implName = (GENERATED_BASE + simple + "Impl").replace('/', '.');
            try {
                Class<?> impl = Class.forName(implName, false, loader);
                ok++;
                VyneLogger.debug("[Compat] LocalRef visibility " + source + ": OK " + implName
                    + " via " + loader + " definedBy=" + impl.getClassLoader());
            } catch (Throwable ignored) {
            }
        }
        if (ok == 0) {
            VyneLogger.warn("[Compat] LocalRef visibility " + source + ": FAIL via " + loader
                + " (no generated Local*RefImpl visible)");
        } else {
            VyneLogger.info("[Compat] LocalRef visibility " + source + ": " + ok
                + " generated Local*RefImpl class(es) visible via " + loader);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Class<?>, String> getGeneratorCache(ClassLoader loader) throws Exception {
        Class.forName("com.llamalad7.mixinextras.sugar.impl.ref.LocalRefUtils", true, loader);
        Class<?> lrg = Class.forName(
            "com.llamalad7.mixinextras.sugar.impl.ref.LocalRefClassGenerator",
            true,
            loader
        );

        Field mapField = lrg.getDeclaredField("interfaceToImpl");
        mapField.setAccessible(true);
        return (Map<Class<?>, String>) mapField.get(null);
    }

    private static byte[] generateRefImplBytes(Class<?> iface, String implOwner) {
        Type valueType = inferValueType(iface);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_FINAL,
            implOwner,
            null,
            "java/lang/Object",
            new String[]{Type.getInternalName(iface)}
        );
        cw.visitField(Opcodes.ACC_PRIVATE, "value", valueType.getDescriptor(), null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PRIVATE, "state", "B", null, null).visitEnd();

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, implOwner, "state", "B");
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "checkState", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, implOwner, "state", "B");
        Label ok = new Label();
        mv.visitJumpInsn(Opcodes.IFEQ, ok);
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("Invalid LocalRef state");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false);
        mv.visitInsn(Opcodes.ATHROW);
        mv.visitLabel(ok);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        Set<String> emitted = new HashSet<>();
        for (Method method : iface.getMethods()) {
            if (!java.lang.reflect.Modifier.isAbstract(method.getModifiers())) continue;
            if (method.getDeclaringClass() == Object.class) continue;
            emitted.add(method.getName() + Type.getMethodDescriptor(method));
            emitInterfaceMethod(cw, method, implOwner, valueType);
        }
        emitCompatibilityMethods(cw, iface, implOwner, valueType, emitted);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void emitInterfaceMethod(ClassWriter cw, Method method, String owner, Type valueType) {
        String desc = Type.getMethodDescriptor(method);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), desc, null, null);
        mv.visitCode();

        Class<?>[] params = method.getParameterTypes();
        Type returnType = Type.getType(method.getReturnType());

        if (method.getName().equals("get") && params.length == 0) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "checkState", "()V", false);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, owner, "value", valueType.getDescriptor());
            mv.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return;
        }
        if (method.getName().equals("set") && params.length == 1 && method.getReturnType() == void.class) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "checkState", "()V", false);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            Type p = Type.getType(params[0]);
            mv.visitVarInsn(p.getOpcode(Opcodes.ILOAD), 1);
            mv.visitFieldInsn(Opcodes.PUTFIELD, owner, "value", valueType.getDescriptor());
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return;
        }
        if (method.getName().equals("init") && params.length == 1 && method.getReturnType() == void.class) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            Type p = Type.getType(params[0]);
            mv.visitVarInsn(p.getOpcode(Opcodes.ILOAD), 1);
            mv.visitFieldInsn(Opcodes.PUTFIELD, owner, "value", valueType.getDescriptor());
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitFieldInsn(Opcodes.PUTFIELD, owner, "state", "B");
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return;
        }
        if (method.getName().equals("dispose") && params.length == 0) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "checkState", "()V", false);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ICONST_2);
            mv.visitFieldInsn(Opcodes.PUTFIELD, owner, "state", "B");
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, owner, "value", valueType.getDescriptor());
            mv.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return;
        }

        emitDefaultReturn(mv, returnType);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void emitCompatibilityMethods(
        ClassWriter cw,
        Class<?> iface,
        String owner,
        Type valueType,
        Set<String> emitted
    ) {
        String valueDesc = valueType.getDescriptor();
        String ifaceInternal = Type.getInternalName(iface);

        if (!emitted.contains("get()" + valueDesc)) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "get", "()" + valueDesc, null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "checkState", "()V", false);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, owner, "value", valueDesc);
            mv.visitInsn(valueType.getOpcode(Opcodes.IRETURN));
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        if (!emitted.contains("set(" + valueDesc + ")V")) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "set", "(" + valueDesc + ")V", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "checkState", "()V", false);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(valueType.getOpcode(Opcodes.ILOAD), 1);
            mv.visitFieldInsn(Opcodes.PUTFIELD, owner, "value", valueDesc);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        if (!emitted.contains("init(" + valueDesc + ")V")) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "init", "(" + valueDesc + ")V", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(valueType.getOpcode(Opcodes.ILOAD), 1);
            mv.visitFieldInsn(Opcodes.PUTFIELD, owner, "value", valueDesc);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitFieldInsn(Opcodes.PUTFIELD, owner, "state", "B");
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        if (!emitted.contains("dispose()" + valueDesc)) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "dispose", "()" + valueDesc, null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "checkState", "()V", false);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ICONST_2);
            mv.visitFieldInsn(Opcodes.PUTFIELD, owner, "state", "B");
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, owner, "value", valueDesc);
            mv.visitInsn(valueType.getOpcode(Opcodes.IRETURN));
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        if (!emitted.contains("init(" + valueDesc + ")L" + ifaceInternal + ";")) {
            MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC,
                "init",
                "(" + valueDesc + ")L" + ifaceInternal + ";",
                null,
                null
            );
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(valueType.getOpcode(Opcodes.ILOAD), 1);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "init", "(" + valueDesc + ")V", false);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        if (!emitted.contains("set(" + valueDesc + ")L" + ifaceInternal + ";")) {
            MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC,
                "set",
                "(" + valueDesc + ")L" + ifaceInternal + ";",
                null,
                null
            );
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(valueType.getOpcode(Opcodes.ILOAD), 1);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "set", "(" + valueDesc + ")V", false);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }

    private static Type inferValueType(Class<?> iface) {
        for (Method m : iface.getMethods()) {
            if (m.getName().equals("get") && m.getParameterCount() == 0) {
                return Type.getType(m.getReturnType());
            }
            if ((m.getName().equals("set") || m.getName().equals("init")) && m.getParameterCount() == 1) {
                return Type.getType(m.getParameterTypes()[0]);
            }
        }
        return Type.getType(Object.class);
    }

    private static void emitDefaultReturn(MethodVisitor mv, Type ret) {
        switch (ret.getSort()) {
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
            default -> {
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitInsn(Opcodes.ARETURN);
            }
        }
    }
}
