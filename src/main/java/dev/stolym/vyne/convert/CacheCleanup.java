/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import dev.stolym.vyne.util.VyneLogger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class CacheCleanup {
    private static final List<String> LEGACY = List.of("remap");

    private CacheCleanup() {}

    public static void removeLegacy(Path gameDir) {
        Path cache = gameDir.resolve(".vyne").resolve("cache");
        for (String name : LEGACY) {
            Path dir = cache.resolve(name);
            if (Files.isDirectory(dir)) {
                deleteTree(dir);
                VyneLogger.info("[Cache] Removed unused cache folder " + dir.getFileName());
            }
        }
    }

    public static void keepOnly(Path parent, Path current) {
        if (!Files.isDirectory(parent)) return;
        try (DirectoryStream<Path> children = Files.newDirectoryStream(parent)) {
            for (Path child : children) {
                if (Files.isDirectory(child) && !child.equals(current)) deleteTree(child);
            }
        } catch (IOException e) {
            VyneLogger.debug("[Cache] Could not prune " + parent + ": " + e.getMessage());
        }
    }

    static void deleteTree(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
