/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic.synth;

import dev.stolym.vyne.engine.dynamic.asm.FrameSafeClassWriter;
import dev.stolym.vyne.engine.dynamic.catalog.BridgeCatalog;
import dev.stolym.vyne.engine.dynamic.catalog.BridgeRule;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AutoShimSynthesizer {
    public static final String SHIM_PREFIX = "vyne_autoshim/";

    private final BridgeCatalog catalog;

    public AutoShimSynthesizer(BridgeCatalog catalog) {
        this.catalog = catalog;
    }

    public static String shimInternalFor(String interOwner) {
        return SHIM_PREFIX + interOwner.replace('$', '_');
    }

    public record ShimMethod(
        String interOwner,
        String name,
        String interDesc,
        String shimDesc,
        boolean wasInstance
    ) {
        public String key() { return name + shimDesc; }
    }

    public static final class ShimPlan {
        public final String interOwner;

        public final Map<String, ShimMethod> methods = new LinkedHashMap<>();

        public ShimPlan(String interOwner) { this.interOwner = interOwner; }
    }

    public static Map<String, ShimPlan> planShims(Iterable<ShimMethod> missingOnExistingOwners) {
        Map<String, ShimPlan> out = new LinkedHashMap<>();
        for (ShimMethod sm : missingOnExistingOwners) {
            ShimPlan p = out.computeIfAbsent(sm.interOwner, ShimPlan::new);
            p.methods.putIfAbsent(sm.key(), sm);
        }
        return out;
    }

    public byte[] synthesize(ShimPlan plan) {
        return synthesize(plan, null);
    }

    public byte[] synthesize(ShimPlan plan, java.util.Set<String> synthesizedStubOwners) {
        String shimInternal = shimInternalFor(plan.interOwner);
        ClassWriter cw = new FrameSafeClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
            shimInternal, null, "java/lang/Object", null);

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        java.util.Set<String> emitted = new java.util.HashSet<>();
        for (ShimMethod sm : plan.methods.values()) {
            if (emitted.add(sm.key())) {
                emitShimMethod(cw, sm, synthesizedStubOwners);
            }

            String neoDesc = forgeToNeoforgeDesc(sm.shimDesc);
            if (neoDesc != null && !neoDesc.equals(sm.shimDesc)
                    && emitted.add(sm.name + neoDesc)) {
                ShimMethod twin = new ShimMethod(sm.interOwner, sm.name,
                    sm.interDesc, neoDesc, sm.wasInstance);
                emitShimMethod(cw, twin, synthesizedStubOwners);
            }
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static String forgeToNeoforgeDesc(String desc) {
        if (desc == null || !desc.contains("Lnet/minecraftforge/")) return null;
        StringBuilder out = new StringBuilder(desc.length() + 16);
        int i = 0;
        while (i < desc.length()) {
            int start = desc.indexOf("Lnet/minecraftforge/", i);
            if (start < 0) {
                out.append(desc, i, desc.length());
                break;
            }
            out.append(desc, i, start);
            int semi = desc.indexOf(';', start);
            if (semi < 0) {
                out.append(desc, start, desc.length());
                break;
            }
            String typeRef = desc.substring(start + 1, semi); 
            String remapped = remapForgeInternal(typeRef);
            out.append('L').append(remapped).append(';');
            i = semi + 1;
        }
        return out.toString();
    }

    private static String remapForgeInternal(String internal) {
        if (!internal.startsWith("net/minecraftforge/")) return internal;
        if (internal.startsWith("net/minecraftforge/vyne/")) return internal;
        if (internal.startsWith("net/minecraftforge/fml/")) return internal;
        if (internal.startsWith("net/minecraftforge/common/capabilities/")) return internal;
        String neo = internal.replace("net/minecraftforge/", "net/neoforged/neoforge/");
        if (neo.contains("ForgeConfigSpec")) {
            neo = neo.replace("ForgeConfigSpec", "ModConfigSpec");
        }
        return neo;
    }

    private void emitShimMethod(ClassWriter cw, ShimMethod sm,
                                java.util.Set<String> synthesizedStubOwners) {
        if ("<init>".equals(sm.name) || "<clinit>".equals(sm.name)) return;

        Type returnType = Type.getReturnType(sm.shimDesc);
        Type[] shimArgs = Type.getArgumentTypes(sm.shimDesc);

        MethodVisitor mv = cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            sm.name, sm.shimDesc, null, null);
        mv.visitCode();

        BridgeRule.Method rule = catalog.methodRule(sm.interOwner, sm.name, sm.interDesc);

        if (rule != null && rule.body == BridgeRule.BodyKind.DELEGATE_STATIC
                && rule.target != null) {
            String invokeDesc = rule.targetDesc != null ? rule.targetDesc : sm.shimDesc;
            Type[] invokeArgs = Type.getArgumentTypes(invokeDesc);
            int slot = 0;
            for (int i = 0; i < shimArgs.length && i < invokeArgs.length; i++) {
                Type shimT = shimArgs[i];
                loadLocal(mv, shimT, slot);
                slot += shimT.getSize();
            }
            int split = rule.target.lastIndexOf('.');
            if (split > 0) {
                String targetOwner = rule.target.substring(0, split);
                String targetName  = rule.target.substring(split + 1);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, targetOwner, targetName,
                    invokeDesc, false);
                Type invokeReturn = Type.getReturnType(invokeDesc);
                if (invokeReturn.getSort() == Type.OBJECT
                        && returnType.getSort() == Type.OBJECT
                        && !invokeReturn.getInternalName().equals(returnType.getInternalName())) {
                    mv.visitTypeInsn(Opcodes.CHECKCAST, returnType.getInternalName());
                }
                emitReturnOfType(mv, returnType);
            } else {
                BytecodeDefaults.emitDefaultReturn(mv, returnType);
            }
        } else {
            if (sm.wasInstance && returnType.getSort() == Type.OBJECT
                    && returnType.getInternalName().equals(sm.interOwner)) {
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitTypeInsn(Opcodes.CHECKCAST, returnType.getInternalName());
                mv.visitInsn(Opcodes.ARETURN);
            } else {
                BytecodeDefaults.emitSmartReturn(mv, returnType, catalog,
                    synthesizedStubOwners != null ? synthesizedStubOwners::contains : null);
            }
        }

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void loadLocal(MethodVisitor mv, Type t, int slot) {
        switch (t.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT ->
                mv.visitVarInsn(Opcodes.ILOAD, slot);
            case Type.LONG -> mv.visitVarInsn(Opcodes.LLOAD, slot);
            case Type.FLOAT -> mv.visitVarInsn(Opcodes.FLOAD, slot);
            case Type.DOUBLE -> mv.visitVarInsn(Opcodes.DLOAD, slot);
            default -> mv.visitVarInsn(Opcodes.ALOAD, slot);
        }
    }

    private static void emitReturnOfType(MethodVisitor mv, Type t) {
        switch (t.getSort()) {
            case Type.VOID -> mv.visitInsn(Opcodes.RETURN);
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT ->
                mv.visitInsn(Opcodes.IRETURN);
            case Type.LONG -> mv.visitInsn(Opcodes.LRETURN);
            case Type.FLOAT -> mv.visitInsn(Opcodes.FRETURN);
            case Type.DOUBLE -> mv.visitInsn(Opcodes.DRETURN);
            default -> mv.visitInsn(Opcodes.ARETURN);
        }
    }
}
