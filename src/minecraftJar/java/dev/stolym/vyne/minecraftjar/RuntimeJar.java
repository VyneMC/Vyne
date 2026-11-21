/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.minecraftjar;

import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class RuntimeJar {
    private RuntimeJar() {}

    public static void main(String[] args) throws Exception {
        Path clientJar = Path.of(args[0]);
        Path intermediaryJar = Path.of(args[1]);
        Path out = Path.of(args[2]);
        MemoryMappingTree tree = new MemoryMappingTree();
        try (ZipFile mappings = new ZipFile(intermediaryJar.toFile());
             Reader reader = new InputStreamReader(mappings.getInputStream(mappings.getEntry("mappings/mappings.tiny")), StandardCharsets.UTF_8)) {
            Tiny2FileReader.read(reader, tree);
        }
        Files.createDirectories(out.getParent());
        Path classesOnly = out.resolveSibling("input-" + out.getFileName());
        try (ZipFile zip = new ZipFile(clientJar.toFile());
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
                .withMappings(TinyUtils.createMappingProvider(tree, "official", "intermediary"))
                .renameInvalidLocals(true)
                .build();
        try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(tmp).assumeArchive(true).build()) {
            remapper.readInputs(classesOnly);
            remapper.apply(consumer);
        } finally {
            remapper.finish();
            Files.deleteIfExists(classesOnly);
        }
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Minecraft with runtime (intermediary) names: " + out);
    }
}
