/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.loaders;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.core.VyneLauncher;
import dev.stolym.vyne.mappings.MappingComposer;
import dev.stolym.vyne.mappings.MappingRegistry;
import dev.stolym.vyne.util.VyneLogger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class AccessTransformerBridge {
    public static final String DEFAULT_LOCATION = "META-INF/accesstransformer.cfg";
    private static final String HEADER = "accessWidener\tv2\tintermediary";

    private record Members(Map<String, String> fields, List<String[]> methods) {}

    public interface ClassSource {
        byte[] read(String internalName);
    }

    private AccessTransformerBridge() {}

    public static String buildForInstalledMods(Collection<Path> gameJars) {
        if (!MappingRegistry.isReady() || MappingRegistry.get().classMap().isEmpty()) return null;
        List<Path> sources = new ArrayList<>();
        Path neoApi = VyneLauncher.getNeoForgeApiJar();
        if (neoApi != null && RealNeoForgeApiMount.isEnabled()) sources.add(neoApi);
        List<DetectedMod> detected = VyneLauncher.getDetectedMods();
        if (detected != null) {
            for (DetectedMod mod : detected) {
                if (mod.getLoaderType() == DetectedMod.LoaderType.NEOFORGE
                        || mod.getLoaderType() == DetectedMod.LoaderType.FORGE) {
                    sources.add(mod.getJarPath());
                }
            }
        }
        List<String> lines = new ArrayList<>();
        for (Path jar : sources) {
            if (jar == null || !Files.isRegularFile(jar)) continue;
            try (ZipFile zf = new ZipFile(jar.toFile())) {
                ZipEntry entry = zf.getEntry(DEFAULT_LOCATION);
                if (entry == null) continue;
                try (InputStream in = zf.getInputStream(entry)) {
                    for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                        lines.add(line);
                    }
                }
            } catch (IOException e) {
                VyneLogger.warn("[AccessTransformer] Could not read " + jar.getFileName() + ": " + e.getMessage());
            }
        }
        if (lines.isEmpty()) return null;

        List<ZipFile> open = new ArrayList<>();
        try {
            if (gameJars != null) {
                for (Path jar : gameJars) {
                    if (jar == null || !Files.isRegularFile(jar)) continue;
                    try {
                        open.add(new ZipFile(jar.toFile()));
                    } catch (IOException ignored) {
                    }
                }
            }
            ClassSource source = internalName -> {
                for (ZipFile zf : open) {
                    ZipEntry entry = zf.getEntry(internalName + ".class");
                    if (entry == null) continue;
                    try (InputStream in = zf.getInputStream(entry)) {
                        return in.readAllBytes();
                    } catch (IOException ignored) {
                    }
                }
                return null;
            };
            int[] skipped = new int[1];
            String widener = translate(lines, MappingRegistry.get(), source, skipped);
            int entries = (int) widener.lines().count() - 1;
            VyneLogger.info("[AccessTransformer] Applying " + entries + " access rules from " + lines.size()
                    + " access transformer lines (" + skipped[0] + " not applicable on this side)");
            dev.stolym.vyne.ui.VyneLoadingScreen.activity("Applied " + entries + " access rules");
            return widener;
        } finally {
            for (ZipFile zf : open) {
                try {
                    zf.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static String translate(List<String> lines, MappingComposer.ComposedMappings mappings,
                                   ClassSource source, int[] skipped) {
        Set<String> out = new LinkedHashSet<>();
        Map<String, Members> cache = new HashMap<>();
        Remapper descriptors = new Remapper() {
            @Override
            public String map(String internalName) {
                return mappings.mapClass(internalName);
            }
        };
        for (String raw : lines) {
            int hash = raw.indexOf('#');
            String line = (hash >= 0 ? raw.substring(0, hash) : raw).trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+");
            if (parts.length < 2) continue;
            String modifier = parts[0];
            boolean removeFinal = modifier.endsWith("-f");
            String access = modifier.replace("-f", "").replace("+f", "");
            boolean widen = access.equals("public") || access.equals("protected") || access.equals("default");
            if (!widen && !removeFinal) continue;

            String mojOwner = parts[1].replace('.', '/');
            String owner = mappings.mapClass(mojOwner);
            Members members = cache.computeIfAbsent(owner, name -> readMembers(source.read(name)));
            if (members == null) {
                if (skipped != null) skipped[0]++;
                continue;
            }

            if (parts.length == 2) {
                if (widen) out.add("accessible\tclass\t" + owner);
                if (removeFinal) out.add("extendable\tclass\t" + owner);
                continue;
            }

            String member = parts[2];
            int paren = member.indexOf('(');
            if (member.equals("*")) {
                for (Map.Entry<String, String> field : members.fields().entrySet()) {
                    addField(out, owner, field.getKey(), field.getValue(), widen, removeFinal);
                }
            } else if (member.equals("*()")) {
                for (String[] method : members.methods()) {
                    addMethod(out, owner, method[0], method[1], widen, removeFinal);
                }
            } else if (paren < 0) {
                String name = mappings.mapField(mojOwner, member);
                String descriptor = members.fields().get(name);
                if (descriptor == null) {
                    if (skipped != null) skipped[0]++;
                    continue;
                }
                addField(out, owner, name, descriptor, widen, removeFinal);
            } else {
                String mojName = member.substring(0, paren);
                String mojDescriptor = member.substring(paren);
                String descriptor = descriptors.mapMethodDesc(mojDescriptor);
                String name = mojName.startsWith("<") ? mojName : mappings.mapMethod(mojOwner, mojName, mojDescriptor);
                boolean present = false;
                for (String[] method : members.methods()) {
                    if (method[0].equals(name) && method[1].equals(descriptor)) {
                        present = true;
                        break;
                    }
                }
                if (!present) {
                    if (skipped != null) skipped[0]++;
                    continue;
                }
                addMethod(out, owner, name, descriptor, widen, removeFinal);
            }
        }
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (String entry : out) sb.append(entry).append('\n');
        return sb.toString();
    }

    private static void addField(Set<String> out, String owner, String name, String descriptor,
                                 boolean widen, boolean removeFinal) {
        String tail = "\tfield\t" + owner + "\t" + name + "\t" + descriptor;
        if (widen) out.add("accessible" + tail);
        if (removeFinal) out.add("mutable" + tail);
    }

    private static void addMethod(Set<String> out, String owner, String name, String descriptor,
                                  boolean widen, boolean removeFinal) {
        if (name.equals("<clinit>")) return;
        String tail = "\tmethod\t" + owner + "\t" + name + "\t" + descriptor;
        if (widen) out.add("accessible" + tail);
        if (removeFinal && !name.equals("<init>")) out.add("extendable" + tail);
    }

    private static Members readMembers(byte[] bytes) {
        if (bytes == null) return null;
        Map<String, String> fields = new LinkedHashMap<>();
        List<String[]> methods = new ArrayList<>();
        try {
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    fields.put(name, descriptor);
                    return null;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    methods.add(new String[] {name, descriptor});
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (RuntimeException e) {
            return null;
        }
        return new Members(fields, methods);
    }
}
