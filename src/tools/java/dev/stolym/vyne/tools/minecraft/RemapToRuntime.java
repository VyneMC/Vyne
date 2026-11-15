/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.tools.minecraft;

import dev.stolym.vyne.convert.ExactRemap;
import dev.stolym.vyne.mappings.MojmapDownloader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RemapToRuntime {
    private RemapToRuntime() {}

    public static void main(String[] args) throws Exception {
        String version = args[0];
        Path in = Path.of(args[1]);
        Path out = Path.of(args[2]);
        List<Path> classpath = new ArrayList<>();
        for (int i = 3; i < args.length; i++) classpath.add(Path.of(args[i]));
        Files.createDirectories(out.getParent());
        ExactRemap.remap(List.of(in), List.of(out), classpath, ExactRemap.mappings(
                MojmapDownloader.getIntermediaryPath(version), MojmapDownloader.getMojmapPath(version)));
        System.out.println("Remapped to runtime names: " + out);
    }
}
