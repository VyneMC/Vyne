/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class VyneStubDefaults {
    public static Object defaultFor(String descriptor) {
        if (descriptor == null) return null;
        return switch (descriptor) {
            case "Ljava/nio/file/Path;" -> resolveConfigPath();
            case "Ljava/util/List;"     -> new ArrayList<>();
            case "Ljava/util/Map;"      -> new HashMap<>();
            case "Ljava/util/Set;"      -> new HashSet<>();
            case "Ljava/util/Optional;" -> Optional.empty();
            case "Ljava/lang/String;"   -> "";
            case "Z" -> false;
            case "B", "C", "S", "I" -> 0;
            case "J" -> 0L;
            case "F" -> 0.0f;
            case "D" -> 0.0d;
            default -> {
                if (descriptor.startsWith("Lnet/minecraft/")) {
                    yield null; 
                }
                yield null;
            }
        };
    }

    private static Path resolveConfigPath() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
        } catch (Throwable t) {
            return Paths.get("config");
        }
    }
}
