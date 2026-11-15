/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.fml.loading;

import java.nio.file.Path;
import java.nio.file.Paths;

public class FMLConfig {
    public static Path getConfigPath() {
        System.out.println("[VYNE-FML] FMLConfig.getConfigPath() called!");
        return Paths.get("config");
    }

    public static Path getDefaultConfigPath() {
        System.out.println("[VYNE-FML] FMLConfig.getDefaultConfigPath() called!");
        return Paths.get("config");
    }
}
