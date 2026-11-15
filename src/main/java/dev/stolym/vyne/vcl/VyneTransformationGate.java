/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.mappings.MappingRegistry;
import dev.stolym.vyne.vcl.metadata.MetadataClassWriter;
import dev.stolym.vyne.vcl.metadata.VyneMetadataIndex;
import dev.stolym.vyne.util.VyneLogger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class VyneTransformationGate {
    private static final boolean FAIL_OPEN =
            Boolean.getBoolean("vyne.gate.failOpen") || System.getProperty("vyne.gate.failOpen") == null;

    private static final Set<String> SCOPE_WARNINGS = ConcurrentHashMap.newKeySet();

    public static byte[] transform(String name, byte[] b) {
        if (b == null) return null;

        String dottedName = name.replace('/', '.');

        if (isCoreLibrary(dottedName)) {
            return b;
        }

        if (dottedName.startsWith("dev.stolym.vyne.") || dottedName.startsWith("net.fabricmc.")) {
            return b;
        }

        LoaderAudit.record(dottedName);

        if (dottedName.startsWith("net.minecraft.")) {
            boolean isMappedRegistry = dottedName.equals("net.minecraft.class_2385") ||
                                       dottedName.equals("net.minecraft.class_2378");
            if (!isMappedRegistry) {
                return b;
            }
            if (!VyneMetadataIndex.contains(dottedName)) {
                VyneLogger.warn("[VyneGate] WARNING: " + dottedName + " is missing from metadata index! Interception will be skipped.");
            }
        }

        if (!MappingRegistry.isReady()) {
            return b;
        }

        VyneMetadataIndex.ClassInfo info = VyneMetadataIndex.get(dottedName);
        if (info == null) {
            return b;
        }

        if (info.loaderType() == DetectedMod.LoaderType.FABRIC) {
            warnScopeViolation(dottedName, info.loaderType());
            return b;
        }

        enforceSideOnly(dottedName, info.loaderType(), b);

        String internalName = dottedName.replace('.', '/');
        if (dev.stolym.vyne.convert.ModConverter.isPreConverted(info.sourceJar(), internalName)) {
            return b;
        }

        String jarSha = RemapCache.jarSha256(info.sourceJar() != null ? Path.of(info.sourceJar()) : null);

        byte[] cached = RemapCache.get(jarSha, internalName);
        if (cached != null) {
            return cached;
        }

        try {
            byte[] transformed = translate(dottedName, b);
            RemapCache.put(jarSha, internalName, transformed);
            return transformed;
        } catch (Throwable t) {
            String owner = ownerModId(info);
            VyneLogger.error("[VyneGate] Transformation failed for " + dottedName
                    + (owner != null ? " (mod '" + owner + "')" : "")
                    + ": " + t.getMessage(), t);
            if (!FAIL_OPEN) {
                if (t instanceof RuntimeException rt) throw rt;
                throw new IllegalStateException("[VyneGate] Transform failed (vyne.gate.failOpen=false)", t);
            }
            return b;
        }
    }

    public static boolean isTranslatable(String dottedName) {
        return !isCoreLibrary(dottedName)
                && !dottedName.startsWith("dev.stolym.vyne.")
                && !dottedName.startsWith("net.fabricmc.")
                && !dottedName.startsWith("net.minecraft.");
    }

    public static byte[] translate(String dottedName, byte[] b) {
        boolean minecraftClass = dottedName.startsWith("net.minecraft.");
        byte[] input = minecraftClass ? b : dev.stolym.vyne.mixin.MixinAnnotationRemapper.remap(b, MappingRegistry.get());
        ClassReader reader = new ClassReader(input);

        MetadataClassWriter writer = new MetadataClassWriter(reader, ClassWriter.COMPUTE_FRAMES);

        org.objectweb.asm.ClassVisitor interceptor = new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9, writer) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                if (interfaces != null && interfaces.length > 1) {
                    java.util.LinkedHashSet<String> unique =
                            new java.util.LinkedHashSet<>(java.util.Arrays.asList(interfaces));
                    if (unique.size() != interfaces.length) {
                        interfaces = unique.toArray(new String[0]);
                    }
                }
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                org.objectweb.asm.MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                if (name.equals("method_40276") || (name.equals("freeze") && descriptor.endsWith("Lnet/minecraft/class_2378;"))) {
                    return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            super.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
                            super.visitInsn(org.objectweb.asm.Opcodes.ARETURN);
                        }

                        @Override
                        public void visitMaxs(int maxStack, int maxLocals) {
                            super.visitMaxs(1, 1);
                        }
                    };
                }
                return mv;
            }
        };

        reader.accept(minecraftClass
                ? interceptor
                : new net.fabricmc.loader.impl.transformer.VyneRemapper(org.objectweb.asm.Opcodes.ASM9, interceptor), 0);

        byte[] out = writer.toByteArray();
        return dev.stolym.vyne.core.VyneMode.patchedGame() ? dev.stolym.vyne.convert.Routing.apply(out) : out;
    }

    private static void warnScopeViolation(String dottedName, DetectedMod.LoaderType loaderType) {
        if (SCOPE_WARNINGS.add(dottedName)) {
            VyneLogger.debug("[VyneGate] Refusing to transform " + dottedName
                    + " (owner mod is " + loaderType + "-native; Fabric mods must run untransformed)");
        }
    }

    private static final String[] ONLY_IN_DESCRIPTORS = {
            "Lnet/neoforged/api/distmarker/OnlyIn;",
            "Lnet/minecraftforge/api/distmarker/OnlyIn;",
    };

    private static final boolean ON_DEDICATED_SERVER = detectDedicatedServer();

    private static boolean detectDedicatedServer() {
        try {
            Class<?> loader = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object inst = loader.getMethod("getInstance").invoke(null);
            Object env = loader.getMethod("getEnvironmentType").invoke(inst);
            return env != null && "SERVER".equals(env.toString());
        } catch (Throwable t) {
            return false;
        }
    }

    private static void enforceSideOnly(String dottedName, DetectedMod.LoaderType loaderType, byte[] b) {
        if (loaderType != DetectedMod.LoaderType.NEOFORGE
                && loaderType != DetectedMod.LoaderType.FORGE
                && loaderType != DetectedMod.LoaderType.UNKNOWN) {
            return;
        }
        try {
            org.objectweb.asm.ClassReader reader = new org.objectweb.asm.ClassReader(b);
            String[] side = new String[1];
            reader.accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature,
                                  String superName, String[] interfaces) {
                }

                @Override
                public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    for (String onlyIn : ONLY_IN_DESCRIPTORS) {
                        if (descriptor.equals(onlyIn)) {
                            return new org.objectweb.asm.AnnotationVisitor(org.objectweb.asm.Opcodes.ASM9) {
                                @Override
                                public void visit(String name, Object value) {
                                    if (value instanceof String[] arr && arr.length == 2) {
                                        side[0] = arr[1];
                                    }
                                }
                            };
                        }
                    }
                    return null;
                }
            }, org.objectweb.asm.ClassReader.SKIP_CODE
                    | org.objectweb.asm.ClassReader.SKIP_DEBUG
                    | org.objectweb.asm.ClassReader.SKIP_FRAMES);

            if (side[0] == null) return;
            boolean clientOnly = side[0].equals("CLIENT");
            boolean serverOnly = side[0].equals("DEDICATED_SERVER");
            if ((clientOnly && ON_DEDICATED_SERVER) || (serverOnly && !ON_DEDICATED_SERVER)) {
                throw new NoClassDefFoundError(dottedName
                        + " is @OnlyIn(" + side[0] + ") and cannot be loaded on the "
                        + (ON_DEDICATED_SERVER ? "dedicated server" : "client")
                        + " — the mod must guard it with DistExecutor or a dist check");
            }
        } catch (NoClassDefFoundError e) {
            throw e;
        } catch (Throwable t) {
            VyneLogger.debug("[VyneGate] @OnlyIn inspection skipped for " + dottedName + ": " + t);
        }
    }

    private static String ownerModId(VyneMetadataIndex.ClassInfo info) {
        String jar = info.sourceJar();
        if (jar == null) return null;
        for (var mod : dev.stolym.vyne.core.VyneLauncher.getDetectedMods()) {
            Path modJar = mod.getJarPath();
            if (modJar != null && modJar.toAbsolutePath().normalize().toString().equals(jar)) {
                return mod.getModId();
            }
        }
        return null;
    }

    private static boolean isCoreLibrary(String name) {
        return name.startsWith("java.") ||
               name.startsWith("javax.") ||
               name.startsWith("jdk.") ||
               name.startsWith("sun.") ||
               name.startsWith("org.apache.logging.") ||
               name.startsWith("org.objectweb.asm.") ||
               name.startsWith("com.google.common.") ||
               name.startsWith("com.google.gson.") ||
               name.startsWith("org.slf4j.") ||
               name.startsWith("org.joml.") ||
               name.startsWith("org.lwjgl.") ||
               name.startsWith("it.unimi.dsi.fastutil.") ||
               name.startsWith("com.mojang.math.") ||
               name.startsWith("org.apache.commons.") ||
               name.startsWith("joptsimple.");
    }
}
