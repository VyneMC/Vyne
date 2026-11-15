/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import dev.stolym.vyne.mappings.MojmapDownloader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.proguard.ProGuardFileReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class VanillaGame {
    private VanillaGame() {}

    public static Path mojang(String version, boolean dedicatedServer, Path cacheDir) throws Exception {
        String side = dedicatedServer ? "server" : "client";
        Path out = cacheDir.resolve("vanilla").resolve(version).resolve(side + "-mojang.jar");
        if (Files.isRegularFile(out)) return out;
        Path jar = dedicatedServer ? MojmapDownloader.getServerJar(version) : MojmapDownloader.getClientJar(version);
        Path mappings = dedicatedServer ? MojmapDownloader.getServerMojmapPath(version) : MojmapDownloader.getMojmapPath(version);
        remap(jar, mappings, out);
        return out;
    }

    public static void remap(Path officialJar, Path proguardMappings, Path out) throws IOException {
        MemoryMappingTree tree = new MemoryMappingTree();
        try (Reader reader = Files.newBufferedReader(proguardMappings, StandardCharsets.UTF_8)) {
            ProGuardFileReader.read(reader, "named", "official", new MappingSourceNsSwitch(tree, "official"));
        }
        Files.createDirectories(out.getParent());
        Path classesOnly = out.resolveSibling("input-" + out.getFileName());
        try (ZipFile zip = new ZipFile(officialJar.toFile());
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(classesOnly))) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                zos.putNextEntry(new ZipEntry(entry.getName()));
                try (InputStream in = zip.getInputStream(entry)) {
                    in.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
        Path tmp = out.resolveSibling("tmp-" + out.getFileName());
        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(TinyUtils.createMappingProvider(tree, "official", "named"))
                .renameInvalidLocals(true)
                .rebuildSourceFilenames(true)
                .build();
        try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(tmp).assumeArchive(true).build()) {
            remapper.readInputs(classesOnly);
            remapper.apply(consumer);
        } finally {
            remapper.finish();
            Files.deleteIfExists(classesOnly);
        }
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
    }
}
