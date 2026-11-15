/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.tools.minecraft;

import dev.stolym.vyne.convert.VanillaGame;
import dev.stolym.vyne.mappings.MojmapDownloader;

import java.nio.file.Path;

public final class MojangJar {
    private MojangJar() {}

    public static void main(String[] args) throws Exception {
        String version = args[0];
        Path out = Path.of(args[1]);
        VanillaGame.remap(MojmapDownloader.getClientJar(version), MojmapDownloader.getMojmapPath(version), out);
        System.out.println("Minecraft " + version + " with Mojang names: " + out);
    }
}
