/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.vcl.metadata.VyneMetadataIndex;
import dev.stolym.vyne.util.VyneLogger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class VyneTransformationGate {
    private static final Set<String> SCOPE_WARNINGS = ConcurrentHashMap.newKeySet();

    public static byte[] transform(String name, byte[] b) {
        if (b == null) return null;
        String dottedName = name.replace('/', '.');
        if (isCoreLibrary(dottedName) || dottedName.startsWith("dev.stolym.vyne.") || dottedName.startsWith("net.fabricmc.")
                || dottedName.startsWith("net.minecraft.")) {
            return b;
        }
        LoaderAudit.record(dottedName);
        VyneMetadataIndex.ClassInfo info = VyneMetadataIndex.get(dottedName);
        if (info == null) return b;
        if (info.loaderType() == DetectedMod.LoaderType.FABRIC) {
            warnScopeViolation(dottedName, info.loaderType());
            return b;
        }
        enforceSideOnly(dottedName, info.loaderType(), b);
        return b;
    }

    private static void warnScopeViolation(String dottedName, DetectedMod.LoaderType loaderType) {
        if (SCOPE_WARNINGS.add(dottedName)) {
            VyneLogger.debug("[VyneGate] Refusing to transform " + dottedName
                    + " (owner mod is " + loaderType + "-native; Fabric mods must run untransformed)");
        }
    }

    private static final String[] ONLY_IN_DESCRIPTORS = {
            "Lnet/neoforged/api/distmarker/OnlyIn;",
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
        if (loaderType != DetectedMod.LoaderType.NEOFORGE && loaderType != DetectedMod.LoaderType.UNKNOWN) {
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
