/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.world;

import dev.stolym.vyne.config.VyneContainmentConfig;
import dev.stolym.vyne.util.VyneLogger;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class VyneWorldSnapshot {
    private VyneWorldSnapshot() {}

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static final String[] CRITICAL_PATHS = {
        "level.dat",
        "level.dat_old",
        "playerdata",
        "advancements",
        "stats",
        "data",
        "scoreboard.dat",
    };

    private static final Path SNAPSHOT_ROOT = Paths.get(".vyne", "snapshots");

    private static volatile Path lastSnapshotPath = null;

    public static void takeStartupSnapshot(Path worldDir) {
        if (!VyneContainmentConfig.isSnapshotEnabled()) {
            VyneLogger.debug("[VyneSnapshot] Snapshots disabled in config — skipping.");
            return;
        }
        if (worldDir == null || !Files.isDirectory(worldDir)) {
            VyneLogger.warn("[VyneSnapshot] World directory not found: " + worldDir + " — skipping snapshot.");
            return;
        }
        String label = "startup-" + LocalDateTime.now().format(TS);
        Path dest = SNAPSHOT_ROOT.resolve(label);
        try {
            Files.createDirectories(dest);
            long bytesCopied = 0;
            List<String> copied = new ArrayList<>();
            List<String> skipped = new ArrayList<>();

            for (String relPath : CRITICAL_PATHS) {
                Path source = worldDir.resolve(relPath);
                if (!Files.exists(source)) {
                    skipped.add(relPath);
                    continue;
                }
                Path target = dest.resolve(relPath);
                if (Files.isDirectory(source)) {
                    bytesCopied += copyDirectory(source, target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    bytesCopied += Files.size(target);
                }
                copied.add(relPath);
            }

            String manifest = "Vyne Startup Snapshot\n"
                    + "Timestamp : " + LocalDateTime.now() + "\n"
                    + "World     : " + worldDir.toAbsolutePath() + "\n"
                    + "Size      : " + formatBytes(bytesCopied) + "\n\n"
                    + "Copied:\n" + String.join("\n", copied.stream().map(s -> "  " + s).toList()) + "\n\n"
                    + "Skipped (not present):\n"
                    + (skipped.isEmpty() ? "  (none)\n" :
                       String.join("\n", skipped.stream().map(s -> "  " + s).toList()) + "\n");
            Files.writeString(dest.resolve("vyne-snapshot.txt"), manifest);

            lastSnapshotPath = dest;
            VyneLogger.info("[VyneSnapshot] Snapshot taken: " + dest.getFileName()
                    + " (" + formatBytes(bytesCopied) + " of critical files)");

            pruneOldSnapshots(VyneContainmentConfig.getSnapshotKeepCount());
        } catch (IOException e) {
            VyneLogger.warn("[VyneSnapshot] Failed to take snapshot: " + e.getMessage());
        }
    }

    public static void offerRollback(String reason) {
        if (lastSnapshotPath == null) {
            VyneLogger.error("[VyneSnapshot] No snapshot available — cannot offer rollback.");
            return;
        }
        String msg = "\n"
            + "╔══════════════════════════════════════════════════════╗\n"
            + "║             VYNE — WORLD BACKUP AVAILABLE            ║\n"
            + "╠══════════════════════════════════════════════════════╣\n"
            + "║                                                      ║\n"
            + "║  A critical failure forced Vyne to shut down.        ║\n"
            + "║  Game state may be inconsistent.                     ║\n"
            + "║                                                      ║\n"
            + "║  FAILURE: " + fitTo("" + reason, 42) + "  ║\n"
            + "║                                                      ║\n"
            + "║  A backup of your critical world data was saved at:  ║\n"
            + "║  " + fitTo(lastSnapshotPath.toString(), 50) + "  ║\n"
            + "║                                                      ║\n"
            + "║  TO RESTORE:                                         ║\n"
            + "║  1. Stop the server completely.                      ║\n"
            + "║  2. Copy the files from the snapshot folder          ║\n"
            + "║     back into your world folder.                     ║\n"
            + "║  3. Remove the broken mod and restart.               ║\n"
            + "║                                                      ║\n"
            + "║  Your terrain (region files) was NOT touched.        ║\n"
            + "║                                                      ║\n"
            + "╚══════════════════════════════════════════════════════╝\n";
        System.err.println(msg);
        VyneLogger.error(msg);
    }

    private static long copyDirectory(Path source, Path target) throws IOException {
        final long[] total = {0};
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Path dest = target.resolve(source.relativize(file));
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                total[0] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });
        return total[0];
    }

    public static void pruneOldSnapshots(int keepCount) {
        try {
            if (!Files.isDirectory(SNAPSHOT_ROOT)) return;
            List<Path> snapshots = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(SNAPSHOT_ROOT, "startup-*")) {
                ds.forEach(snapshots::add);
            }
            snapshots.sort(Comparator.reverseOrder());
            for (int i = keepCount; i < snapshots.size(); i++) {
                deleteRecursive(snapshots.get(i));
                VyneLogger.debug("[VyneSnapshot] Pruned old snapshot: " + snapshots.get(i).getFileName());
            }
        } catch (IOException e) {
            VyneLogger.warn("[VyneSnapshot] Prune failed: " + e.getMessage());
        }
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024));
    }

    private static String fitTo(String s, int width) {
        if (s.length() > width) return s.substring(0, width - 1) + "…";
        return s + " ".repeat(width - s.length());
    }
}
