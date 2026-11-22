/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import dev.stolym.vyne.util.VyneLogger;
import net.fabricmc.mappingio.tree.MappingTree;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class MixinTargetNames {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final byte[] MARKER = MIXIN.getBytes(StandardCharsets.UTF_8);

    private final MappingTree mappings;
    private final int named;
    private int fixed;

    private MixinTargetNames(MappingTree mappings) {
        this.mappings = mappings;
        this.named = mappings.getNamespaceId("named");
    }

    static List<Path> fix(List<Path> sources, List<Path> inputs, MappingTree mappings, Path workDir) throws IOException {
        MixinTargetNames names = new MixinTargetNames(mappings);
        List<Path> result = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            Path source = sources.get(i);
            Map<String, byte[]> entries = new LinkedHashMap<>();
            boolean changed = false;
            try (ZipFile zip = new ZipFile(source.toFile())) {
                for (ZipEntry entry : Collections.list(zip.entries())) {
                    if (entry.isDirectory()) continue;
                    byte[] bytes;
                    try (InputStream in = zip.getInputStream(entry)) {
                        bytes = in.readAllBytes();
                    }
                    if (entry.getName().endsWith(".class") && contains(bytes)) {
                        byte[] rewritten = names.rewrite(bytes);
                        changed |= rewritten != bytes;
                        bytes = rewritten;
                    }
                    entries.put(entry.getName(), bytes);
                }
            }
            if (!changed) {
                result.add(source);
                continue;
            }
            Path out = workDir.resolve("mixin-targets-" + i + "-" + ProcessHandle.current().pid() + ".jar");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(out))) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    zos.putNextEntry(new ZipEntry(entry.getKey()));
                    zos.write(entry.getValue());
                    zos.closeEntry();
                }
            }
            if (!inputs.contains(source)) Files.deleteIfExists(source);
            result.add(out);
        }
        if (names.fixed > 0) VyneLogger.info("[PatchedGame] Resolved " + names.fixed + " mixin target(s) that name an inner class with a dot");
        return result;
    }

    private byte[] rewrite(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean changed = false;
        for (List<AnnotationNode> annotations : java.util.Arrays.asList(node.invisibleAnnotations, node.visibleAnnotations)) {
            if (annotations == null) continue;
            for (AnnotationNode annotation : annotations) {
                if (!MIXIN.equals(annotation.desc) || annotation.values == null) continue;
                for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                    if (!"targets".equals(annotation.values.get(i)) || !(annotation.values.get(i + 1) instanceof List<?> targets)) continue;
                    List<Object> resolved = new ArrayList<>();
                    for (Object target : targets) {
                        String inner = target instanceof String name ? innerClass(name) : null;
                        if (inner != null) {
                            resolved.add(inner);
                            fixed++;
                            changed = true;
                        } else {
                            resolved.add(target);
                        }
                    }
                    annotation.values.set(i + 1, resolved);
                }
            }
        }
        if (!changed) return bytes;
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private String innerClass(String target) {
        String name = target.replace('.', '/');
        if (known(name)) return null;
        StringBuilder candidate = new StringBuilder(name);
        for (int slash = candidate.lastIndexOf("/"); slash > 0; slash = candidate.lastIndexOf("/", slash - 1)) {
            candidate.setCharAt(slash, '$');
            if (known(candidate.toString())) return candidate.toString().replace('/', '.');
        }
        return null;
    }

    private boolean known(String internalName) {
        return mappings.getClass(internalName, named) != null;
    }

    private static boolean contains(byte[] bytes) {
        outer:
        for (int i = 0; i <= bytes.length - MARKER.length; i++) {
            for (int j = 0; j < MARKER.length; j++) {
                if (bytes[i + j] != MARKER[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
