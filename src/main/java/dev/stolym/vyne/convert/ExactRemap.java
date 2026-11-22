/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.proguard.ProGuardFileReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class ExactRemap {
    private static volatile MemoryMappingTree tree;

    private ExactRemap() {}

    public static synchronized MemoryMappingTree mappings(Path intermediaryTiny, Path mojangMappings) throws IOException {
        if (tree == null) {
            MemoryMappingTree loaded = new MemoryMappingTree();
            MappingReader.read(intermediaryTiny, loaded);
            try (Reader reader = Files.newBufferedReader(mojangMappings, StandardCharsets.UTF_8)) {
                ProGuardFileReader.read(reader, "named", "official", new MappingSourceNsSwitch(loaded, "official"));
            }
            tree = loaded;
        }
        return tree;
    }

    public static void remap(List<Path> inputs, List<Path> outputs, Collection<Path> classpath,
                             MemoryMappingTree mappings) throws IOException {
        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(TinyUtils.createMappingProvider(mappings, "named", "intermediary"))
                .extension(new MixinExtension())
                .rebuildSourceFilenames(true)
                .fixPackageAccess(true)
                .build();
        List<Path> temporary = new ArrayList<>();
        List<Map<String, byte[]>> refmaps = new ArrayList<>();
        List<Path> sources = MixinTargetNames.fix(SharedMethodSplit.prepare(inputs, classpath, mappings, outputs.get(0).getParent()),
                inputs, mappings, outputs.get(0).getParent());
        try {
            for (Path entry : classpath) {
                if (entry != null && Files.exists(entry) && !inputs.contains(entry)) remapper.readClassPath(entry);
            }
            List<InputTag> tags = new ArrayList<>();
            for (Path input : sources) {
                InputTag tag = remapper.createInputTag();
                tags.add(tag);
                remapper.readInputs(tag, input);
            }
            for (int i = 0; i < inputs.size(); i++) {
                Path tmp = outputs.get(i).resolveSibling(outputs.get(i).getFileName() + "." + ProcessHandle.current().pid() + ".remap");
                temporary.add(tmp);
                try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(tmp).assumeArchive(true).build()) {
                    consumer.addNonClassFiles(sources.get(i), NonClassCopyMode.FIX_META_INF, remapper);
                    remapper.apply(consumer, tags.get(i));
                }
                refmaps.add(RefmapNames.remap(sources.get(i), remapper.getEnvironment().getRemapper()));
            }
        } finally {
            remapper.finish();
            for (Path source : sources) {
                if (!inputs.contains(source)) Files.deleteIfExists(source);
            }
        }
        for (int i = 0; i < inputs.size(); i++) {
            route(temporary.get(i), outputs.get(i), refmaps.get(i));
            Files.deleteIfExists(temporary.get(i));
        }
    }

    static void route(Path in, Path out, Map<String, byte[]> refmaps) throws IOException {
        Path routed = out.resolveSibling(out.getFileName() + "." + ProcessHandle.current().pid() + ".routing");
        try (ZipFile zip = new ZipFile(in.toFile());
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(routed))) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (entry.isDirectory()) continue;
                zos.putNextEntry(new ZipEntry(entry.getName()));
                try (InputStream data = zip.getInputStream(entry)) {
                    byte[] bytes = data.readAllBytes();
                    zos.write(entry.getName().endsWith(".class")
                            ? Routing.apply(SharedMethodSplit.restoreNames(bytes))
                            : refmaps.getOrDefault(entry.getName(), bytes));
                }
                zos.closeEntry();
            }
        }
        Files.move(routed, out, StandardCopyOption.REPLACE_EXISTING);
    }
}
