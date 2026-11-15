/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.fml.loading;

import java.nio.file.Path;
import java.nio.file.Paths;

public class FMLLoader {
    public static Path getGamePath() {
        System.out.println("[VYNE-FML] FMLLoader.getGamePath() called!");
        return Paths.get(".");
    }

    public static Path getConfigPath() {
        System.out.println("[VYNE-FML] FMLLoader.getConfigPath() called!");
        return Paths.get("config");
    }

    public static boolean isProduction() {
        System.out.println("[VYNE-FML] FMLLoader.isProduction() called!");
        return true;
    }

    public static net.neoforged.api.distmarker.Dist getDist() {
        System.out.println("[VYNE-FML] FMLLoader.getDist() called!");
        return net.neoforged.fml.loading.FMLEnvironment.dist;
    }

    public static boolean isData() {
        return false;
    }

    public static String launcherVersion() {
        return "Vyne-0.1.0-alpha";
    }

    public static boolean isDataGen() {
        return false;
    }
}
