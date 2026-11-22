/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.detector;

import com.google.gson.JsonElement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moandjiezana.toml.Toml;
import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.util.VyneLogger;

import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;

import java.io.IOException;
import java.util.Map;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.Collections;
import java.util.regex.Pattern;

public final class ModDetector {
    private final Path modsDir;

    public ModDetector(Path modsDir) {
        this.modsDir = modsDir;
    }

    public List<DetectedMod> scan() throws IOException {
        List<DetectedMod> results = new ArrayList<>();

        if (!Files.exists(modsDir)) {
            VyneLogger.warn("Mods directory not found: " + modsDir.toAbsolutePath() + " — creating it.");
            Files.createDirectories(modsDir);
            return results;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path jarPath : stream) {
                try {
                    DetectedMod mod = fingerprint(jarPath);
                    mod.setSide(scanForEnvironment(mod));
                    results.add(mod);
                    if (mod.getLoaderType() == DetectedMod.LoaderType.UNKNOWN) {
                        VyneLogger.warn("[Detector] Unrecognised mod format: " + jarPath.getFileName());
                    } else {
                        VyneLogger.detail("[Detector] Recognized " + mod.getLoaderType() + " mod: " + mod.getModId() + " v" + mod.getVersion() + " (" + mod.getSide() + ")");
                        VyneLogger.debug("  └─ Name: " + mod.getName() + " | JAR: " + jarPath.getFileName());
                    }
                } catch (Exception e) {
                    VyneLogger.error("[Detector] Failed to scan " + jarPath.getFileName() + ": " + e.getMessage());
                }
            }
        }

        results.addAll(expandJarInJar(results));
        return results;
    }

    private List<DetectedMod> expandJarInJar(List<DetectedMod> topLevel) {
        Path cacheRoot = modsDir.toAbsolutePath().normalize().getParent().resolve(".vyne").resolve("cache").resolve("jarjar");
        java.util.Set<String> present = new java.util.HashSet<>();
        for (DetectedMod mod : topLevel) present.add(nestedKey(mod));

        Map<String, DetectedMod> chosen = new java.util.LinkedHashMap<>();
        java.util.ArrayDeque<DetectedMod> hosts = new java.util.ArrayDeque<>(topLevel);
        int processed = 0;
        while (!hosts.isEmpty() && processed++ < 1000) {
            DetectedMod host = hosts.poll();
            if (host.getLoaderType() != DetectedMod.LoaderType.NEOFORGE && host.getLoaderType() != DetectedMod.LoaderType.FORGE) continue;
            for (Path nested : extractNestedJars(host.getJarPath(), cacheRoot)) {
                DetectedMod mod;
                try {
                    mod = fingerprint(nested);
                    mod.setSide(scanForEnvironment(mod));
                } catch (Exception e) {
                    VyneLogger.warn("[Detector] Failed to scan nested jar " + nested.getFileName() + ": " + e.getMessage());
                    continue;
                }
                String key = nestedKey(mod);
                if (present.contains(key)) continue;
                DetectedMod previous = chosen.get(key);
                if (previous != null && dev.stolym.vyne.util.VersionConstraintMatcher.compareVersions(previous.getVersion(), mod.getVersion()) >= 0) continue;
                chosen.put(key, mod);
                hosts.add(mod);
                VyneLogger.detail("[Detector] Found " + mod.getModId() + " " + mod.getVersion() + " bundled in " + host.getJarPath().getFileName());
            }
        }
        if (!chosen.isEmpty()) {
            VyneLogger.info("[Detector] Loaded " + chosen.size() + " bundled (jar-in-jar) dependencies: " + String.join(", ", chosen.keySet()));
        }
        return new ArrayList<>(chosen.values());
    }

    private static String nestedKey(DetectedMod mod) {
        if (mod.getLoaderType() != DetectedMod.LoaderType.UNKNOWN && mod.getModId() != null) return mod.getModId();
        return mod.getJarPath().getFileName().toString().replaceAll("[-_+]?\\d[\\w.+-]*\\.jar$", "");
    }

    private static List<java.util.zip.ZipEntry> nestedJarEntries(java.util.zip.ZipFile zip) throws IOException {
        java.util.LinkedHashMap<String, java.util.zip.ZipEntry> found = new java.util.LinkedHashMap<>();
        java.util.zip.ZipEntry metadata = zip.getEntry("META-INF/jarjar/metadata.json");
        if (metadata != null) {
            try (InputStream in = zip.getInputStream(metadata)) {
                JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                if (root.isJsonObject() && root.getAsJsonObject().get("jars") instanceof com.google.gson.JsonArray jars) {
                    for (JsonElement jar : jars) {
                        if (!jar.isJsonObject() || !jar.getAsJsonObject().has("path")) continue;
                        String path = jar.getAsJsonObject().get("path").getAsString();
                        java.util.zip.ZipEntry entry = zip.getEntry(path);
                        if (entry != null && !entry.isDirectory()) found.put(path, entry);
                    }
                }
            } catch (RuntimeException e) {
                VyneLogger.warn("[Detector] Unreadable jar-in-jar metadata in " + zip.getName() + ": " + e.getMessage());
            }
        }
        java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            java.util.zip.ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!entry.isDirectory() && name.startsWith("META-INF/jarjar/") && name.endsWith(".jar")) found.putIfAbsent(name, entry);
        }
        return new ArrayList<>(found.values());
    }

    private static List<Path> extractNestedJars(Path hostJar, Path cacheRoot) {
        List<Path> out = new ArrayList<>();
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(hostJar.toFile())) {
            Path dir = cacheRoot.resolve(hostJar.getFileName().toString().replace(".jar", ""));
            for (java.util.zip.ZipEntry entry : nestedJarEntries(zip)) {
                String name = entry.getName();
                Path target = dir.resolve(name.substring(name.lastIndexOf('/') + 1)).normalize();
                if (!target.startsWith(dir)) continue;
                if (!Files.exists(target) || Files.size(target) != entry.getSize()) {
                    Files.createDirectories(dir);
                    Path tmp = target.resolveSibling(target.getFileName() + ".part");
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                    }
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                out.add(target);
            }
        } catch (IOException e) {
            VyneLogger.warn("[Detector] Could not read bundled jars from " + hostJar.getFileName() + ": " + e.getMessage());
        }
        return out;
    }

        private DetectedMod fingerprint(Path jarPath) throws IOException, net.fabricmc.loader.impl.metadata.ParseMetadataException {
            try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry vyneEntry = jar.getJarEntry("vyne.mod.json");
            if (vyneEntry != null) return parseVyneJson(jarPath, jar, vyneEntry);

            JarEntry fabricEntry = jar.getJarEntry("fabric.mod.json");
            if (fabricEntry != null) return parseFabricJson(jarPath, jar, fabricEntry);

            JarEntry neoforgeEntry = jar.getJarEntry("META-INF/neoforge.mods.toml");
            if (neoforgeEntry != null) return parseNeoForgeToml(jarPath, jar, neoforgeEntry);

            JarEntry forgeEntry = jar.getJarEntry("META-INF/mods.toml");
            if (forgeEntry != null) return parseForgeToml(jarPath, jar, forgeEntry);

            java.util.jar.Manifest manifest = jar.getManifest();
            String modType = manifest != null ? manifest.getMainAttributes().getValue("FMLModType") : null;
            if ("GAMELIBRARY".equals(modType) || "LIBRARY".equals(modType)) {
                String id = jarPath.getFileName().toString().replaceAll("[-_+]?\\d[\\w.+-]*\\.jar$", "").replaceAll("\\.jar$", "").toLowerCase(java.util.Locale.ROOT);
                String version = manifest.getMainAttributes().getValue("Implementation-Version");
                DetectedMod library = new DetectedMod(jarPath, DetectedMod.LoaderType.NEOFORGE, id, jarPath.getFileName().toString(),
                        version != null ? version : "unknown", null, null, null);
                library.setLibrary(true);
                return library;
            }
        }

        return new DetectedMod(jarPath, DetectedMod.LoaderType.UNKNOWN, jarPath.getFileName().toString(), null, "unknown", null, null);
    }

    private DetectedMod parseVyneJson(Path jarPath, JarFile jar, JarEntry entry) throws IOException {
        JsonObject json = readJson(jar, entry);
        return new DetectedMod(
                jarPath,
                DetectedMod.LoaderType.VYNE,
                jsonString(json, "id", "unknown"),
                jsonString(json, "name", null),
                jsonString(json, "version", "1.0.0"),
                jsonString(json, "description", null),
                jsonList(json, "authors")
        );
    }

    private DetectedMod parseFabricJson(Path jarPath, JarFile jar, JarEntry entry) throws IOException, net.fabricmc.loader.impl.metadata.ParseMetadataException {
    try (InputStream is = jar.getInputStream(entry)) {
        LoaderModMetadata realMeta = net.fabricmc.loader.impl.metadata.ModMetadataParser
    .parseMetadata(is, jarPath.toString(), Collections.emptyList(),
            new net.fabricmc.loader.impl.metadata.VersionOverrides(),
            new net.fabricmc.loader.impl.metadata.DependencyOverrides(null),
        false);
        DetectedMod mod = new DetectedMod(
                jarPath,
                DetectedMod.LoaderType.FABRIC,
                realMeta.getId(),
                realMeta.getName(),
                realMeta.getVersion().getFriendlyString(),
                realMeta.getDescription(),
                null,
                realMeta
            );
        for (net.fabricmc.loader.api.metadata.ModDependency dep : realMeta.getDependencies()) {
            String depId = dep.getModId();
            if (depId.equals("fabricloader") || depId.equals("minecraft") || depId.equals("java")) {
                continue;
            }

            net.fabricmc.loader.api.metadata.ModDependency.Kind kind = dep.getKind();
            String constraint = dep.getVersionRequirements().toString();
            if (kind == net.fabricmc.loader.api.metadata.ModDependency.Kind.DEPENDS) {
                mod.addDependency(depId, constraint);
            } else if (kind == net.fabricmc.loader.api.metadata.ModDependency.Kind.RECOMMENDS
                    || kind == net.fabricmc.loader.api.metadata.ModDependency.Kind.SUGGESTS) {
                mod.addOptionalDependency(depId, constraint);
            }
        }
        return mod;
        }
    }

    private DetectedMod parseNeoForgeToml(Path jarPath, JarFile jar, JarEntry entry) throws IOException {
        Toml toml = readToml(jar, entry);
        List<Toml> mods = toml.getTables("mods");
        if (mods != null && !mods.isEmpty()) {
            Toml firstMod = mods.get(0);
            List<String> mixins = extractMixins(toml);
            String version = resolveVersion(firstMod.getString("version"), jar);
            DetectedMod mod = new DetectedMod(
                    jarPath,
                    DetectedMod.LoaderType.NEOFORGE,
                    firstMod.getString("modId"),
                    firstMod.getString("displayName"),
                    version,
                    firstMod.getString("description"),
                    mixins,
                    null
            );
            extractTomlDeps(toml, mod);
            return mod;
        }
        return new DetectedMod(jarPath, DetectedMod.LoaderType.NEOFORGE, "unknown", null, "unknown", null, null);
    }

    private String resolveVersion(String raw, JarFile jar) {
        if (raw == null || !raw.contains("${")) return raw;
        try {
            java.util.jar.Manifest manifest = jar.getManifest();
            if (manifest != null) {
                java.util.jar.Attributes attrs = manifest.getMainAttributes();
                String v = attrs.getValue("Implementation-Version");
                if (v == null) v = attrs.getValue("Specification-Version");
                if (v != null) return v;
            }
        } catch (Exception ignored) {}
        return raw;
    }

    private static final Pattern MODS_TOML_NEOFORGE_DEP = Pattern.compile(
        "(?is)modId\\s*=\\s*['\"]neoforge['\"]");

    private static final byte[] NEOFORGE_BYTECODE_SIG =
        "net/neoforged/neoforge".getBytes(StandardCharsets.US_ASCII);

    private static boolean modsTomlDeclaresNeoForgeDependency(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream is = jar.getInputStream(entry)) {
            String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return MODS_TOML_NEOFORGE_DEP.matcher(text).find();
        }
    }

    private static boolean jarBytecodeReferencesNeoForge(JarFile jar) {
        return jar.stream()
            .filter(e -> !e.isDirectory() && e.getName().endsWith(".class"))
            .filter(e -> !e.getName().startsWith("META-INF/"))
            .anyMatch(e -> classFileReferencesNeoForge(jar, e));
    }

    private static boolean classFileReferencesNeoForge(JarFile jar, JarEntry e) {
        long size = e.getSize();
        int cap = (size > 0 && size <= 512_000L) ? (int) size : 200_000;
        try (InputStream is = jar.getInputStream(e)) {
            byte[] buf = is.readNBytes(Math.min(cap, 400_000));
            return indexOfBytes(buf, NEOFORGE_BYTECODE_SIG) >= 0;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static int indexOfBytes(byte[] data, byte[] needle) {
        if (needle.length == 0 || data.length < needle.length) {
            return -1;
        }
        outer:
        for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private DetectedMod parseForgeToml(Path jarPath, JarFile jar, JarEntry entry) throws IOException {
        Toml toml = readToml(jar, entry);
        List<Toml> mods = toml.getTables("mods");
        if (mods != null && !mods.isEmpty()) {
            Toml firstMod = mods.get(0);
            List<String> mixins = extractMixins(toml);
            String version = resolveVersion(firstMod.getString("version"), jar);
            boolean neoDep = modsTomlDeclaresNeoForgeDependency(jar, entry);
            DetectedMod.LoaderType loader = neoDep
                ? DetectedMod.LoaderType.NEOFORGE
                : DetectedMod.LoaderType.FORGE;
            if (loader == DetectedMod.LoaderType.FORGE && jarBytecodeReferencesNeoForge(jar)) {
                loader = DetectedMod.LoaderType.NEOFORGE;
                VyneLogger.info("[Detector] JavaFML jar references NeoForge API in bytecode — treating as NEOFORGE: "
                    + firstMod.getString("modId"));
            } else if (loader == DetectedMod.LoaderType.NEOFORGE && neoDep) {
                VyneLogger.info("[Detector] mods.toml declares NeoForge dependency — treating as NEOFORGE: "
                    + firstMod.getString("modId"));
            }
            DetectedMod mod = new DetectedMod(
                    jarPath,
                    loader,
                    firstMod.getString("modId"),
                    firstMod.getString("displayName"),
                    version,
                    firstMod.getString("description"),
                    mixins,
                    null
            );
            extractTomlDeps(toml, mod);
            return mod;
        }
        return new DetectedMod(jarPath, DetectedMod.LoaderType.FORGE, "unknown", null, "unknown", null, null);
    }

    private void extractTomlDeps(Toml toml, DetectedMod mod) {
        try {
            List<Toml> deps = toml.getTables("dependencies." + mod.getModId());
            if (deps == null) deps = toml.getTables("dependencies");
            if (deps == null) return;
            for (Toml dep : deps) {
                String depId = dep.getString("modId");
                if (depId == null) continue;
                if (depId.equals("minecraft") || depId.equals("java")) {
                    continue;
                }
                String versionRange = dep.getString("versionRange", "*");
                if (isHardRequirement(dep)) {
                    mod.addDependency(depId, versionRange);
                } else if (isOptionalRequirement(dep)) {
                    mod.addOptionalDependency(depId, versionRange);
                }
            }
        } catch (Exception ignored) {}
    }

    private static boolean isHardRequirement(Toml dep) {
        String type = dep.getString("type");
        if (type != null) {
            return type.equalsIgnoreCase("required");
        }

        Boolean mandatory = dep.getBoolean("mandatory");
        return mandatory == null || mandatory;
    }

    private static boolean isOptionalRequirement(Toml dep) {
        String type = dep.getString("type");
        if (type != null) {
            return type.equalsIgnoreCase("optional");
        }
        Boolean mandatory = dep.getBoolean("mandatory");
        return mandatory != null && !mandatory;
    }

    private JsonObject readJson(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream is = jar.getInputStream(entry);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private Toml readToml(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream is = jar.getInputStream(entry)) {
            return new Toml().read(is);
        }
    }

    private List<String> extractMixins(Toml toml) {
        List<String> results = new ArrayList<>();
        List<Toml> mixins = toml.getTables("mixins");
        if (mixins != null) {
            for (Toml t : mixins) {
                String cfg = t.getString("config");
                if (cfg != null) results.add(cfg);
            }
        }
        return results;
    }

    private String jsonString(JsonObject obj, String key, String fallback) {
        return obj.has(key) ? obj.get(key).getAsString() : fallback;
    }

    private List<String> jsonList(JsonObject obj, String key) {
        List<String> list = new ArrayList<>();
        if (obj.has(key)) {
            JsonElement el = obj.get(key);
            if (el.isJsonArray()) {
                for (JsonElement e : el.getAsJsonArray()) {
                    list.add(e.getAsString());
                }
            } else {
                list.add(el.getAsString());
            }
        }
        return list;
    }

    private DetectedMod.Environment scanForEnvironment(DetectedMod mod) {
        if (mod.getLoaderType() == DetectedMod.LoaderType.FABRIC || mod.getLoaderType() == DetectedMod.LoaderType.UNKNOWN) {
            return mod.getSide();
        }

        try (JarFile jar = new JarFile(mod.getJarPath().toFile())) {
            if (mod.getLoaderType() == DetectedMod.LoaderType.NEOFORGE || mod.getLoaderType() == DetectedMod.LoaderType.FORGE) {
                String tomlPath = mod.getLoaderType() == DetectedMod.LoaderType.NEOFORGE ? "META-INF/neoforge.mods.toml" : "META-INF/mods.toml";
                JarEntry tomlEntry = jar.getJarEntry(tomlPath);
                if (tomlEntry != null) {
                    Toml toml = readToml(jar, tomlEntry);
                    List<Toml> mods = toml.getTables("mods");
                    if (mods != null && !mods.isEmpty()) {
                        String side = mods.get(0).getString("displayTest");
                        if ("IGNORE_ALL_VERSION".equals(side)) {  }
                    }
                }
            }

            for (String mixinCfg : mod.getMixins()) {
                JarEntry cfgEntry = jar.getJarEntry(mixinCfg);
                if (cfgEntry == null) continue;
                JsonObject json = readJson(jar, cfgEntry);
                if (json.has("package")) {
                    String pkg = json.get("package").getAsString().replace('.', '/') + "/";
                    if (json.has("mixins")) {
                        for (JsonElement el : json.getAsJsonArray("mixins")) {
                            JarEntry clsEntry = jar.getJarEntry(pkg + el.getAsString() + ".class");
                            if (clsEntry == null) continue;
                            try (InputStream is = jar.getInputStream(clsEntry)) {
                                org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(is);
                                org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
                                cr.accept(cn, org.objectweb.asm.ClassReader.SKIP_CODE);
                                if (cn.visibleAnnotations != null) {
                                    for (org.objectweb.asm.tree.AnnotationNode ann : cn.visibleAnnotations) {
                                        if (ann.desc.contains("Mixin")) {
                                            List<Object> values = ann.values;
                                            if (values != null) {
                                                for (int i = 0; i < values.size() - 1; i += 2) {
                                                    if (values.get(i).equals("value") || values.get(i).equals("targets")) {
                                                        String target = values.get(i+1).toString();
                                                        if (target.contains("class_310") || target.contains("Minecraft") || 
                                                            target.contains("class_437") || target.contains("Screen")) {
                                                            return DetectedMod.Environment.CLIENT;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return DetectedMod.Environment.UNIVERSAL;
    }

    public static LoaderModMetadata tryParseUniversal(Path path) {
    try {
        ModDetector detector = new ModDetector(path.getParent());
        DetectedMod mod = detector.fingerprint(path);

        if (mod.getLoaderType() == DetectedMod.LoaderType.FABRIC && mod.hasFabricMetadata()) {
            return new VyneVirtualMetadata(mod.getRealMetadata(), DetectedMod.LoaderType.FABRIC); 
        } else if (mod.getLoaderType() != DetectedMod.LoaderType.UNKNOWN) {
            return new VyneVirtualMetadata(mod.getLoaderType(), mod.getModId(), mod.getVersion(), mod.getName(), mod.getDescription(), mod.getMixins());
        }
    } catch (Exception e) {
        VyneLogger.error("[Detector] Universal parse failed for " + path.getFileName(), e);
    }
        return null;
    }
}
