/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.tools.minecraft;

import dev.stolym.vyne.mappings.MojmapDownloader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.proguard.ProGuardFileReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;

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

public final class MojangJar {
    private MojangJar() {}

    public static void main(String[] args) throws Exception {
        String version = args[0];
        Path out = Path.of(args[1]);
        Path client = MojmapDownloader.getClientJar(version);
        Path mappings = MojmapDownloader.getMojmapPath(version);

        MemoryMappingTree tree = new MemoryMappingTree();
        try (Reader reader = Files.newBufferedReader(mappings, StandardCharsets.UTF_8)) {
            ProGuardFileReader.read(reader, "named", "official", new MappingSourceNsSwitch(tree, "official"));
        }

        Path classesOnly = out.resolveSibling("input-" + out.getFileName());
        try (ZipFile zip = new ZipFile(client.toFile());
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

        Files.createDirectories(out.getParent());
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
        System.out.println("Minecraft " + version + " with Mojang names: " + out);
    }
}
