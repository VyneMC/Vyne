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
        List<Path> inputs = new ArrayList<>(List.of(in));
        List<Path> outputs = new ArrayList<>(List.of(out));
        List<Path> classpath = new ArrayList<>();
        boolean hierarchy = true;
        for (int i = 3; i < args.length; i++) {
            if (args[i].equals("--")) {
                hierarchy = false;
            } else if (hierarchy) {
                inputs.add(Path.of(args[i]));
                outputs.add(out.resolveSibling("hierarchy-" + i + "-" + Path.of(args[i]).getFileName()));
            } else {
                classpath.add(Path.of(args[i]));
            }
        }
        Files.createDirectories(out.getParent());
        try {
            ExactRemap.remap(inputs, outputs, classpath, ExactRemap.mappings(
                    MojmapDownloader.getIntermediaryPath(version), MojmapDownloader.getMojmapPath(version)));
        } finally {
            for (Path discard : outputs.subList(1, outputs.size())) Files.deleteIfExists(discard);
        }
        System.out.println("Remapped to runtime names: " + out);
    }
}
