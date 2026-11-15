/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import dev.stolym.vyne.engine.dynamic.NeoForgeReferenceDownloader;
import dev.stolym.vyne.ui.VyneLoadingScreen;
import dev.stolym.vyne.util.VyneLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public record NeoForgeInstall(String version, Path patchedGame, Path vanillaGame, Path fml, Path bus,
                              List<Path> libraries) {
    private static final int SCHEMA = 1;
    private static final String[][] LIBRARIES = {
            {"cpw/mods/securejarhandler", "securejarhandler"},
            {"org/jline/jline-reader", "jline-reader"},
            {"org/jline/jline-terminal", "jline-terminal"},
            {"net/minecrell/terminalconsoleappender", "terminalconsoleappender"},
    };

    public static NeoForgeInstall ensure(String version, boolean dedicatedServer, Path baseDir) throws IOException {
        String side = dedicatedServer ? "server" : "client";
        Path dir = baseDir.resolve(".vyne").resolve("neoforge").resolve(version).resolve(side);
        Path marker = dir.resolve("install.properties");
        NeoForgeInstall installed = read(version, dir, marker);
        if (installed != null) return installed;

        Path installer = baseDir.resolve(".vyne").resolve("references").resolve("neoforge-" + version + "-installer.jar");
        if (!Files.isRegularFile(installer)
                && !NeoForgeReferenceDownloader.tryDownload(version, installer.getFileName().toString(), installer)) {
            throw new IOException("Could not download NeoForge's installer for " + version);
        }

        long start = System.nanoTime();
        VyneLoadingScreen.update("Installing NeoForge " + version + " (first launch only)", 45);
        Path work = dir.resolveSibling(side + "-install-" + ProcessHandle.current().pid());
        deleteTree(work);
        Files.createDirectories(work);
        try {
            if (!dedicatedServer) Files.writeString(work.resolve("launcher_profiles.json"), "{\"profiles\":{}}");
            runInstaller(installer, work, dedicatedServer);
            Path libraries = work.resolve("libraries");
            Properties found = new Properties();
            Files.createDirectories(dir);
            found.setProperty("patchedGame", copy(libraries.resolve("net/neoforged/neoforge/" + version + "/neoforge-"
                    + version + "-" + side + ".jar"), dir));
            found.setProperty("vanillaGame", copy(newest(libraries.resolve("net/minecraft/" + side), side + "-", "-srg.jar"), dir));
            found.setProperty("fml", copy(newest(libraries.resolve("net/neoforged/fancymodloader/loader"), "loader-", ".jar"), dir));
            found.setProperty("bus", copy(newest(libraries.resolve("net/neoforged/bus"), "bus-", ".jar"), dir));
            List<String> extra = new ArrayList<>();
            for (String[] library : LIBRARIES) {
                Path jar = newest(libraries.resolve(library[0]), library[1] + "-", ".jar");
                if (jar != null) extra.add(copy(jar, dir));
            }
            found.setProperty("libraries", String.join(",", extra));
            found.setProperty("schema", Integer.toString(SCHEMA));
            try (var out = Files.newBufferedWriter(marker)) {
                found.store(out, null);
            }
        } finally {
            deleteTree(work);
        }
        VyneLogger.info("[PatchedGame] Installed NeoForge " + version + " (" + side + ") in "
                + (System.nanoTime() - start) / 1_000_000 + " ms");
        NeoForgeInstall result = read(version, dir, marker);
        if (result == null) throw new IOException("NeoForge's installer did not produce the expected files");
        return result;
    }

    private static void runInstaller(Path installer, Path work, boolean dedicatedServer) throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path log = work.resolve("installer-output.log");
        ProcessBuilder builder = new ProcessBuilder(java, "-jar", installer.toAbsolutePath().toString(),
                dedicatedServer ? "--install-server" : "--install-client", work.toAbsolutePath().toString())
                .directory(work.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        Process process = builder.start();
        try {
            if (!process.waitFor(15, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException("NeoForge's installer did not finish within 15 minutes");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running NeoForge's installer", e);
        }
        if (process.exitValue() != 0) {
            String tail = "";
            try {
                List<String> lines = Files.readAllLines(log);
                tail = String.join("\n", lines.subList(Math.max(0, lines.size() - 15), lines.size()));
            } catch (IOException ignored) {
            }
            throw new IOException("NeoForge's installer failed (exit " + process.exitValue() + "):\n" + tail);
        }
    }

    private static NeoForgeInstall read(String version, Path dir, Path marker) throws IOException {
        if (!Files.isRegularFile(marker)) return null;
        Properties props = new Properties();
        try (var in = Files.newBufferedReader(marker)) {
            props.load(in);
        }
        if (!Integer.toString(SCHEMA).equals(props.getProperty("schema"))) return null;
        Path patched = file(dir, props.getProperty("patchedGame"));
        Path vanilla = file(dir, props.getProperty("vanillaGame"));
        Path fml = file(dir, props.getProperty("fml"));
        Path bus = file(dir, props.getProperty("bus"));
        if (patched == null || vanilla == null || fml == null || bus == null) return null;
        List<Path> libraries = new ArrayList<>();
        for (String name : props.getProperty("libraries", "").split(",")) {
            Path jar = file(dir, name);
            if (jar != null) libraries.add(jar);
        }
        return new NeoForgeInstall(version, patched, vanilla, fml, bus, List.copyOf(libraries));
    }

    private static Path file(Path dir, String name) {
        if (name == null || name.isBlank()) return null;
        Path path = dir.resolve(name);
        return Files.isRegularFile(path) ? path : null;
    }

    private static String copy(Path source, Path dir) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("NeoForge's installer did not produce " + source);
        }
        Files.copy(source, dir.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        return source.getFileName().toString();
    }

    private static Path newest(Path artifactDir, String prefix, String suffix) throws IOException {
        if (!Files.isDirectory(artifactDir)) return null;
        try (Stream<Path> walk = Files.walk(artifactDir, 2)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(prefix) && name.endsWith(suffix)
                                && (suffix.equals("-srg.jar") || !name.substring(prefix.length()).contains("-"));
                    })
                    .max(Comparator.comparing(p -> p.getParent().getFileName().toString()))
                    .orElse(null);
        }
    }

    private static void deleteTree(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
