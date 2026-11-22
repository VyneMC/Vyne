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
        if (info.loaderType() != DetectedMod.LoaderType.NEOFORGE && info.loaderType() != DetectedMod.LoaderType.UNKNOWN) return b;
        return DistCleaner.clean(dottedName, b, ON_DEDICATED_SERVER);
    }

    private static void warnScopeViolation(String dottedName, DetectedMod.LoaderType loaderType) {
        if (SCOPE_WARNINGS.add(dottedName)) {
            VyneLogger.debug("[VyneGate] Refusing to transform " + dottedName
                    + " (owner mod is " + loaderType + "-native; Fabric mods must run untransformed)");
        }
    }

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
