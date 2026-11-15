/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.engine.dynamic.rewrite;

import dev.stolym.vyne.compat.ServiceLoaderUtil;
import dev.stolym.vyne.engine.dynamic.asm.FrameSafeClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class GetServiceMultiLoaderPatch {
    private static final String UTIL = Type.getInternalName(ServiceLoaderUtil.class);
    private static final String UTIL_DESC =
        "(Ljava/lang/Class;Ljava/lang/ClassLoader;)Ljava/lang/Object;";
    private static final String GET_SERVICE_NAME = "getService";
    private static final String CLASS_CLASS_NAME = "java/lang/Class";
    private static final String CLASSLOADER_DESC = "Ljava/lang/ClassLoader;";
    private static final String CLASS_GET_CL_DESC = "()Ljava/lang/ClassLoader;";

    private GetServiceMultiLoaderPatch() {}

    public static byte[] patch(byte[] classfile) {
        ClassReader reader = new ClassReader(classfile);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.SKIP_FRAMES);
        boolean changed = false;
        for (MethodNode mn : node.methods) {
            if (rewriteGetService(node.name, mn)) {
                changed = true;
            }
        }
        if (!changed) {
            return classfile;
        }
        ClassWriter writer = new FrameSafeClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean isGetServiceDescriptor(String desc) {
        Type[] args = Type.getArgumentTypes(desc);
        Type ret = Type.getReturnType(desc);
        return args.length == 1
            && Type.getType(Class.class).equals(args[0])
            && (ret.getSort() == Type.OBJECT || ret.getSort() == Type.ARRAY);
    }

    private static boolean rewriteGetService(String ownerInternal, MethodNode mn) {
        if (!GET_SERVICE_NAME.equals(mn.name) || !isGetServiceDescriptor(mn.desc)) {
            return false;
        }
        if (!usesServiceLoaderLoad(mn)) {
            return false;
        }
        FieldInsnNode clField = firstClassLoaderStatic(ownerInternal, mn);
        boolean useParamClassLoader = clField == null && usesClassParamGetClassLoader(mn);
        if (clField == null && !useParamClassLoader) {
            return false;
        }
        Type retType = Type.getReturnType(mn.desc);
        mn.instructions.clear();
        if (mn.tryCatchBlocks != null) {
            mn.tryCatchBlocks.clear();
        }
        if (mn.localVariables != null) {
            mn.localVariables.clear();
        }
        LabelNode ok = new LabelNode();
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        if (clField != null) {
            il.add(new FieldInsnNode(Opcodes.GETSTATIC, clField.owner, clField.name, clField.desc));
        } else {
            il.add(new InsnNode(Opcodes.DUP));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CLASS_CLASS_NAME, "getClassLoader",
                CLASS_GET_CL_DESC, false));
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, UTIL, "loadFirst", UTIL_DESC, false));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new JumpInsnNode(Opcodes.IFNONNULL, ok));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new TypeInsnNode(Opcodes.NEW, "java/lang/IllegalStateException"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new LdcInsnNode("No valid ServiceImpl (SPI not visible to any ClassLoader)"));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException",
            "<init>", "(Ljava/lang/String;)V", false));
        il.add(new InsnNode(Opcodes.ATHROW));
        il.add(ok);
        if (retType.getSort() == Type.OBJECT
                && !"java/lang/Object".equals(retType.getInternalName())) {
            il.add(new TypeInsnNode(Opcodes.CHECKCAST, retType.getInternalName()));
        } else if (retType.getSort() == Type.ARRAY) {
            il.add(new TypeInsnNode(Opcodes.CHECKCAST, retType.getInternalName()));
        }
        il.add(new InsnNode(Opcodes.ARETURN));
        mn.instructions.add(il);
        mn.maxStack = 0;
        mn.maxLocals = 0;
        return true;
    }

    private static boolean usesClassParamGetClassLoader(MethodNode mn) {
        for (AbstractInsnNode insn = mn.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode min
                && min.getOpcode() == Opcodes.INVOKEVIRTUAL
                && CLASS_CLASS_NAME.equals(min.owner)
                && "getClassLoader".equals(min.name)
                && CLASS_GET_CL_DESC.equals(min.desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesServiceLoaderLoad(MethodNode mn) {
        for (AbstractInsnNode insn = mn.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode min
                && min.getOpcode() == Opcodes.INVOKESTATIC
                && "java/util/ServiceLoader".equals(min.owner)
                && "load".equals(min.name)
                && "(Ljava/lang/Class;Ljava/lang/ClassLoader;)Ljava/util/ServiceLoader;".equals(min.desc)) {
                return true;
            }
        }
        return false;
    }

    private static FieldInsnNode firstClassLoaderStatic(String ownerInternal, MethodNode mn) {
        FieldInsnNode any = null;
        for (AbstractInsnNode insn = mn.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode fin
                && fin.getOpcode() == Opcodes.GETSTATIC
                && CLASSLOADER_DESC.equals(fin.desc)) {
                if (ownerInternal.equals(fin.owner)) {
                    return fin;
                }
                if (any == null) {
                    any = fin;
                }
            }
        }
        return any;
    }
}
