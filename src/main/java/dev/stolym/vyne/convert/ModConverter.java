/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import dev.stolym.vyne.mappings.MappingComposer;
import dev.stolym.vyne.mappings.MappingRegistry;
import dev.stolym.vyne.util.VyneLogger;
import dev.stolym.vyne.vcl.RemapCache;
import dev.stolym.vyne.vcl.VyneTransformationGate;
import dev.stolym.vyne.vcl.metadata.MetadataClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class ModConverter {
    public static final int SCHEMA = 2;
    public static final String DEFERRED_ENTRY = "META-INF/vyne/deferred.list";

    private static final String INDEX_FILE = "index.properties";
    private static final Map<String, Path> RUNTIME_JARS = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> DEFERRED_BY_SOURCE = new ConcurrentHashMap<>();
    private static volatile Path cacheBase;
    private static volatile String progressLabel = "Remapping mods";

    public record Request(Path input, Path indexSource, String label) {
        public Request(Path input, String label) {
            this(input, input, label);
        }
    }

    public record Summary(int reused, int converted, int failed, int classes, int deferred, long millis) {}

    private record Outcome(Path jar, Set<String> deferred, int classes, boolean reused) {}

    private record Item(String name, byte[] bytes, boolean translate) {}

    private ModConverter() {}

    public static boolean isEnabled() {
        String v = System.getProperty("vyne.convert");
        return v == null || Boolean.parseBoolean(v);
    }

    public static void setCacheBase(Path base) {
        cacheBase = base;
    }

    public static void reset() {
        RUNTIME_JARS.clear();
        DEFERRED_BY_SOURCE.clear();
    }

    public static Path runtimeJar(Path original) {
        if (original == null) return null;
        Path converted = RUNTIME_JARS.get(key(original));
        return converted != null ? converted : original;
    }

    public static boolean isPreConverted(String sourceJar, String internalName) {
        if (sourceJar == null) return false;
        Set<String> deferred = DEFERRED_BY_SOURCE.get(sourceJar);
        return deferred != null && !deferred.contains(internalName);
    }

    public static Summary convertAll(List<Request> requests, Collection<Path> hierarchyJars, String environmentKey) {
        long start = System.nanoTime();
        if (requests == null || requests.isEmpty() || !isEnabled()) {
            return new Summary(0, 0, 0, 0, 0, 0);
        }
        if (!MappingRegistry.isReady() || MappingRegistry.get().classMap().isEmpty()) {
            VyneLogger.warn("[Converter] Mappings are not available; mods will be translated while the game runs.");
            return new Summary(0, 0, requests.size(), 0, 0, 0);
        }

        Path root;
        Properties index;
        try {
            root = prepareRoot(environmentKey);
            index = loadIndex(root);
        } catch (IOException e) {
            VyneLogger.warn("[Converter] Cache directory unavailable (" + e.getMessage()
                    + "); mods will be translated while the game runs.");
            return new Summary(0, 0, requests.size(), 0, 0, 0);
        }

        Map<Request, Path> targets = new LinkedHashMap<>();
        List<Request> pending = new ArrayList<>();
        Set<Path> live = new HashSet<>();
        int reused = 0;
        int failed = 0;
        int classes = 0;
        int deferred = 0;
        boolean indexChanged = false;

        for (Request request : requests) {
            if (request == null || request.input() == null || !Files.isRegularFile(request.input())) continue;
            if (RUNTIME_JARS.containsKey(key(request.input()))) {
                reused++;
                dev.stolym.vyne.ui.VyneLoadingScreen.modState(request.label(), dev.stolym.vyne.ui.VyneLoadingScreen.MOD_READY);
                continue;
            }
            try {
                String[] sha = new String[1];
                indexChanged |= resolveSha(index, request.input(), sha);
                Path target = root.resolve(sha[0] + ".jar");
                targets.put(request, target);
                live.add(target);
                if (Files.isRegularFile(target)) {
                    Set<String> names = readDeferred(target);
                    register(request, new Outcome(target, names, 0, true));
                    dev.stolym.vyne.ui.VyneLoadingScreen.modState(request.label(), dev.stolym.vyne.ui.VyneLoadingScreen.MOD_READY);
                    deferred += names.size();
                    reused++;
                } else {
                    pending.add(request);
                }
            } catch (IOException e) {
                failed++;
                VyneLogger.warn("[Converter] Could not prepare " + request.label() + ": " + e.getMessage());
            }
        }

        int converted = 0;
        if (!pending.isEmpty() && dev.stolym.vyne.core.VyneMode.patchedGame()) {
            VyneLogger.info("[Converter] Remapping " + pending.size() + " jar(s) exactly against NeoForge's patched game.");
            dev.stolym.vyne.ui.VyneLoadingScreen.update("Remapping mods (first launch only)", 54);
            try {
                List<Path> inputs = new ArrayList<>();
                List<Path> outputs = new ArrayList<>();
                for (Request request : pending) {
                    inputs.add(request.input());
                    outputs.add(targets.get(request));
                }
                String mc = dev.stolym.vyne.core.VyneLauncher.TARGET_MC;
                ExactRemap.remap(inputs, outputs, dev.stolym.vyne.core.VyneMode.remapClasspath(),
                        ExactRemap.mappings(dev.stolym.vyne.mappings.MojmapDownloader.getIntermediaryPath(mc),
                                dev.stolym.vyne.mappings.MojmapDownloader.getMojmapPath(mc)));
                for (Request request : pending) {
                    register(request, new Outcome(targets.get(request), ConcurrentHashMap.newKeySet(), 0, false));
                    dev.stolym.vyne.ui.VyneLoadingScreen.modState(request.label(), dev.stolym.vyne.ui.VyneLoadingScreen.MOD_READY);
                    dev.stolym.vyne.ui.VyneLoadingScreen.activity("Remapped " + request.label());
                    converted++;
                }
            } catch (Throwable t) {
                failed += pending.size();
                for (Request request : pending) live.remove(targets.get(request));
                VyneLogger.error("[Converter] Exact remap failed: " + t, t);
            }
            pending.clear();
        }

        if (!pending.isEmpty()) {
            VyneLogger.info("[Converter] Translating " + pending.size() + " new or changed jar(s); later boots reuse the result.");
            URLClassLoader probe = openProbe(requests, hierarchyJars);
            ExecutorService pool = newPool(probe);
            try {
                for (Request request : pending) recordHierarchy(request.input());
                int jarNumber = 0;
                for (Request request : pending) {
                    long jarStart = System.nanoTime();
                    jarNumber++;
                    progressLabel = request.label();
                    dev.stolym.vyne.ui.VyneLoadingScreen.modState(request.label(), dev.stolym.vyne.ui.VyneLoadingScreen.MOD_WORKING);
                    dev.stolym.vyne.ui.VyneLoadingScreen.update(
                            "Remapping mods (" + jarNumber + "/" + pending.size() + ", first launch only)", 54);
                    try {
                        Outcome outcome = convertJar(request.input(), targets.get(request), pool);
                        register(request, outcome);
                        converted++;
                        classes += outcome.classes();
                        deferred += outcome.deferred().size();
                        dev.stolym.vyne.ui.VyneLoadingScreen.modState(request.label(), dev.stolym.vyne.ui.VyneLoadingScreen.MOD_READY);
                        dev.stolym.vyne.ui.VyneLoadingScreen.activity("Remapped " + request.label() + " (" + outcome.classes() + " classes)");
                        VyneLogger.info("[Converter] " + request.label() + ": " + outcome.classes() + " classes translated, "
                                + outcome.deferred().size() + " left for the runtime gate ("
                                + (System.nanoTime() - jarStart) / 1_000_000 + " ms)");
                    } catch (Throwable t) {
                        failed++;
                        live.remove(targets.get(request));
                        VyneLogger.warn("[Converter] " + request.label() + " could not be converted ("
                                + t + "); it will be translated while the game runs.");
                    }
                }
            } finally {
                pool.shutdownNow();
                MetadataClassWriter.forgetMissing();
                MappingComposer.invalidateCache();
                if (probe != null) {
                    try {
                        probe.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        if (reused > 0) {
            dev.stolym.vyne.ui.VyneLoadingScreen.activity("Reused " + reused + " remapped jar(s) from cache");
        }
        if (indexChanged) storeIndex(root, index);
        prune(root, live);

        long millis = (System.nanoTime() - start) / 1_000_000;
        Summary summary = new Summary(reused, converted, failed, classes, deferred, millis);
        VyneLogger.info("[Converter] " + reused + " jar(s) reused, " + converted + " converted, "
                + failed + " not converted in " + millis + " ms");
        return summary;
    }

    public static void registerConverted(Path input, Path indexSource, Path converted) {
        register(new Request(input, indexSource, input.getFileName().toString()),
                new Outcome(converted, ConcurrentHashMap.newKeySet(), 0, true));
    }

    private static void register(Request request, Outcome outcome) {
        RUNTIME_JARS.put(key(request.input()), outcome.jar());
        Path source = request.indexSource() != null ? request.indexSource() : request.input();
        DEFERRED_BY_SOURCE.put(source.toAbsolutePath().toString(), outcome.deferred());
        DEFERRED_BY_SOURCE.put(key(source), outcome.deferred());
    }

    private static String key(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static Path prepareRoot(String environmentKey) throws IOException {
        Path base = cacheBase;
        if (base == null) {
            String gameDir = System.getProperty("vyne.gameDir");
            Path dir = (gameDir != null && !gameDir.isBlank())
                    ? Path.of(gameDir)
                    : Path.of(System.getProperty("user.dir", "."));
            base = dir.toAbsolutePath().normalize().resolve(".vyne").resolve("cache").resolve("converted");
        }
        String identity = SCHEMA + "|" + RemapCache.computeMappingsFingerprint(MappingRegistry.get())
                + "|" + (environmentKey != null ? environmentKey : "");
        Path root = base.resolve(SCHEMA + "-" + Integer.toHexString(identity.hashCode()));
        Files.createDirectories(root);
        try (DirectoryStream<Path> siblings = Files.newDirectoryStream(base)) {
            for (Path sibling : siblings) {
                if (!sibling.equals(root) && Files.isDirectory(sibling)) deleteTree(sibling);
            }
        }
        return root;
    }

    private static void deleteTree(Path dir) {
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static void prune(Path root, Set<Path> live) {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(root, "*.jar")) {
            for (Path file : files) {
                if (!live.contains(file)) Files.deleteIfExists(file);
            }
        } catch (IOException ignored) {
        }
    }

    private static Properties loadIndex(Path root) {
        Properties index = new Properties();
        Path file = root.resolve(INDEX_FILE);
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                index.load(reader);
            } catch (IOException | IllegalArgumentException e) {
                index.clear();
            }
        }
        return index;
    }

    private static void storeIndex(Path root, Properties index) {
        try (Writer writer = Files.newBufferedWriter(root.resolve(INDEX_FILE), StandardCharsets.UTF_8)) {
            index.store(writer, null);
        } catch (IOException e) {
            VyneLogger.debug("[Converter] Could not store the jar index: " + e.getMessage());
        }
    }

    private static boolean resolveSha(Properties index, Path jar, String[] out) throws IOException {
        String pathKey = key(jar);
        String stamp = Files.size(jar) + ":" + Files.getLastModifiedTime(jar).toMillis();
        String known = index.getProperty(pathKey);
        if (known != null) {
            int split = known.lastIndexOf(':');
            if (split > 0 && known.substring(0, split).equals(stamp)) {
                out[0] = known.substring(split + 1);
                return false;
            }
        }
        out[0] = sha256(jar);
        index.setProperty(pathKey, stamp + ":" + out[0]);
        return true;
    }

    private static String sha256(Path jar) throws IOException {
        try (InputStream in = Files.newInputStream(jar)) {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static Set<String> readDeferred(Path convertedJar) throws IOException {
        Set<String> names = ConcurrentHashMap.newKeySet();
        try (ZipFile zf = new ZipFile(convertedJar.toFile())) {
            ZipEntry entry = zf.getEntry(DEFERRED_ENTRY);
            if (entry == null) return names;
            try (InputStream in = zf.getInputStream(entry)) {
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                    if (!line.isBlank()) names.add(line.trim());
                }
            }
        }
        return names;
    }

    private static URLClassLoader openProbe(List<Request> requests, Collection<Path> hierarchyJars) {
        Set<Path> jars = new java.util.LinkedHashSet<>();
        if (hierarchyJars != null) jars.addAll(hierarchyJars);
        for (Request request : requests) {
            if (request != null && request.input() != null) jars.add(request.input());
        }
        List<URL> urls = new ArrayList<>();
        for (Path jar : jars) {
            if (jar == null || !Files.exists(jar)) continue;
            try {
                urls.add(jar.toUri().toURL());
            } catch (IOException ignored) {
            }
        }
        return new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getSystemClassLoader());
    }

    private static ExecutorService newPool(ClassLoader probe) {
        int threads = Integer.getInteger("vyne.convert.threads",
                Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1)));
        AtomicInteger counter = new AtomicInteger();
        return Executors.newFixedThreadPool(threads, task -> {
            Thread thread = new Thread(() -> {
                MappingComposer.pushClassByteProbeLoader(probe);
                task.run();
            }, "vyne-converter-" + counter.incrementAndGet());
            thread.setDaemon(true);
            thread.setContextClassLoader(probe);
            return thread;
        });
    }

    private static void recordHierarchy(Path jar) {
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".class") || name.startsWith("META-INF/")) continue;
                try (InputStream in = zf.getInputStream(entry)) {
                    new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(int version, int access, String className, String signature,
                                          String superName, String[] interfaces) {
                            MappingComposer.ComposedMappings.recordClassHierarchy(className, superName, interfaces);
                        }
                    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                } catch (RuntimeException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static Outcome convertJar(Path input, Path target, ExecutorService pool) throws Exception {
        List<Item> items = new ArrayList<>();
        Set<String> versioned = new HashSet<>();
        try (ZipFile zf = new ZipFile(input.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            Set<String> seen = new HashSet<>();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || isSignatureFile(name) || name.equals(DEFERRED_ENTRY) || !seen.add(name)) continue;
                byte[] bytes;
                try (InputStream in = zf.getInputStream(entry)) {
                    bytes = in.readAllBytes();
                }
                boolean isClass = name.endsWith(".class");
                if (isClass && name.startsWith("META-INF/versions/")) {
                    int cut = name.indexOf('/', "META-INF/versions/".length());
                    if (cut > 0) versioned.add(name.substring(cut + 1, name.length() - ".class".length()));
                }
                items.add(new Item(name, bytes, isClass && !name.startsWith("META-INF/")));
            }
        }

        Set<String> deferred = ConcurrentHashMap.newKeySet();
        deferred.addAll(versioned);
        List<Future<byte[]>> results = new ArrayList<>(items.size());
        int classes = 0;
        for (Item item : items) {
            String internal = item.translate() ? item.name().substring(0, item.name().length() - ".class".length()) : null;
            if (internal == null || versioned.contains(internal) || !VyneTransformationGate.isTranslatable(internal.replace('/', '.'))) {
                results.add(null);
                continue;
            }
            classes++;
            results.add(pool.submit(() -> translate(internal, item.bytes(), deferred)));
        }

        Path tmp = target.resolveSibling(target.getFileName() + "." + ProcessHandle.current().pid() + ".tmp");
        try {
            try (OutputStream os = Files.newOutputStream(tmp);
                 ZipOutputStream zos = new ZipOutputStream(os)) {
                zos.setLevel(Deflater.BEST_SPEED);
                int done = 0;
                for (int i = 0; i < items.size(); i++) {
                    Item item = items.get(i);
                    Future<byte[]> result = results.get(i);
                    byte[] bytes = result != null ? result.get() : item.bytes();
                    if (result != null && (++done % 25 == 0 || done == classes)) {
                        dev.stolym.vyne.ui.VyneLoadingScreen.task(progressLabel, done, classes);
                    }
                    zos.putNextEntry(new ZipEntry(item.name()));
                    zos.write(bytes);
                    zos.closeEntry();
                }
                zos.putNextEntry(new ZipEntry(DEFERRED_ENTRY));
                zos.write(String.join("\n", new TreeSet<>(deferred)).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
        return new Outcome(target, deferred, classes - deferred.size() + versioned.size(), false);
    }

    private static byte[] translate(String internal, byte[] original, Set<String> deferred) {
        MetadataClassWriter.beginMissingTrace();
        try {
            byte[] out = VyneTransformationGate.translate(internal.replace('/', '.'), original);
            if (!MetadataClassWriter.endMissingTrace().isEmpty()) {
                deferred.add(internal);
                return original;
            }
            return out;
        } catch (Throwable t) {
            MetadataClassWriter.endMissingTrace();
            deferred.add(internal);
            return original;
        }
    }

    private static boolean isSignatureFile(String name) {
        if (!name.startsWith("META-INF/") || name.indexOf('/', "META-INF/".length()) >= 0) return false;
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        return upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC");
    }
}
