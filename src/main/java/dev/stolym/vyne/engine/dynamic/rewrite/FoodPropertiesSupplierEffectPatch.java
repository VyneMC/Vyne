/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.engine.dynamic.rewrite;

import dev.stolym.vyne.engine.dynamic.asm.FrameSafeClassWriter;
import dev.stolym.vyne.mappings.MappingRegistry;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class FoodPropertiesSupplierEffectPatch {
    private static final String MOJ_BUILDER = "net/minecraft/world/food/FoodProperties$Builder";
    private static final String INTER_BUILDER = "net/minecraft/class_4174$class_4175";
    private static final String MOJ_EFFECT = "net/minecraft/world/effect/MobEffectInstance";
    private static final String INTER_EFFECT = "net/minecraft/class_1293";

    private FoodPropertiesSupplierEffectPatch() {}

    public static byte[] patch(byte[] classfile) {
        if (classfile == null || classfile.length == 0) {
            return classfile;
        }
        ClassReader reader = new ClassReader(classfile);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.SKIP_FRAMES);
        boolean changed = false;
        for (MethodNode mn : node.methods) {
            for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode call)) {
                    continue;
                }
                if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                        || !"effect".equals(call.name)
                        || !isFoodBuilder(call.owner)
                        || call.desc == null
                        || !call.desc.startsWith("(Ljava/util/function/Supplier;F)")) {
                    continue;
                }
                int floatLocal = mn.maxLocals++;
                InsnList before = new InsnList();
                before.add(new VarInsnNode(Opcodes.FSTORE, floatLocal));
                before.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                    "java/util/function/Supplier", "get", "()Ljava/lang/Object;", true));
                before.add(new TypeInsnNode(Opcodes.CHECKCAST, mobEffectInstanceInternal()));
                before.add(new VarInsnNode(Opcodes.FLOAD, floatLocal));
                mn.instructions.insertBefore(call, before);
                call.name = vanillaEffectName();
                call.desc = concreteEffectDescriptor(call.owner);
                call.itf = false;
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

    private static boolean isFoodBuilder(String owner) {
        return MOJ_BUILDER.equals(owner) || INTER_BUILDER.equals(owner);
    }

    private static String vanillaEffectName() {
        if (!MappingRegistry.isReady()) {
            return "effect";
        }
        String mapped = MappingRegistry.get().mapMethod(
            "net/minecraft/world/food/FoodProperties$Builder",
            "effect",
            "(Lnet/minecraft/world/effect/MobEffectInstance;F)Lnet/minecraft/world/food/FoodProperties$Builder;");
        return mapped != null ? mapped : "effect";
    }

    private static String mobEffectInstanceInternal() {
        if (!MappingRegistry.isReady()) {
            return INTER_EFFECT;
        }
        String mapped = MappingRegistry.get().mapClass(MOJ_EFFECT);
        return mapped != null ? mapped : INTER_EFFECT;
    }

    private static String concreteEffectDescriptor(String owner) {
        String effect = mobEffectInstanceInternal();
        return "(L" + effect + ";F)L" + owner + ";";
    }
}
