/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.minecraftjar;

import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.proguard.ProGuardFileReader;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class MojangNamedJar {
    private MojangNamedJar() {}

    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]);
        Path intermediaryJar = Path.of(args[1]);
        Path mojangMappings = Path.of(args[2]);
        Path minecraftRuntime = Path.of(args[3]);
        Path out = Path.of(args[4]);

        MemoryMappingTree tree = new MemoryMappingTree();
        try (ZipFile mappings = new ZipFile(intermediaryJar.toFile());
             Reader reader = new InputStreamReader(mappings.getInputStream(mappings.getEntry("mappings/mappings.tiny")), StandardCharsets.UTF_8)) {
            Tiny2FileReader.read(reader, tree);
        }
        try (Reader reader = Files.newBufferedReader(mojangMappings, StandardCharsets.UTF_8)) {
            ProGuardFileReader.read(reader, "named", "official", new MappingSourceNsSwitch(tree, "official"));
        }

        Files.createDirectories(out.getParent());
        Path work = Files.createTempDirectory(out.getParent(), "mojang-named-");
        List<Path> inputs = new ArrayList<>();
        inputs.add(input);
        try (ZipFile zip = new ZipFile(input.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (!entry.getName().startsWith("META-INF/jars/") || !entry.getName().endsWith(".jar")) continue;
                Path nested = work.resolve(entry.getName().substring("META-INF/jars/".length()));
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, nested, StandardCopyOption.REPLACE_EXISTING);
                }
                inputs.add(nested);
            }
        }

        Path tmp = out.resolveSibling("tmp-" + out.getFileName());
        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(TinyUtils.createMappingProvider(tree, "intermediary", "named"))
                .renameInvalidLocals(true)
                .build();
        try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(tmp).assumeArchive(true).build()) {
            remapper.readClassPath(minecraftRuntime);
            for (Path jar : inputs) remapper.readInputs(jar);
            remapper.apply(consumer);
        } finally {
            remapper.finish();
            try (var files = Files.walk(work)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Mojang-named jar: " + out);
    }
}
