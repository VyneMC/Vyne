/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.core;

public final class LoaderSides {
    private static final String[] LAUNCHER = {
            "dev.stolym.vyne.core.",
            "dev.stolym.vyne.ui.",
            "dev.stolym.vyne.util.",
            "dev.stolym.vyne.config.",
            "dev.stolym.vyne.mappings.",
            "dev.stolym.vyne.convert.",
            "dev.stolym.vyne.engine.detector.",
            "dev.stolym.vyne.engine.conflict.",
            "dev.stolym.vyne.install.",
            "dev.stolym.vyne.containment.",
            "dev.stolym.vyne.vcl.metadata.",
            "dev.stolym.vyne.vcl.VyneTransformationGate",
            "dev.stolym.vyne.vcl.RemapCache",
            "dev.stolym.vyne.vcl.LoaderAudit",
            "dev.stolym.vyne.loaders.KnotModClasspathBridge",
            "dev.stolym.vyne.loaders.RealNeoForgeApiMount",
            "dev.stolym.vyne.loaders.AccessTransformerBridge",
            "dev.stolym.vyne.mixin.MixinAnnotationRemapper",
    };

    private static final String[] GAME_EXCEPTIONS = {
            "dev.stolym.vyne.config.VyneConfigBridge",
            "dev.stolym.vyne.util.ReflectionHelper",
            "dev.stolym.vyne.util.NullMemberWrapper",
    };

    private LoaderSides() {}

    public static boolean isLauncherSide(String className) {
        String name = className.replace('/', '.');
        for (String exception : GAME_EXCEPTIONS) {
            if (name.startsWith(exception)) return false;
        }
        for (String prefix : LAUNCHER) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }
}
