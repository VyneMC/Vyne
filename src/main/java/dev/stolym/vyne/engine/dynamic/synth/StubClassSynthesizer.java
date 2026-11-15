/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic.synth;

import dev.stolym.vyne.engine.dynamic.asm.FrameSafeClassWriter;
import dev.stolym.vyne.engine.dynamic.catalog.BridgeCatalog;
import dev.stolym.vyne.engine.dynamic.catalog.BridgeRule;
import dev.stolym.vyne.engine.dynamic.scanner.ReferenceReport;
import dev.stolym.vyne.mappings.MappingRegistry;
import dev.stolym.vyne.util.VyneLogger;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class StubClassSynthesizer {
    private final BridgeCatalog catalog;

    public StubClassSynthesizer(BridgeCatalog catalog) {
        this.catalog = catalog;
    }

    public Map<String, byte[]> synthesize(Set<String> missingClasses,
                                          ReferenceReport report) {
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (String internal : missingClasses) {
            ReferenceReport.ClassUsage usage = report.externalUsage.get(internal);
            if (usage == null) continue;
            try {
                byte[] bytes = synthesizeOne(internal, usage, missingClasses);
                out.put(internal, bytes);
            } catch (Throwable t) {
                VyneLogger.warn("[DynamicEngine] Failed to synthesize stub for "
                    + internal + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
        return out;
    }

    private byte[] synthesizeOne(String internal, ReferenceReport.ClassUsage usage,
                                 Set<String> missingClasses) {
        BridgeRule.Cls rule = catalog.classRule(internal);

        String superClass = rule != null ? rule.superclass : inferSuperclass(internal);
        String[] interfaces = rule != null && !rule.interfaces.isEmpty()
            ? rule.interfaces.toArray(new String[0]) : null;

        boolean asInterface = usage.usedAsInterface
            || (internal != null && internal.startsWith("net/neoforged/neoforge/registries/callback/"));
        final boolean deferredSoundNeo = !asInterface
            && "net/neoforged/neoforge/common/util/DeferredSoundType".equals(internal)
            && "net/minecraft/class_2498".equals(superClass);
        ClassWriter cw = new FrameSafeClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        int access = asInterface
            ? (Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT)
            : (Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER);
        String stubSuper = asInterface ? "java/lang/Object" : superClass;
        cw.visit(Opcodes.V17, access, internal, null, stubSuper, interfaces);

        if (!asInterface) {
            if (!deferredSoundNeo) {
                emitNoArgCtor(cw, superClass);
            }
            if (deferredSoundNeo) {
                emitDeferredSoundTypeFields(cw);
                emitDeferredSoundTypeSupplierCtor(cw, internal, superClass);
            } else {
                for (String ctorDesc : usage.newCtorDescs) {
                    if ("()V".equals(ctorDesc)) continue;
                    emitCtor(cw, internal, ctorDesc, superClass);
                }
            }
            if ("net/neoforged/neoforge/fluids/BaseFlowingFluid".equals(internal)
                    && isMinecraftFlowingFluidSuper(superClass)) {
                String neoProps = "(Lnet/neoforged/neoforge/fluids/BaseFlowingFluid$Properties;)V";
                if (!usage.newCtorDescs.contains(neoProps)) {
                    emitCtor(cw, internal, neoProps, superClass);
                }
            }
            if ("net/minecraftforge/fluids/ForgeFlowingFluid".equals(internal)
                    && isMinecraftFlowingFluidSuper(superClass)) {
                String forgeProps = "(Lnet/minecraftforge/fluids/ForgeFlowingFluid$Properties;)V";
                if (!usage.newCtorDescs.contains(forgeProps)) {
                    emitCtor(cw, internal, forgeProps, superClass);
                }
            }
            String neoPropsInner = "(Lnet/neoforged/neoforge/fluids/BaseFlowingFluid$Properties;)V";
            if ("net/neoforged/neoforge/fluids/BaseFlowingFluid$Flowing".equals(internal)
                    || "net/neoforged/neoforge/fluids/BaseFlowingFluid$Source".equals(internal)) {
                if (!usage.newCtorDescs.contains(neoPropsInner)) {
                    emitCtor(cw, internal, neoPropsInner, superClass);
                }
            }
            String forgePropsInner = "(Lnet/minecraftforge/fluids/ForgeFlowingFluid$Properties;)V";
            if ("net/minecraftforge/fluids/ForgeFlowingFluid$Flowing".equals(internal)
                    || "net/minecraftforge/fluids/ForgeFlowingFluid$Source".equals(internal)) {
                if (!usage.newCtorDescs.contains(forgePropsInner)) {
                    emitCtor(cw, internal, forgePropsInner, superClass);
                }
            }
        }

        Set<String> emittedFields = new HashSet<>();

        LinkedHashMap<String, String> autoInitStaticFields = new LinkedHashMap<>();
        for (ReferenceReport.FieldRef f : usage.fields) {
            if (!emittedFields.add(f.name)) continue;

            int fAcc;
            if (asInterface) {
                if (!f.isStatic) continue;
                fAcc = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
            } else {
                fAcc = Opcodes.ACC_PUBLIC | (f.isStatic ? Opcodes.ACC_STATIC : 0);
            }
            FieldVisitor fv = cw.visitField(fAcc, f.name, f.desc, null, null);
            if (fv != null) fv.visitEnd();

            if (!f.isStatic) continue;
            String typeInternal = objectTypeOf(f.desc);
            if (typeInternal == null) continue;
            boolean isSelf = typeInternal.equals(internal);
            boolean isMissing = missingClasses != null && missingClasses.contains(typeInternal);
            if (isSelf || isMissing) {
                autoInitStaticFields.put(f.name, typeInternal);
            }
        }

        if (rule != null) {
            for (Map.Entry<String, String> e : rule.singletons.entrySet()) {
                if (emittedFields.add(e.getKey())) {
                    cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        e.getKey(), "L" + internal + ";", null, null).visitEnd();
                }
            }
        }

        boolean needsClinit = (rule != null && !rule.singletons.isEmpty())
            || !autoInitStaticFields.isEmpty();
        if (needsClinit) {
            emitClinitCombined(cw, internal, rule, autoInitStaticFields);
        }

        Set<String> inheritedMethodSigs = new HashSet<>();
        if (!asInterface) {
            collectMethodsFromTypeHierarchy(superClass, inheritedMethodSigs);
        }
        if (interfaces != null) {
            for (String ifc : interfaces) {
                collectMethodsFromTypeHierarchy(ifc, inheritedMethodSigs);
            }
        }

        Set<String> emitted = new HashSet<>();
        for (ReferenceReport.MethodRef m : usage.methods) {
            if (isInitOrClinit(m.name)) continue; 
            String sig = m.name + m.desc;
            if (!emitted.add(sig)) continue;
            if (inheritedMethodSigs.contains(sig)) {
                continue;
            }
            emitMethod(cw, internal, m, missingClasses);
        }
        if (deferredSoundNeo) {
            emitDeferredSoundTypeSupplierGetters(cw, internal);
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static String inferSuperclass(String internal) {
        if (internal == null) return "java/lang/Object";
        if ((internal.startsWith("net/neoforged/neoforge/event/")
             || internal.startsWith("net/minecraftforge/event/"))
            && endsWithSimpleName(internal, "Event")) {
            return "net/neoforged/bus/api/Event";
        }
        return "java/lang/Object";
    }

    private static boolean endsWithSimpleName(String internal, String suffix) {
        int slash = internal.lastIndexOf('/');
        int dollar = internal.lastIndexOf('$');
        int cut = Math.max(slash, dollar);
        String simple = cut >= 0 ? internal.substring(cut + 1) : internal;
        return simple.endsWith(suffix);
    }

    private static String objectTypeOf(String desc) {
        if (desc == null || desc.isEmpty()) return null;
        if (desc.charAt(0) != 'L' || !desc.endsWith(";")) return null;
        return desc.substring(1, desc.length() - 1);
    }

    private static boolean isInitOrClinit(String name) {
        return "<init>".equals(name) || "<clinit>".equals(name);
    }

    private static void collectMethodsFromTypeHierarchy(String internal, Set<String> out) {
        if (internal == null || internal.isEmpty() || "java/lang/Object".equals(internal)) {
            return;
        }
        try {
            Class<?> c = Class.forName(internal.replace('/', '.'), false,
                Thread.currentThread().getContextClassLoader());
            if (c.isInterface()) {
                for (java.lang.reflect.Method met : c.getMethods()) {
                    out.add(met.getName() + Type.getMethodDescriptor(met));
                }
                return;
            }
            while (c != null && c != Object.class) {
                for (java.lang.reflect.Method met : c.getDeclaredMethods()) {
                    out.add(met.getName() + Type.getMethodDescriptor(met));
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void emitNoArgCtor(ClassWriter cw, String superClass) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superClass, "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void emitCtor(ClassWriter cw, String selfInternal, String desc, String superClass) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", desc, null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        Type[] childArgs = Type.getArgumentTypes(desc);
        String superDesc = superInitDescriptorFor(selfInternal, superClass, desc, childArgs);
        Type[] superArgs = Type.getArgumentTypes(superDesc);
        int slot = 1;
        for (Type arg : superArgs) {
            loadLocal(mv, arg, slot);
            slot += arg.getSize();
        }
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superClass, "<init>", superDesc, false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static String superInitDescriptorFor(
        String selfInternal,
        String superClass,
        String childInitDesc,
        Type[] childArgs) {
        if (childArgs.length == 0) {
            return "()V";
        }
        final String neoBase = "net/neoforged/neoforge/fluids/BaseFlowingFluid";
        if (neoBase.equals(superClass)
                && selfInternal != null
                && selfInternal.startsWith(neoBase + "$")) {
            return childInitDesc;
        }
        if (selfInternal != null && selfInternal.equals(neoBase)) {
            if (isMinecraftFlowingFluidSuper(superClass)) {
                return "()V";
            }
        }
        final String forgeBase = "net/minecraftforge/fluids/ForgeFlowingFluid";
        if (forgeBase.equals(superClass)
                && selfInternal != null
                && selfInternal.startsWith(forgeBase + "$")) {
            return childInitDesc;
        }
        if (selfInternal != null && selfInternal.equals(forgeBase)) {
            if (isMinecraftFlowingFluidSuper(superClass)) {
                return "()V";
            }
        }
        if ("java/lang/Object".equals(superClass)) {
            return "()V";
        }
        return childInitDesc;
    }

    private static boolean isMinecraftFlowingFluidSuper(String superClass) {
        return "net/minecraft/world/level/material/FlowingFluid".equals(superClass)
            || "net/minecraft/class_3609".equals(superClass);
    }

    private void emitInterfaceMethod(ClassWriter cw, ReferenceReport.MethodRef m) {
        boolean isStatic = m.opcode == Opcodes.INVOKESTATIC;
        if (isStatic) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                m.name, m.desc, null, null);
            mv.visitCode();
            Type returnType = Type.getReturnType(m.desc);
            BytecodeDefaults.emitSmartReturn(mv, returnType, catalog, null);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return;
        }
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            m.name, m.desc, null, null);
        mv.visitEnd();
    }

    private void emitMethod(ClassWriter cw, String selfInternal,
                            ReferenceReport.MethodRef m,
                            Set<String> missingClasses) {
        boolean isStatic = m.opcode == Opcodes.INVOKESTATIC;
        int access = Opcodes.ACC_PUBLIC | (isStatic ? Opcodes.ACC_STATIC : 0);

        BridgeRule.Method rule = catalog.methodRule(m.owner, m.name, m.desc);
        MethodVisitor mv = cw.visitMethod(access, m.name, m.desc, null, null);
        mv.visitCode();
        emitBody(mv, selfInternal, m, isStatic, rule, catalog, missingClasses);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void emitBody(MethodVisitor mv, String selfInternal,
                                 ReferenceReport.MethodRef m, boolean isStatic,
                                 BridgeRule.Method rule,
                                 BridgeCatalog catalog,
                                 Set<String> missingClasses) {
        Type returnType = Type.getReturnType(m.desc);
        Type[] argTypes = Type.getArgumentTypes(m.desc);
        BridgeRule.BodyKind kind = rule != null ? rule.body : BridgeRule.BodyKind.AUTO_DEFAULT;

        if (kind == BridgeRule.BodyKind.AUTO_DEFAULT && isStatic
                && returnType.getSort() == Type.OBJECT
                && selfInternal.equals(returnType.getInternalName())) {
            kind = BridgeRule.BodyKind.NEW_INSTANCE_SELF;
        }

        switch (kind) {
            case NEW_INSTANCE_SELF -> {
                mv.visitTypeInsn(Opcodes.NEW, selfInternal);
                mv.visitInsn(Opcodes.DUP);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, selfInternal, "<init>", "()V", false);
                mv.visitInsn(Opcodes.ARETURN);
            }
            case RETURN_FIELD -> {
                if (rule.field == null) {
                    BytecodeDefaults.emitDefaultReturn(mv, returnType);
                    return;
                }
                mv.visitFieldInsn(Opcodes.GETSTATIC, selfInternal, rule.field, "L" + selfInternal + ";");
                mv.visitInsn(Opcodes.ARETURN);
            }
            case RETURN_ARG -> {
                if (rule.argIndex < 0 || rule.argIndex >= argTypes.length) {
                    BytecodeDefaults.emitDefaultReturn(mv, returnType);
                    return;
                }
                int localSlot = isStatic ? 0 : 1;
                for (int i = 0; i < rule.argIndex; i++) localSlot += argTypes[i].getSize();
                Type argT = argTypes[rule.argIndex];
                loadLocal(mv, argT, localSlot);
                convertAndReturn(mv, argT, returnType);
            }
            case EMPTY_OPTIONAL -> {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Optional",
                    "empty", "()Ljava/util/Optional;", false);
                mv.visitInsn(Opcodes.ARETURN);
            }
            case EMPTY_LIST -> {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Collections",
                    "emptyList", "()Ljava/util/List;", false);
                mv.visitInsn(Opcodes.ARETURN);
            }
            case EMPTY_SET -> {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Collections",
                    "emptySet", "()Ljava/util/Set;", false);
                mv.visitInsn(Opcodes.ARETURN);
            }
            case EMPTY_MAP -> {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Collections",
                    "emptyMap", "()Ljava/util/Map;", false);
                mv.visitInsn(Opcodes.ARETURN);
            }
            case DELEGATE_STATIC -> {
                if (rule.target == null) {
                    BytecodeDefaults.emitDefaultReturn(mv, returnType);
                    return;
                }
                int split = rule.target.lastIndexOf('.');
                if (split <= 0 || split >= rule.target.length() - 1) {
                    BytecodeDefaults.emitDefaultReturn(mv, returnType);
                    return;
                }
                String targetOwner = rule.target.substring(0, split);
                String targetName = rule.target.substring(split + 1);

                StringBuilder implicitDesc = new StringBuilder("(");
                if (!isStatic) implicitDesc.append("Ljava/lang/Object;");
                for (Type t : argTypes) implicitDesc.append(t.getDescriptor());
                implicitDesc.append(")").append(returnType.getDescriptor());
                String targetDesc = rule.targetDesc != null
                    ? rule.targetDesc : implicitDesc.toString();

                Type[] targetArgs = Type.getArgumentTypes(targetDesc);
                int slot = 0;
                int loadedArgs = 0;
                if (!isStatic && loadedArgs < targetArgs.length) {
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    slot = 1;
                    loadedArgs++;
                } else if (!isStatic) {
                    slot = 1;
                }
                for (int i = 0; i < argTypes.length && loadedArgs < targetArgs.length; i++) {
                    Type t = argTypes[i];
                    loadLocal(mv, t, slot);
                    slot += t.getSize();
                    loadedArgs++;
                }
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, targetOwner, targetName,
                    targetDesc, false);
                Type invokeReturn = Type.getReturnType(targetDesc);
                if (invokeReturn.getSort() == Type.OBJECT
                        && returnType.getSort() == Type.OBJECT
                        && !invokeReturn.getInternalName().equals(returnType.getInternalName())) {
                    mv.visitTypeInsn(Opcodes.CHECKCAST, returnType.getInternalName());
                }
                if (invokeReturn.getSort() == Type.VOID
                        && returnType.getSort() != Type.VOID) {
                    BytecodeDefaults.emitDefaultReturn(mv, returnType);
                    return;
                }
                emitReturnOfType(mv, returnType);
            }
            default -> BytecodeDefaults.emitSmartReturn(mv, returnType, catalog,
                missingClasses != null ? missingClasses::contains : null);
        }
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

    private static void convertAndReturn(MethodVisitor mv, Type argT, Type returnT) {
        if (argT.getSort() == returnT.getSort() || returnT.getSort() == Type.OBJECT
                || returnT.getSort() == Type.ARRAY) {
            emitReturnOfType(mv, returnT);
            return;
        }

        switch (argT.getSize()) {
            case 2 -> mv.visitInsn(Opcodes.POP2);
            default -> mv.visitInsn(Opcodes.POP);
        }
        BytecodeDefaults.emitDefaultReturn(mv, returnT);
    }

    private static void emitClinitCombined(ClassWriter cw, String internal,
                                           BridgeRule.Cls rule,
                                           Map<String, String> autoInitStatics) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();

        if (rule != null) {
            for (Map.Entry<String, String> e : rule.singletons.entrySet()) {
                if (!"self-ctor".equals(e.getValue())) continue;
                if (autoInitStatics.containsKey(e.getKey())) continue; 
                mv.visitTypeInsn(Opcodes.NEW, internal);
                mv.visitInsn(Opcodes.DUP);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, internal, "<init>", "()V", false);
                mv.visitFieldInsn(Opcodes.PUTSTATIC, internal, e.getKey(), "L" + internal + ";");
            }
        }

        for (Map.Entry<String, String> e : autoInitStatics.entrySet()) {
            String fieldName = e.getKey();
            String fieldTypeInternal = e.getValue();
            mv.visitTypeInsn(Opcodes.NEW, fieldTypeInternal);
            mv.visitInsn(Opcodes.DUP);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, fieldTypeInternal,
                "<init>", "()V", false);
            mv.visitFieldInsn(Opcodes.PUTSTATIC, internal, fieldName,
                "L" + fieldTypeInternal + ";");
        }

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static final String DEFERRED_SOUND_SUPPLIER_INIT =
        "(FFLjava/util/function/Supplier;Ljava/util/function/Supplier;Ljava/util/function/Supplier;"
            + "Ljava/util/function/Supplier;Ljava/util/function/Supplier;)V";

    private static void emitDeferredSoundTypeFields(ClassWriter cw) {
        String[] names = {
            "vyneBreakSound", "vyneStepSound", "vynePlaceSound", "vyneHitSound", "vyneFallSound"
        };
        for (String n : names) {
            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, n,
                "Ljava/util/function/Supplier;", null, null).visitEnd();
        }
    }

    private static void emitDeferredSoundTypeSupplierCtor(ClassWriter cw, String selfInternal,
                                                          String superInternal) {
        String soundEventInternal = intermediarySoundEventInternalName();
        String superCtor = soundTypeVanillaCtorDescriptor(soundEventInternal);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
            DEFERRED_SOUND_SUPPLIER_INIT, null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.FLOAD, 1);
        mv.visitVarInsn(Opcodes.FLOAD, 2);
        for (int i = 0; i < 5; i++) {
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitTypeInsn(Opcodes.CHECKCAST, soundEventInternal);
        }
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superInternal, "<init>", superCtor, false);
        String[] fields = {
            "vyneBreakSound", "vyneStepSound", "vynePlaceSound", "vyneHitSound", "vyneFallSound"
        };
        int slot = 3;
        for (String fn : fields) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, slot++);
            mv.visitFieldInsn(Opcodes.PUTFIELD, selfInternal, fn, "Ljava/util/function/Supplier;");
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void emitDeferredSoundTypeSupplierGetters(ClassWriter cw, String selfInternal) {
        String[][] pairs = {
            {"vyneBreakSound", "getBreakSound"},
            {"vyneStepSound", "getStepSound"},
            {"vynePlaceSound", "getPlaceSound"},
            {"vyneHitSound", "getHitSound"},
            {"vyneFallSound", "getFallSound"},
        };
        for (String[] p : pairs) {
            emitDeferredSoundSupplierGetter(cw, selfInternal, p[0], p[1]);
        }
    }

    private static void emitDeferredSoundSupplierGetter(ClassWriter cw, String selfInternal,
                                                        String fieldName, String mojGetter) {
        String mojDesc = "()Lnet/minecraft/sounds/SoundEvent;";
        String interDesc = mapMethodReturnToInterDescriptor(mojDesc);
        String interName = mapSoundTypeMethodNameOrSelf(mojGetter, mojDesc);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, interName, interDesc, null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, selfInternal, fieldName, "Ljava/util/function/Supplier;");
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/function/Supplier", "get",
            "()Ljava/lang/Object;", true);
        mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getReturnType(interDesc).getInternalName());
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static String mapSoundTypeMethodNameOrSelf(String mojGetter, String mojDesc) {
        if (!MappingRegistry.isReady()) {
            return mojGetter;
        }
        String mapped = MappingRegistry.get().mapMethod(
            "net/minecraft/world/level/block/SoundType", mojGetter, mojDesc);
        return mapped != null ? mapped : mojGetter;
    }

    private static String mapMethodReturnToInterDescriptor(String mojMethodDescriptor) {
        if (mojMethodDescriptor == null || !mojMethodDescriptor.startsWith("()")) {
            return "()V";
        }
        String ret = mojMethodDescriptor.substring(2);
        if (!ret.startsWith("L") || !ret.endsWith(";")) {
            return mojMethodDescriptor;
        }
        String mojSlash = ret.substring(1, ret.length() - 1);
        if (MappingRegistry.isReady()) {
            String inter = MappingRegistry.get().mapClass(mojSlash);
            if (inter != null && !inter.isEmpty()) {
                String normalized = inter.indexOf('.') >= 0
                    ? inter.replace('.', '/') : inter;
                return "()" + "L" + normalized + ";";
            }
        }
        return "()Lnet/minecraft/class_3414;";
    }

    private static String intermediarySoundEventInternalName() {
        if (MappingRegistry.isReady()) {
            String mapped = MappingRegistry.get().mapClass("net/minecraft/sounds/SoundEvent");
            if (mapped != null && !mapped.isEmpty()) {
                return mapped.indexOf('.') >= 0 ? mapped.replace('.', '/') : mapped;
            }
        }
        return "net/minecraft/class_3414";
    }

    private static String soundTypeVanillaCtorDescriptor(String soundEventInternal) {
        String part = "L" + soundEventInternal + ";";
        return "(FF" + part + part + part + part + part + ")V";
    }
}
