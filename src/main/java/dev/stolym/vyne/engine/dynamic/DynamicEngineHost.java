/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.util.VyneLogger;

import dev.stolym.vyne.engine.dynamic.cache.EngineCache;
import dev.stolym.vyne.engine.dynamic.synth.StubUnifier;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class DynamicEngineHost {
    public record PreparedClasspathSplit(List<URL> urls, List<Path> knotRewriteJarPaths) {}

    private static final ConcurrentHashMap<String, DynamicBridgeEngine.PreparedMod>
        PREPARED = new ConcurrentHashMap<>();

    private static final java.util.Set<Path> ACCUM_STUBS_SOURCES =
        java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());
    private static final java.util.Set<Path> ACCUM_SHIMS_SOURCES =
        java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());

    private static volatile DynamicBridgeEngine INSTANCE;
    private static volatile Path cacheRoot;

    private DynamicEngineHost() {}

    public static Path cacheRoot() {
        if (cacheRoot == null) resolveCacheRoot();
        return cacheRoot;
    }

    private static synchronized void resolveCacheRoot() {
        if (cacheRoot != null) return;
        String gameDirProp = System.getProperty("vyne.gameDir");
        Path base = gameDirProp != null
            ? Paths.get(gameDirProp)
            : Paths.get(".").toAbsolutePath().normalize();
        Path root = base.resolve(".vyne").resolve("cache").resolve("dynamic");
        try {
            Files.createDirectories(root);
        } catch (IOException ioe) {
            VyneLogger.warn("[DynamicEngine] Could not create cache dir "
                + root + ": " + ioe.getMessage());
        }
        cacheRoot = root;
    }

    public static synchronized DynamicBridgeEngine engine(ClassLoader probe) {
        if (INSTANCE != null) return INSTANCE;
        resolveCacheRoot();
        Path overrides = cacheRoot.getParent().getParent().resolve("bridges.json");
        INSTANCE = new DynamicBridgeEngine(cacheRoot, probe, overrides);

        try {
            java.net.URL selfUrl = DynamicEngineHost.class.getProtectionDomain()
                .getCodeSource().getLocation();
            if (selfUrl != null) {
                Path selfPath = Paths.get(selfUrl.toURI());
                if (Files.isRegularFile(selfPath)) {
                    INSTANCE.registerProvidedFromJars(java.util.List.of(selfPath));

                    int aliasCount = indexVyneForgeAliasesFromJar(selfPath, INSTANCE);
                    VyneLogger.info("[DynamicEngine] Indexed Vyne's own loader jar "
                        + "as provided classpath (" + selfPath.getFileName()
                        + "; +" + aliasCount + " forge→neoforge alias(es)).");
                }
            }
        } catch (Throwable t) {
            VyneLogger.warn("[DynamicEngine] Could not self-index Vyne loader jar: "
                + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        return INSTANCE;
    }

    private static int indexVyneForgeAliasesFromJar(Path selfPath, DynamicBridgeEngine engine) {
        java.util.Set<String> aliases = new java.util.LinkedHashSet<>();
        try (ZipFile zf = new ZipFile(selfPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                if (!name.endsWith(".class")) continue;
                if (!name.startsWith("net/neoforged/neoforge/")) continue;
                String stripped = name.substring(0, name.length() - ".class".length());

                String forgeTail = stripped.substring("net/neoforged/neoforge/".length());
                String forgeAlias = "net/minecraftforge/" + forgeTail;
                aliases.add(forgeAlias);
                if (forgeTail.contains("ModConfigSpec")) {
                    aliases.add("net/minecraftforge/"
                        + forgeTail.replace("ModConfigSpec", "ForgeConfigSpec"));
                }
            }
        } catch (IOException ioe) {
            VyneLogger.warn("[DynamicEngine] Failed to enumerate Vyne jar for "
                + "forge-alias indexing: " + ioe.getMessage());
            return 0;
        }
        if (aliases.isEmpty()) return 0;
        engine.existence().markProvided(aliases);
        return aliases.size();
    }

    public static DynamicBridgeEngine.PreparedMod prepare(DetectedMod mod, ClassLoader probe) {
        if (mod == null || mod.getJarPath() == null) return null;
        return PREPARED.computeIfAbsent(mod.getModId(), id -> {
            try {
                return engine(probe).prepare(mod.getJarPath());
            } catch (Throwable t) {
                VyneLogger.warn("[DynamicEngine] Prepare failed for " + id + ": "
                    + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + " — falling back to original jar.");
                return null;
            }
        });
    }

    public static List<URL> preparedClasspath(Iterable<DetectedMod> mods, ClassLoader probe) {
        return preparedClasspathSplit(mods, probe).urls();
    }

    public static PreparedClasspathSplit preparedClasspathSplit(
            Iterable<DetectedMod> mods, ClassLoader probe) {
        DynamicBridgeEngine eng = engine(probe);
        List<Path> jars = new ArrayList<>();
        for (DetectedMod m : mods) {
            if (m != null && m.getJarPath() != null) jars.add(m.getJarPath());
        }

        List<Path> extractedNested = new ArrayList<>();
        for (DetectedMod m : mods) {
            if (m == null || m.getJarPath() == null) continue;
            extractedNested.addAll(extractNestedJars(m.getJarPath()));
        }
        jars.addAll(extractedNested);
        eng.registerProvidedFromJars(jars);

        List<URL> rewritten = new ArrayList<>();
        LinkedHashSet<Path> knotRewriteJars = new LinkedHashSet<>();
        List<Path> stubsJars = new ArrayList<>();
        List<Path> shimsJars = new ArrayList<>();
        for (DetectedMod mod : mods) {
            DynamicBridgeEngine.PreparedMod p = prepare(mod, probe);
            if (p == null) {
                if (mod != null && mod.getJarPath() != null) {
                    safeAdd(rewritten, mod.getJarPath());
                    knotRewriteJars.add(mod.getJarPath().toAbsolutePath().normalize());
                }
                continue;
            }
            safeAdd(rewritten, p.rewrittenJar);
            knotRewriteJars.add(p.rewrittenJar.toAbsolutePath().normalize());
            if (Files.exists(p.stubsJar)) {
                stubsJars.add(p.stubsJar);
                registerSynthesizedFromJar(p.stubsJar);
            }
            if (Files.exists(p.shimsJar)) {
                shimsJars.add(p.shimsJar);
                registerSynthesizedFromJar(p.shimsJar);
            }
        }

        for (Path nested : extractedNested) {
            DynamicBridgeEngine.PreparedMod np = prepareNested(nested, eng);
            if (np == null) {
                safeAdd(rewritten, nested);
                knotRewriteJars.add(nested.toAbsolutePath().normalize());
                continue;
            }
            safeAdd(rewritten, np.rewrittenJar);
            knotRewriteJars.add(np.rewrittenJar.toAbsolutePath().normalize());
            if (Files.exists(np.stubsJar)) {
                stubsJars.add(np.stubsJar);
                registerSynthesizedFromJar(np.stubsJar);
            }
            if (Files.exists(np.shimsJar)) {
                shimsJars.add(np.shimsJar);
                registerSynthesizedFromJar(np.shimsJar);
            }
        }

        ACCUM_STUBS_SOURCES.addAll(stubsJars);
        ACCUM_SHIMS_SOURCES.addAll(shimsJars);
        Path unifiedStubs = writeUnifiedJar(
            new ArrayList<>(ACCUM_STUBS_SOURCES), "vyne-global-stubs.jar");
        Path unifiedShims = writeUnifiedJar(
            new ArrayList<>(ACCUM_SHIMS_SOURCES), "vyne-global-shims.jar");

        List<URL> out = new ArrayList<>(rewritten.size() + 2);
        if (unifiedStubs != null) {
            safeAdd(out, unifiedStubs);
            knotRewriteJars.add(unifiedStubs.toAbsolutePath().normalize());
        }
        if (unifiedShims != null) {
            safeAdd(out, unifiedShims);
            knotRewriteJars.add(unifiedShims.toAbsolutePath().normalize());
        }
        out.addAll(rewritten);
        return new PreparedClasspathSplit(out, new ArrayList<>(knotRewriteJars));
    }

    public static List<URL> remappingClasspathUrls(PreparedClasspathSplit split) {
        List<URL> all = split.urls();
        List<Path> knotPaths = split.knotRewriteJarPaths();
        if (knotPaths == null || knotPaths.isEmpty()) {
            return new ArrayList<>(all);
        }
        java.util.Set<Path> knot = new java.util.HashSet<>();
        for (Path p : knotPaths) {
            if (p != null) knot.add(p.toAbsolutePath().normalize());
        }
        List<URL> out = new ArrayList<>(all.size());
        for (URL u : all) {
            Path up = urlToNormalizedFilePath(u);
            if (up != null && knot.contains(up)) {
                continue;
            }
            out.add(u);
        }
        return out;
    }

    private static Path urlToNormalizedFilePath(URL u) {
        if (u == null || !"file".equalsIgnoreCase(u.getProtocol())) {
            return null;
        }
        try {
            return Paths.get(u.toURI()).toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Path writeUnifiedJar(List<Path> sources, String fileName) {
        if (sources == null || sources.isEmpty()) return null;
        Map<String, List<byte[]>> perName = new LinkedHashMap<>();
        int totalEntries = 0;
        for (Path jar : sources) {
            try (ZipFile zf = new ZipFile(jar.toFile())) {
                Enumeration<? extends ZipEntry> e = zf.entries();
                while (e.hasMoreElements()) {
                    ZipEntry entry = e.nextElement();
                    String name = entry.getName();
                    if (!name.endsWith(".class")) continue;
                    String internal = name.substring(0, name.length() - ".class".length());
                    try (InputStream is = zf.getInputStream(entry)) {
                        byte[] bytes = is.readAllBytes();
                        perName.computeIfAbsent(internal, k -> new ArrayList<>()).add(bytes);
                        totalEntries++;
                    }
                }
            } catch (IOException ioe) {
                VyneLogger.warn("[DynamicEngine] Failed to read "
                    + jar.getFileName() + " for global unification: " + ioe.getMessage());
            }
        }
        if (perName.isEmpty()) return null;

        Map<String, byte[]> merged = StubUnifier.unify(perName);
        int collisions = totalEntries - merged.size();
        if (collisions > 0) {
            VyneLogger.info("[DynamicEngine] Unified " + fileName + ": "
                + merged.size() + " class(es), merged " + collisions
                + " cross-mod collision(s) across " + sources.size() + " source jar(s).");
        }

        Path out = cacheRoot().resolve(fileName);
        try {
            EngineCache.writeClassJar(out, merged);
            return out;
        } catch (IOException ioe) {
            VyneLogger.warn("[DynamicEngine] Failed to write " + out + ": " + ioe.getMessage());
            return null;
        }
    }

    private static final java.util.Set<String> NESTED_CACHE_KEYS =
        java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());
    private static final ConcurrentHashMap<String, DynamicBridgeEngine.PreparedMod>
        PREPARED_NESTED = new ConcurrentHashMap<>();

    private static List<Path> extractNestedJars(Path hostJar) {
        List<Path> out = new ArrayList<>();
        if (hostJar == null || !Files.isRegularFile(hostJar)) return out;
        Path nestedRoot = cacheRoot().resolve("nested");
        try {
            Files.createDirectories(nestedRoot);
        } catch (IOException ioe) {
            VyneLogger.warn("[DynamicEngine] Could not create nested-jar cache dir "
                + nestedRoot + ": " + ioe.getMessage());
            return out;
        }
        try (ZipFile zf = new ZipFile(hostJar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory()) continue;
                String name = e.getName();
                boolean isJij = (name.startsWith("META-INF/jarjar/")
                        || name.startsWith("META-INF/jars/"))
                    && name.endsWith(".jar");
                if (!isJij) continue;
                String flatName = name.replace('/', '_');

                String key = hostJar.getFileName().toString() + "!" + flatName
                    + "@" + e.getSize() + "/" + e.getCrc();
                Path dest = nestedRoot.resolve(flatName);
                try {
                    if (!NESTED_CACHE_KEYS.add(key) && Files.exists(dest)
                            && Files.size(dest) == e.getSize()) {
                        out.add(dest);
                        continue;
                    }
                    try (InputStream is = zf.getInputStream(e)) {
                        Files.copy(is, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    out.add(dest);
                    VyneLogger.info("[DynamicEngine] Extracted nested jar "
                        + hostJar.getFileName() + " → " + flatName);
                } catch (IOException ioe) {
                    VyneLogger.warn("[DynamicEngine] Failed to extract nested jar "
                        + name + " from " + hostJar.getFileName() + ": " + ioe.getMessage());
                }
            }
        } catch (IOException ioe) {
            VyneLogger.warn("[DynamicEngine] Failed to enumerate "
                + hostJar.getFileName() + " for nested jars: " + ioe.getMessage());
        }
        return out;
    }

    private static DynamicBridgeEngine.PreparedMod prepareNested(
            Path nestedJar, DynamicBridgeEngine eng) {
        if (nestedJar == null || eng == null) return null;
        String key = nestedJar.toAbsolutePath().toString();
        return PREPARED_NESTED.computeIfAbsent(key, k -> {
            try {
                return eng.prepare(nestedJar);
            } catch (Throwable t) {
                VyneLogger.warn("[DynamicEngine] Prepare failed for nested "
                    + nestedJar.getFileName() + ": "
                    + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + " — falling back to original nested jar.");
                return null;
            }
        });
    }

    private static void registerSynthesizedFromJar(Path jar) {
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> e = zf.entries();
            while (e.hasMoreElements()) {
                String name = e.nextElement().getName();
                if (!name.endsWith(".class")) continue;
                String internal = name.substring(0, name.length() - ".class".length());
                dev.stolym.vyne.vcl.RemappingClassLoader.registerSynthesizedClass(
                    internal.replace('/', '.'));
            }
        } catch (IOException ioe) {
            VyneLogger.warn("[DynamicEngine] Failed to register synthesized names from "
                + jar + ": " + ioe.getMessage());
        }
    }

    private static void safeAdd(List<URL> list, Path p) {
        try {
            list.add(p.toUri().toURL());
        } catch (java.net.MalformedURLException mue) {
            VyneLogger.warn("[DynamicEngine] Skipping unrouteable path " + p
                + ": " + mue.getMessage());
        }
    }

    public static DynamicBridgeEngine.PreparedMod getPrepared(String modId) {
        return PREPARED.get(modId);
    }
}
