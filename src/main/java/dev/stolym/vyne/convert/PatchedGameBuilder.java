/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import dev.stolym.vyne.util.VyneLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class PatchedGameBuilder {
    public static final int SCHEMA = 3;
    private static final String[] CLIENT_ONLY = {"net/minecraft/client/", "com/mojang/blaze3d/", "com/mojang/realmsclient/"};

    public record Result(Path game, Map<Path, Path> converted) {}

    private PatchedGameBuilder() {}

    public static Result build(Path patchedJar, List<Path> otherInputs, Collection<Path> classpath, Path intermediaryTiny,
                               Path mojangMappings, Path cacheDir, boolean dedicatedServer) throws IOException {
        StringBuilder key = new StringBuilder().append(SCHEMA).append('|').append(dedicatedServer).append('|')
                .append(Routing.fingerprint()).append('|').append(stamp(patchedJar)).append('|')
                .append(stamp(intermediaryTiny)).append('|').append(stamp(mojangMappings));
        for (Path input : otherInputs) key.append('|').append(input.toAbsolutePath()).append('=').append(stamp(input));
        Path dir = cacheDir.resolve(Integer.toHexString(key.toString().hashCode()));
        Path game = dir.resolve("game.jar");
        Map<Path, Path> converted = new LinkedHashMap<>();
        for (int i = 0; i < otherInputs.size(); i++) {
            converted.put(otherInputs.get(i), dir.resolve(i + "-" + otherInputs.get(i).getFileName()));
        }
        boolean cached = Files.isRegularFile(game);
        for (Path out : converted.values()) cached &= Files.isRegularFile(out);
        if (cached) return new Result(game, converted);

        long start = System.nanoTime();
        Files.createDirectories(dir);
        Path input = dedicatedServer ? withoutClientClasses(patchedJar, dir) : patchedJar;
        try {
            List<Path> inputs = new ArrayList<>();
            List<Path> outputs = new ArrayList<>();
            inputs.add(input);
            outputs.add(game);
            for (Map.Entry<Path, Path> entry : converted.entrySet()) {
                inputs.add(entry.getKey());
                outputs.add(entry.getValue());
            }
            ExactRemap.remap(inputs, outputs, classpath, ExactRemap.mappings(intermediaryTiny, mojangMappings));
        } finally {
            if (input != patchedJar) Files.deleteIfExists(input);
        }
        try (var siblings = Files.newDirectoryStream(cacheDir)) {
            for (Path sibling : siblings) {
                if (Files.isDirectory(sibling) && !sibling.equals(dir) && sibling.getFileName().toString().matches("-?[0-9a-f]+")) {
                    deleteTree(sibling);
                }
            }
        }
        VyneLogger.info("[PatchedGame] Prepared NeoForge's patched game and " + otherInputs.size() + " jar(s) in "
                + (System.nanoTime() - start) / 1_000_000 + " ms");
        return new Result(game, converted);
    }

    private static void deleteTree(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static Path withoutClientClasses(Path jar, Path dir) throws IOException {
        java.util.Map<String, byte[]> classes = new java.util.HashMap<>();
        java.util.Map<String, byte[]> resources = new LinkedHashMap<>();
        try (ZipFile in = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : Collections.list(in.entries())) {
                if (entry.isDirectory()) continue;
                try (InputStream data = in.getInputStream(entry)) {
                    byte[] bytes = data.readAllBytes();
                    if (entry.getName().endsWith(".class")) classes.put(entry.getName(), bytes);
                    else resources.put(entry.getName(), bytes);
                }
            }
        }
        java.util.Set<String> keep = new java.util.HashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        for (String name : classes.keySet()) {
            if (!isClientOnly(name)) queue.add(name);
        }
        while (!queue.isEmpty()) {
            String name = queue.poll();
            if (!keep.add(name)) continue;
            for (String ref : references(classes.get(name))) {
                String file = ref + ".class";
                if (classes.containsKey(file) && !keep.contains(file)) queue.add(file);
            }
        }
        Path filtered = dir.resolve("server-input-" + ProcessHandle.current().pid() + ".jar");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(filtered))) {
            for (String name : keep) {
                out.putNextEntry(new ZipEntry(name));
                out.write(classes.get(name));
                out.closeEntry();
            }
            for (Map.Entry<String, byte[]> resource : resources.entrySet()) {
                out.putNextEntry(new ZipEntry(resource.getKey()));
                out.write(resource.getValue());
                out.closeEntry();
            }
        }
        long clientKept = keep.stream().filter(PatchedGameBuilder::isClientOnly).count();
        VyneLogger.info("[PatchedGame] Dedicated server: kept " + clientKept + " client class(es) that server code uses");
        return filtered;
    }

    private static java.util.Set<String> references(byte[] bytes) {
        java.util.Set<String> out = new java.util.HashSet<>();
        org.objectweb.asm.commons.Remapper collector = new org.objectweb.asm.commons.Remapper() {
            @Override
            public String map(String internalName) {
                out.add(internalName);
                return internalName;
            }
        };
        new org.objectweb.asm.ClassReader(bytes).accept(new org.objectweb.asm.commons.ClassRemapper(
                new org.objectweb.asm.tree.ClassNode(), collector), org.objectweb.asm.ClassReader.SKIP_FRAMES);
        return out;
    }

    private static boolean isClientOnly(String name) {
        for (String prefix : CLIENT_ONLY) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }

    private static String stamp(Path file) throws IOException {
        return Files.size(file) + ":" + Files.getLastModifiedTime(file).toMillis();
    }
}
