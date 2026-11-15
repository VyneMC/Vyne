/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.fml;

import dev.stolym.vyne.core.GameLoader;
import dev.stolym.vyne.core.VyneLauncher;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.IBindingsProvider;
import net.neoforged.fml.loading.VersionInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public class VyneFMLLoader {
    public static Runnable progressWindowTick = () -> { };
    private static volatile IBindingsProvider bindings;

    public static Dist getDist() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType()
                    == net.fabricmc.api.EnvType.CLIENT ? Dist.CLIENT : Dist.DEDICATED_SERVER;
        } catch (Throwable t) {
            return Dist.DEDICATED_SERVER;
        }
    }

    public static boolean isProduction() {
        return true;
    }

    public static VyneLoadingModList getLoadingModList() {
        return VyneLoadingModList.get();
    }

    public static Path getGamePath() {
        return Path.of(System.getProperty("vyne.gameDir", ".")).toAbsolutePath().normalize();
    }

    public static String getLauncherInfo() {
        return "Vyne " + VyneLauncher.VERSION;
    }

    public static List<Map<String, String>> modLauncherModList() {
        return List.of();
    }

    public static String launcherHandlerName() {
        return getDist() == Dist.CLIENT ? "vyneclient" : "vyneserver";
    }

    public static ModuleLayer getGameLayer() {
        return null;
    }

    public static VersionInfo versionInfo() {
        String neo = VyneMods.neoForgeVersion();
        return new VersionInfo(neo, "4.0.45", VyneLauncher.TARGET_MC, VyneLauncher.TARGET_MC);
    }

    public static IBindingsProvider getBindings() {
        IBindingsProvider current = bindings;
        if (current == null) {
            ClassLoader loader = GameLoader.get() != null ? GameLoader.get() : VyneFMLLoader.class.getClassLoader();
            current = ServiceLoader.load(IBindingsProvider.class, loader).findFirst()
                    .orElseThrow(() -> new IllegalStateException("NeoForge's bindings provider is missing"));
            bindings = current;
        }
        return current;
    }
}
