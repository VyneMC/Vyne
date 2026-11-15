/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.fabricmc.loader.impl.transformer;

import dev.stolym.vyne.mappings.MappingComposer.ComposedMappings;
import dev.stolym.vyne.mappings.MappingRegistry;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

public final class VyneRemapper extends ClassRemapper {
    String currentClassMojmap;
    String currentSuperMojmap;
    String[] currentInterfacesMojmap;

    public VyneRemapper(int api, ClassVisitor cv) {
        super(api, cv, new MojmapRemapper());
        ((MojmapRemapper) super.remapper).owner = this;
    }

    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        this.currentClassMojmap = name;
        this.currentSuperMojmap = unmapHeaderOwnerIfNeeded(superName);
        this.currentInterfacesMojmap = unmapHeaderOwnersIfNeeded(interfaces);

        dev.stolym.vyne.mappings.MappingComposer.ComposedMappings.recordClassHierarchy(
            name, currentSuperMojmap, currentInterfacesMojmap);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    private static String unmapHeaderOwnerIfNeeded(String internal) {
        if (internal == null || !internal.startsWith("net/minecraft/class_")
                || !MappingRegistry.isReady()) {
            return internal;
        }
        try {
            String moj = MappingRegistry.get().unmapClass(internal);
            return moj == null || moj.isBlank() ? internal : moj;
        } catch (Throwable ignored) {
            return internal;
        }
    }

    private static String[] unmapHeaderOwnersIfNeeded(String[] internals) {
        if (internals == null || internals.length == 0) {
            return internals;
        }
        String[] out = new String[internals.length];
        boolean changed = false;
        for (int i = 0; i < internals.length; i++) {
            out[i] = unmapHeaderOwnerIfNeeded(internals[i]);
            changed |= out[i] != internals[i] && !out[i].equals(internals[i]);
        }
        return changed ? out : internals;
    }

    @Override
    public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        org.objectweb.asm.MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new org.objectweb.asm.MethodVisitor(api, mv) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (opcode == org.objectweb.asm.Opcodes.INVOKESTATIC && 
                    (owner.equals("net/minecraft/resources/ResourceLocation") || owner.equals("net/minecraft/class_2960")) &&
                    name.equals("of")) {
                    super.visitMethodInsn(opcode, "net/minecraftforge/vyne/VyneShims", "resourceLocationOf", descriptor, false);
                    return;
                }

                if (owner.equals("dev/stolym/vyne/vcl/shims/ResourceLocationShim")) {
                    super.visitMethodInsn(opcode, "net/minecraftforge/vyne/VyneShims", "resourceLocationOf", descriptor, false);
                    return;
                }

                if (isNeoForgeBlockStateSetValue(opcode, owner, name, descriptor)) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraftforge/vyne/VyneShims", "setValueLazy", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
                    return;
                }

                if (isRegisterDefaultStateCompat(opcode, name, descriptor)) {
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "net/minecraftforge/vyne/VyneShims",
                        "registerDefaultStateCompat",
                        "(Ljava/lang/Object;Ljava/lang/Object;)V",
                        false);
                    return;
                }

                if (isItemDefaultStackCall(opcode, name, descriptor)) {
                    super.visitMethodInsn(
                        opcode,
                        owner,
                        "method_7854",
                        "()Lnet/minecraft/class_1799;",
                        isInterface);
                    return;
                }

                if (isItemStackHasDataComponentCall(opcode, owner, name, descriptor)) {
                    super.visitMethodInsn(
                        opcode,
                        owner,
                        "method_57826",
                        "(Lnet/minecraft/class_9331;)Z",
                        isInterface);
                    return;
                }

                if (isDataComponentHolderGetCall(opcode, owner, name, descriptor)) {
                    super.visitMethodInsn(
                        opcode,
                        owner,
                        "method_57824",
                        "(Lnet/minecraft/class_9331;)Ljava/lang/Object;",
                        isInterface);
                    return;
                }

                if (isDataComponentHolderGetOrDefaultCall(opcode, owner, name, descriptor)) {
                    super.visitMethodInsn(
                        opcode,
                        owner,
                        "method_57825",
                        "(Lnet/minecraft/class_9331;Ljava/lang/Object;)Ljava/lang/Object;",
                        isInterface);
                    return;
                }

                if (isItemPropertyRegisterCompat(opcode, owner, name, descriptor)) {
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "net/minecraftforge/vyne/VyneShims",
                        "registerItemPropertyCompat",
                        "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
                        false);
                    return;
                }

                if (isLegacyBlockBoxToCuboidShape(opcode, owner, name, descriptor)) {
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "net/minecraft/world/level/block/Block",
                        "createCuboidShape",
                        descriptor,
                        false);
                    return;
                }

                if (isGetConnectedDirectionCall(opcode, name, descriptor)) {
                    super.visitMethodInsn(
                        opcode,
                        "net/minecraft/world/level/block/FaceAttachedHorizontalDirectionalBlock",
                        "getConnectedDirection",
                        descriptor,
                        isInterface);
                    return;
                }

                if (opcode == org.objectweb.asm.Opcodes.INVOKESTATIC
                        && "create".equals(name)
                        && descriptor != null
                        && descriptor.contains("IContainerFactory")
                        && ("net/neoforged/neoforge/common/extensions/IMenuTypeExtension".equals(owner)
                            || "net/neoforged/neoforge/common/extensions/IForgeMenuType".equals(owner))) {
                    super.visitMethodInsn(
                        org.objectweb.asm.Opcodes.INVOKESTATIC,
                        "dev/stolym/vyne/compat/NeoForgeMenuTypeBridge",
                        "create",
                        descriptor,
                        false);
                    return;
                }

                if (opcode == Opcodes.INVOKEVIRTUAL && "get".equals(name) && descriptor != null && descriptor.startsWith("()") && owner != null && owner.startsWith("com/tterrag/registrate/util/entry/")) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraftforge/vyne/VyneShims", "getRegistrateEntry", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                    if (!descriptor.equals("()Ljava/lang/Object;")) {
                        String targetType = descriptor.substring(3, descriptor.length() - 1);
                        super.visitTypeInsn(Opcodes.CHECKCAST, targetType);
                    }
                    return;
                }

                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        };
    }

    private static boolean isNeoForgeBlockStateSetValue(int opcode, String owner, String name, String descriptor) {
        if (!"setValue".equals(name) || descriptor == null) {
            return false;
        }
        if (opcode != Opcodes.INVOKEVIRTUAL && opcode != Opcodes.INVOKEINTERFACE) {
            return false;
        }
        if (!descriptor.contains("Comparable")) {
            return false;
        }
        if (!descriptor.contains("class_2769")
                && !descriptor.contains("world/level/block/state/properties/Property")) {
            return false;
        }
        if (owner == null) {
            return false;
        }
        if ("net/minecraft/class_2680".equals(owner)) {
            return true;
        }

        return owner.contains("BlockState");
    }

    private static boolean isRegisterDefaultStateCompat(int opcode, String name, String descriptor) {
        if (opcode != Opcodes.INVOKEVIRTUAL && opcode != Opcodes.INVOKESPECIAL) {
            return false;
        }
        if (!"registerDefaultState".equals(name)) {
            return false;
        }
        if (descriptor == null || !descriptor.endsWith(")V")) {
            return false;
        }
        return descriptor.contains("class_2680")
            || descriptor.contains("world/level/block/state/BlockState");
    }

    private static boolean isItemDefaultStackCall(int opcode, String name, String descriptor) {
        if (opcode != Opcodes.INVOKEVIRTUAL && opcode != Opcodes.INVOKEINTERFACE) {
            return false;
        }
        if (!"getDefaultInstance".equals(name) && !"getDefaultStack".equals(name)) {
            return false;
        }
        if (descriptor == null) {
            return false;
        }
        return "()Lnet/minecraft/class_1799;".equals(descriptor)
            || "()Lnet/minecraft/world/item/ItemStack;".equals(descriptor);
    }

    private static boolean isItemStackHasDataComponentCall(int opcode, String owner, String name, String descriptor) {
        if (opcode != Opcodes.INVOKEVIRTUAL && opcode != Opcodes.INVOKEINTERFACE) {
            return false;
        }
        if (!"has".equals(name) || descriptor == null) {
            return false;
        }
        if (owner == null) {
            return false;
        }
        if (!"net/minecraft/class_1799".equals(owner)
            && !"net/minecraft/world/item/ItemStack".equals(owner)
            && !"net/minecraft/class_9322".equals(owner)) {
            return false;
        }
        return "(Lnet/minecraft/class_9331;)Z".equals(descriptor)
            || "(Lnet/minecraft/core/component/DataComponentType;)Z".equals(descriptor);
    }

    private static boolean isDataComponentHolderGetCall(int opcode, String owner, String name, String descriptor) {
        if (opcode != Opcodes.INVOKEVIRTUAL && opcode != Opcodes.INVOKEINTERFACE) {
            return false;
        }
        if (!"get".equals(name) || descriptor == null) {
            return false;
        }
        if (owner == null) {
            return false;
        }
        if (!isDataComponentHolderLikeOwner(owner)) {
            return false;
        }
        return "(Lnet/minecraft/class_9331;)Ljava/lang/Object;".equals(descriptor)
            || "(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;".equals(descriptor);
    }

    private static boolean isDataComponentHolderGetOrDefaultCall(int opcode, String owner, String name, String descriptor) {
        if (opcode != Opcodes.INVOKEVIRTUAL && opcode != Opcodes.INVOKEINTERFACE) {
            return false;
        }
        if (!"getOrDefault".equals(name) || descriptor == null) {
            return false;
        }
        if (owner == null || !isDataComponentHolderLikeOwner(owner)) {
            return false;
        }
        return "(Lnet/minecraft/class_9331;Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor)
            || "(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
    }

    private static boolean isDataComponentHolderLikeOwner(String owner) {
        return "net/minecraft/class_1799".equals(owner)
            || "net/minecraft/world/item/ItemStack".equals(owner)
            || "net/minecraft/class_9322".equals(owner)
            || "net/minecraft/core/component/DataComponentHolder".equals(owner);
    }

    private static boolean isItemPropertyRegisterCompat(int opcode, String owner, String name, String descriptor) {
        if (opcode != Opcodes.INVOKESTATIC || descriptor == null || owner == null) {
            return false;
        }
        if (!"net/minecraft/class_5272".equals(owner)
            && !"net/minecraft/client/renderer/item/ItemProperties".equals(owner)) {
            return false;
        }
        if ("method_27879".equals(name)) {
            return "(Lnet/minecraft/class_1792;Lnet/minecraft/class_2960;Lnet/minecraft/class_1800;)V"
                .equals(descriptor);
        }
        if ("register".equals(name)
            && "net/minecraft/client/renderer/item/ItemProperties".equals(owner)) {
            return ("(Lnet/minecraft/world/item/Item;Lnet/minecraft/resources/ResourceLocation;"
                + "Lnet/minecraft/client/renderer/item/ItemPropertyFunction;)V").equals(descriptor);
        }
        return false;
    }

    private static boolean isLegacyBlockBoxToCuboidShape(int opcode, String owner, String name,
            String descriptor) {
        if (opcode != Opcodes.INVOKESTATIC || owner == null || descriptor == null) {
            return false;
        }
        if (!"box".equals(name)) {
            return false;
        }
        if (!descriptor.startsWith("(DDDDDD)")) {
            return false;
        }
        boolean voxelReturn = descriptor.endsWith(")Lnet/minecraft/world/phys/shapes/VoxelShape;")
            || descriptor.endsWith(")Lnet/minecraft/class_265;");
        if (!voxelReturn) {
            return false;
        }
        if ("net/minecraft/world/phys/shapes/Shapes".equals(owner)
                || "net/minecraft/class_259".equals(owner)) {
            return false;
        }
        return true;
    }

    private static boolean isGetConnectedDirectionCall(int opcode, String name, String descriptor) {
        if (opcode != Opcodes.INVOKESTATIC || descriptor == null) {
            return false;
        }
        if (!"getConnectedDirection".equals(name) && !"connectedDirection".equals(name)) {
            return false;
        }
        return descriptor.equals("(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/core/Direction;");
    }

    public static boolean shouldRemap(String className) {
        return !className.startsWith("net/minecraft/")
            && !className.startsWith("net/fabricmc/")
            && !className.startsWith("java/")
            && !className.startsWith("com/mojang/")
            && !className.startsWith("dev.stolym.vyne.mappings/");
    }

    @Override
    protected org.objectweb.asm.MethodVisitor createMethodRemapper(org.objectweb.asm.MethodVisitor methodVisitor) {
        return new org.objectweb.asm.commons.MethodRemapper(api, methodVisitor, remapper) {
            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, org.objectweb.asm.Handle bootstrap,
                                               Object... bootstrapArguments) {
                super.visitInvokeDynamicInsn(lambdaMethodName(name, descriptor, bootstrap, bootstrapArguments),
                        descriptor, bootstrap, bootstrapArguments);
            }
        };
    }

    private String lambdaMethodName(String name, String descriptor, org.objectweb.asm.Handle bootstrap, Object[] arguments) {
        if (!"java/lang/invoke/LambdaMetafactory".equals(bootstrap.getOwner())
                || arguments.length == 0 || !(arguments[0] instanceof org.objectweb.asm.Type samType)) {
            return name;
        }
        String owner = org.objectweb.asm.Type.getReturnType(descriptor).getInternalName();
        return remapper.mapMethodName(owner, name, samType.getDescriptor());
    }

    static final class MojmapRemapper extends Remapper {
        VyneRemapper owner;

        private static boolean usesReadableMojmapNamesAtRuntime(String ownerInternal) {
            if (ownerInternal == null) return false;
            return ownerInternal.startsWith("com/mojang/serialization/")
                || ownerInternal.startsWith("com/mojang/datafixers/")
                || ownerInternal.startsWith("com/mojang/brigadier/")
                || ownerInternal.startsWith("com/mojang/authlib/")
                || ownerInternal.startsWith("com/mojang/logging/")
                || ownerInternal.startsWith("com/mojang/jtracy/")

                || ownerInternal.startsWith("net/neoforged/bus/")
                || ownerInternal.startsWith("net/neoforged/fml/")
                || ownerInternal.startsWith("net/neoforged/api/")
                || ownerInternal.startsWith("net/neoforged/neoforgespi/")

                || ownerInternal.startsWith("dev/stolym/vyne/");
        }

        private ComposedMappings mappings() {
            return MappingRegistry.get();
        }

        @Override
        public String map(String internalName) {
            if (internalName.startsWith("net/minecraftforge/")) {
                if (internalName.startsWith("net/minecraftforge/vyne/")) {
                    return internalName;
                }

                if (internalName.startsWith("net/minecraftforge/fml/")) {
                    return internalName;
                }

                if (internalName.startsWith("net/minecraftforge/common/capabilities/")) {
                    return internalName;
                }

                String neoforgeName = internalName.replace("net/minecraftforge/", "net/neoforged/neoforge/");
                if (neoforgeName.contains("ForgeConfigSpec")) {
                    neoforgeName = neoforgeName.replace("ForgeConfigSpec", "ModConfigSpec");
                }
                return neoforgeName;
            }
            return mappings().mapClass(internalName);
        }

        @Override
        public String mapMethodName(String ownerInternal, String name, String descriptor) {
            if (usesReadableMojmapNamesAtRuntime(ownerInternal)) {
                return name;
            }

            if (owner != null && ownerInternal != null
                    && ownerInternal.equals(owner.currentClassMojmap)) {
                return mappings().mapMethod(
                        ownerInternal,
                        owner.currentSuperMojmap,
                        owner.currentInterfacesMojmap,
                        name, descriptor);
            }
            return mappings().mapMethod(ownerInternal, name, descriptor);
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            if (usesReadableMojmapNamesAtRuntime(owner)) {
                return name;
            }
            return mappings().mapField(owner, name);
        }
    }
}
