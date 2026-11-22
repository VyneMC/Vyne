/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.stolym.vyne.util.VyneLogger;
import net.fabricmc.tinyremapper.api.TrRemapper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class RefmapNames {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";

    private final ZipFile jar;
    private final TrRemapper remapper;
    private final Map<String, List<String>> targets = new HashMap<>();

    private RefmapNames(ZipFile jar, TrRemapper remapper) {
        this.jar = jar;
        this.remapper = remapper;
    }

    static Map<String, byte[]> remap(Path source, TrRemapper remapper) throws IOException {
        Map<String, byte[]> refmaps = new HashMap<>();
        try (ZipFile jar = new ZipFile(source.toFile())) {
            RefmapNames names = new RefmapNames(jar, remapper);
            for (ZipEntry entry : Collections.list(jar.entries())) {
                if (entry.isDirectory() || !entry.getName().endsWith("refmap.json")) continue;
                try (InputStream in = jar.getInputStream(entry)) {
                    JsonObject refmap = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                    if (refmap.get("mappings") instanceof JsonObject mappings) names.section(mappings);
                    if (refmap.get("data") instanceof JsonObject data) {
                        for (Map.Entry<String, JsonElement> environment : data.entrySet()) {
                            if (environment.getValue() instanceof JsonObject mappings) names.section(mappings);
                        }
                    }
                    refmaps.put(entry.getName(), refmap.toString().getBytes(StandardCharsets.UTF_8));
                } catch (JsonParseException | IllegalStateException e) {
                    VyneLogger.warn("[PatchedGame] Could not read " + entry.getName() + " in " + source.getFileName() + ": " + e);
                }
            }
        }
        return refmaps;
    }

    private void section(JsonObject mixins) throws IOException {
        for (Map.Entry<String, JsonElement> mixin : mixins.entrySet()) {
            if (!(mixin.getValue() instanceof JsonObject references)) continue;
            List<String> owners = targets(mixin.getKey());
            for (Map.Entry<String, JsonElement> reference : references.entrySet()) {
                if (reference.getValue().isJsonPrimitive()) {
                    reference.setValue(new JsonPrimitive(map(reference.getValue().getAsString(), owners)));
                }
            }
        }
    }

    private String map(String reference, List<String> mixinTargets) {
        if (reference.startsWith("(")) return remapper.mapMethodDesc(reference);

        String owner = null;
        String member = reference;
        int end = reference.indexOf(';');
        if (reference.startsWith("L") && end > 0 && !hasAny(reference.substring(0, end), "(:")) {
            owner = reference.substring(1, end);
            member = reference.substring(end + 1);
            if (member.isEmpty()) return "L" + remapper.map(owner) + ";";
        } else if (!hasAny(reference, "(:") && reference.indexOf('/') >= 0) {
            return remapper.map(reference);
        }

        String prefix = owner != null ? "L" + remapper.map(owner) + ";" : "";
        List<String> owners = owner != null ? List.of(owner) : mixinTargets;
        int open = member.indexOf('(');
        int colon = member.indexOf(':');
        if (open >= 0) {
            String name = member.substring(0, open);
            String desc = member.substring(open);
            String mapped = name;
            for (int i = 0; i < owners.size() && mapped.equals(name) && !name.startsWith("<"); i++) {
                mapped = remapper.mapMethodName(owners.get(i), name, desc);
            }
            return prefix + mapped + remapper.mapMethodDesc(desc);
        }
        if (colon >= 0) {
            String name = member.substring(0, colon);
            String desc = member.substring(colon + 1);
            String mapped = name;
            for (int i = 0; i < owners.size() && mapped.equals(name); i++) {
                mapped = remapper.mapFieldName(owners.get(i), name, desc);
            }
            return prefix + mapped + ":" + remapper.mapDesc(desc);
        }
        return prefix + member;
    }

    private List<String> targets(String mixinClass) throws IOException {
        List<String> known = targets.get(mixinClass);
        if (known != null) return known;
        List<String> found = new ArrayList<>();
        ZipEntry entry = jar.getEntry(mixinClass + ".class");
        if (entry != null) {
            ClassNode node = new ClassNode();
            try (InputStream in = jar.getInputStream(entry)) {
                new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_CODE);
            }
            if (node.invisibleAnnotations != null) {
                for (AnnotationNode annotation : node.invisibleAnnotations) {
                    if (!MIXIN.equals(annotation.desc) || annotation.values == null) continue;
                    for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                        if (!(annotation.values.get(i + 1) instanceof List<?> values)) continue;
                        for (Object value : values) {
                            if (value instanceof Type type) found.add(type.getInternalName());
                            else if (value instanceof String name) found.add(name.replace('.', '/'));
                        }
                    }
                }
            }
        }
        targets.put(mixinClass, found);
        return found;
    }

    private static boolean hasAny(String text, String characters) {
        for (int i = 0; i < characters.length(); i++) {
            if (text.indexOf(characters.charAt(i)) >= 0) return true;
        }
        return false;
    }
}
