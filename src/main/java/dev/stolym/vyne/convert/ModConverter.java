/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import dev.stolym.vyne.util.VyneLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModConverter {
    public static final int SCHEMA = 3;

    private static final String INDEX_FILE = "index.properties";
    private static final Map<String, Path> RUNTIME_JARS = new ConcurrentHashMap<>();
    private static volatile Path cacheBase;

    public record Request(Path input, Path indexSource, String label) {
        public Request(Path input, String label) {
            this(input, input, label);
        }
    }

    public record Summary(int reused, int converted, long millis) {}

    private ModConverter() {}

    public static void setCacheBase(Path base) {
        cacheBase = base;
    }

    public static void reset() {
        RUNTIME_JARS.clear();
    }

    public static Path runtimeJar(Path original) {
        if (original == null) return null;
        Path converted = RUNTIME_JARS.get(key(original));
        return converted != null ? converted : original;
    }

    public static Summary convertAll(List<Request> requests, String environmentKey) throws IOException {
        long start = System.nanoTime();
        if (requests == null || requests.isEmpty()) return new Summary(0, 0, 0);
        if (!dev.stolym.vyne.core.VyneMode.patchedGame()) {
            throw new IllegalStateException("NeoForge mods can only be remapped against NeoForge's patched game");
        }
        Path root = prepareRoot(environmentKey);
        Properties index = loadIndex(root);
        Map<Request, Path> targets = new LinkedHashMap<>();
        List<Request> pending = new ArrayList<>();
        Set<Path> live = new HashSet<>();
        int reused = 0;
        boolean indexChanged = false;

        for (Request request : requests) {
            if (request == null || request.input() == null || !Files.isRegularFile(request.input())) continue;
            if (RUNTIME_JARS.containsKey(key(request.input()))) {
                reused++;
                dev.stolym.vyne.ui.VyneLoadingScreen.modState(request.label(), dev.stolym.vyne.ui.VyneLoadingScreen.MOD_READY);
                continue;
            }
            String[] sha = new String[1];
            indexChanged |= resolveSha(index, request.input(), sha);
            Path target = root.resolve(sha[0] + ".jar");
            targets.put(request, target);
            live.add(target);
            if (Files.isRegularFile(target)) {
                RUNTIME_JARS.put(key(request.input()), target);
                dev.stolym.vyne.ui.VyneLoadingScreen.modState(request.label(), dev.stolym.vyne.ui.VyneLoadingScreen.MOD_READY);
                reused++;
            } else {
                pending.add(request);
            }
        }

        if (!pending.isEmpty()) {
            VyneLogger.info("[Converter] Remapping " + pending.size() + " jar(s) exactly against NeoForge's patched game.");
            dev.stolym.vyne.ui.VyneLoadingScreen.update("Remapping mods (first launch only)", 54);
            List<Path> inputs = new ArrayList<>();
            List<Path> outputs = new ArrayList<>();
            for (Request request : pending) {
                inputs.add(request.input());
                outputs.add(targets.get(request));
            }
            String mc = dev.stolym.vyne.core.VyneLauncher.TARGET_MC;
            try {
                ExactRemap.remap(inputs, outputs, dev.stolym.vyne.core.VyneMode.remapClasspath(),
                        ExactRemap.mappings(dev.stolym.vyne.mappings.MojmapDownloader.getIntermediaryPath(mc),
                                dev.stolym.vyne.mappings.MojmapDownloader.getMojmapPath(mc)));
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Remapping NeoForge mods failed: " + e, e);
            }
            for (Request request : pending) {
                RUNTIME_JARS.put(key(request.input()), targets.get(request));
                dev.stolym.vyne.ui.VyneLoadingScreen.modState(request.label(), dev.stolym.vyne.ui.VyneLoadingScreen.MOD_READY);
                dev.stolym.vyne.ui.VyneLoadingScreen.activity("Remapped " + request.label());
            }
        }

        if (reused > 0) {
            dev.stolym.vyne.ui.VyneLoadingScreen.activity("Reused " + reused + " remapped jar(s) from cache");
        }
        if (indexChanged) storeIndex(root, index);
        prune(root, live);

        long millis = (System.nanoTime() - start) / 1_000_000;
        VyneLogger.info("[Converter] " + reused + " jar(s) reused, " + pending.size() + " remapped in " + millis + " ms");
        return new Summary(reused, pending.size(), millis);
    }

    public static void registerConverted(Path input, Path converted) {
        RUNTIME_JARS.put(key(input), converted);
    }

    private static String key(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static Path prepareRoot(String environmentKey) throws IOException {
        Path base = cacheBase;
        if (base == null) {
            String gameDir = System.getProperty("vyne.gameDir");
            Path dir = (gameDir != null && !gameDir.isBlank())
                    ? Path.of(gameDir)
                    : Path.of(System.getProperty("user.dir", "."));
            base = dir.toAbsolutePath().normalize().resolve(".vyne").resolve("cache").resolve("converted");
        }
        String identity = SCHEMA + "|" + (environmentKey != null ? environmentKey : "");
        Path root = base.resolve(SCHEMA + "-" + Integer.toHexString(identity.hashCode()));
        Files.createDirectories(root);
        try (DirectoryStream<Path> siblings = Files.newDirectoryStream(base)) {
            for (Path sibling : siblings) {
                if (!sibling.equals(root) && Files.isDirectory(sibling)) deleteTree(sibling);
            }
        }
        return root;
    }

    private static void deleteTree(Path dir) {
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static void prune(Path root, Set<Path> live) {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(root, "*.jar")) {
            for (Path file : files) {
                if (!live.contains(file)) Files.deleteIfExists(file);
            }
        } catch (IOException ignored) {
        }
    }

    private static Properties loadIndex(Path root) {
        Properties index = new Properties();
        Path file = root.resolve(INDEX_FILE);
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                index.load(reader);
            } catch (IOException | IllegalArgumentException e) {
                index.clear();
            }
        }
        return index;
    }

    private static void storeIndex(Path root, Properties index) {
        try (Writer writer = Files.newBufferedWriter(root.resolve(INDEX_FILE), StandardCharsets.UTF_8)) {
            index.store(writer, null);
        } catch (IOException e) {
            VyneLogger.debug("[Converter] Could not store the jar index: " + e.getMessage());
        }
    }

    private static boolean resolveSha(Properties index, Path jar, String[] out) throws IOException {
        String pathKey = key(jar);
        String stamp = Files.size(jar) + ":" + Files.getLastModifiedTime(jar).toMillis();
        String known = index.getProperty(pathKey);
        if (known != null) {
            int split = known.lastIndexOf(':');
            if (split > 0 && known.substring(0, split).equals(stamp)) {
                out[0] = known.substring(split + 1);
                return false;
            }
        }
        out[0] = sha256(jar);
        index.setProperty(pathKey, stamp + ":" + out[0]);
        return true;
    }

    private static String sha256(Path jar) throws IOException {
        try (InputStream in = Files.newInputStream(jar)) {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
