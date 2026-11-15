/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.fml.loading;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

public enum FMLPaths {
    GAMEDIR(() -> Paths.get(".")),
    CONFIGDIR(() -> Paths.get("config")),
    MODSDIR(() -> Paths.get("mods"));

    private final Supplier<Path> pathSupplier;

    FMLPaths(Supplier<Path> pathSupplier) {
        this.pathSupplier = pathSupplier;
    }

    public Path get() {
        Path p = pathSupplier.get();
        System.out.println("[VYNE-FML] FMLPaths." + name() + ".get() -> " + p);
        return p;
    }

    public static Path getOrCreateGameRelativePath(Path relative) {
        Path resolved = GAMEDIR.get().resolve(relative);
        try {
            java.nio.file.Files.createDirectories(resolved);
        } catch (java.io.IOException ignored) {
        }
        return resolved;
    }

    public static Path getOrCreateGameRelativePath(Path relative, String subdir) {
        return getOrCreateGameRelativePath(relative.resolve(subdir));
    }
}
