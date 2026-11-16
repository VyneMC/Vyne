/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.loaders;

import dev.stolym.vyne.util.VyneLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class RealNeoForgeApiMount {
    public static final int CACHE_SCHEMA = 2;
    private static final boolean ENABLED = flagValue();

    private RealNeoForgeApiMount() {}

    private static boolean flagValue() {
        String v = System.getProperty("vyne.realNeoforgeApi");
        if (v == null) v = System.getProperty("vyne.experimental.realNeoforgeApi");
        return v == null || Boolean.parseBoolean(v);
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static void mount(Path universalJar) {
        if (!ENABLED || universalJar == null) return;
        if (!Files.isRegularFile(universalJar)) {
            VyneLogger.warn("[RealNeoAPI] Flag set but no NeoForge universal jar available — skipping.");
            return;
        }
        try {
            Path apiJar = dev.stolym.vyne.convert.ModConverter.runtimeJar(prepareStrippedApiJar(universalJar));
            KnotModClasspathBridge.addRewriteJars(java.util.List.of(apiJar));
            VyneLogger.info("[RealNeoAPI] Real NeoForge API mounted on Knot: " + apiJar.getFileName());
        } catch (Throwable t) {
            VyneLogger.warn("[RealNeoAPI] Mount failed, shims remain active: " + t);
        }
    }

    public static Path prepareFullApiJar(Path universalJar) throws IOException {
        return prepareApiJar(universalJar, java.util.Set.of(), true);
    }

    public static Path prepareStrippedApiJar(Path universalJar) throws IOException {
        return prepareStrippedApiJar(universalJar, vyneProvidedClasses());
    }

    static java.util.Set<String> vyneProvidedClasses() {
        java.util.Map<String, byte[]> classes = new java.util.HashMap<>();
        try {
            Path self = Path.of(RealNeoForgeApiMount.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(self)) {
                try (ZipFile zf = new ZipFile(self.toFile())) {
                    for (ZipEntry e : java.util.Collections.list(zf.entries())) {
                        if (isRelevant(e.getName())) {
                            try (InputStream in = zf.getInputStream(e)) {
                                classes.put(e.getName(), in.readAllBytes());
                            }
                        }
                    }
                }
            } else if (Files.isDirectory(self)) {
                try (java.util.stream.Stream<Path> walk = Files.walk(self)) {
                    for (Path f : (Iterable<Path>) walk::iterator) {
                        String name = self.relativize(f).toString().replace('\\', '/');
                        if (isRelevant(name)) classes.put(name, Files.readAllBytes(f));
                    }
                }
            }
        } catch (Exception e) {
            VyneLogger.warn("[RealNeoAPI] Could not analyse Vyne's own NeoForge classes: " + e.getMessage());
        }
        return integrationClasses(classes);
    }

    private static boolean isRelevant(String name) {
        return name.endsWith(".class") && (name.startsWith("dev/stolym/vyne/")
                || name.startsWith("net/neoforged/") || name.startsWith("net/minecraftforge/"));
    }

    static java.util.Set<String> integrationClasses(java.util.Map<String, byte[]> classes) {
        java.util.Set<String> reached = new java.util.TreeSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        for (String name : classes.keySet()) if (name.startsWith("dev/stolym/vyne/")) queue.add(name);
        java.util.Set<String> visited = new java.util.HashSet<>();
        while (!queue.isEmpty()) {
            String name = queue.poll();
            byte[] bytes = classes.get(name);
            if (bytes == null || !visited.add(name)) continue;
            if (!name.startsWith("dev/stolym/vyne/")) reached.add(name);
            for (String ref : referencedClasses(bytes)) {
                String file = ref + ".class";
                if (classes.containsKey(file) && !visited.contains(file)) queue.add(file);
                int dollar = ref.indexOf('$');
                if (dollar > 0) queue.add(ref.substring(0, dollar) + ".class");
            }
            String outer = name.substring(0, name.length() - ".class".length());
            for (String other : classes.keySet()) {
                if (other.startsWith(outer + "$") && !visited.contains(other)) queue.add(other);
            }
        }
        return reached;
    }

    private static java.util.Set<String> referencedClasses(byte[] bytes) {
        java.util.Set<String> out = new java.util.HashSet<>();
        org.objectweb.asm.commons.Remapper recorder = new org.objectweb.asm.commons.Remapper() {
            @Override
            public String map(String internalName) {
                out.add(internalName);
                return internalName;
            }
        };
        try {
            new org.objectweb.asm.ClassReader(bytes).accept(new org.objectweb.asm.commons.ClassRemapper(
                    new org.objectweb.asm.tree.ClassNode(), recorder), 0);
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    static Path prepareStrippedApiJar(Path universalJar, java.util.Set<String> vyneProvided) throws IOException {
        return prepareApiJar(universalJar, vyneProvided, false);
    }

    private static Path prepareApiJar(Path universalJar, java.util.Set<String> vyneProvided, boolean keepResources) throws IOException {
        String sha = dev.stolym.vyne.vcl.RemapCache.jarSha256(universalJar);
        String providedHash = Integer.toHexString(vyneProvided.hashCode());
        String gameDir = System.getProperty("vyne.gameDir");
        Path base = (gameDir != null && !gameDir.isBlank())
                ? Path.of(gameDir)
                : Path.of(System.getProperty("user.dir", "."));
        Path dir = base.toAbsolutePath().normalize()
                .resolve(".vyne").resolve("cache").resolve("realneo")
                .resolve((sha != null ? sha : "unknown") + "-" + providedHash + (keepResources ? "-full" : ""));
        Path out = dir.resolve("api-net.jar");
        Path done = dir.resolve("done");
        if (Files.isRegularFile(done) && Files.isRegularFile(out)
                && ("schema=" + CACHE_SCHEMA).equals(Files.readString(done).trim())) {
            return out;
        }
        Files.createDirectories(dir);
        try (ZipFile zf = new ZipFile(universalJar.toFile());
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(out))) {
            java.util.Enumeration<? extends ZipEntry> entries = zf.entries();
            byte[] buf = new byte[8192];
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory()) continue;
                String name = e.getName();
                boolean signature = name.startsWith("META-INF/") && name.indexOf('/', "META-INF/".length()) < 0
                        && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"));
                boolean keep = keepResources ? !signature : name.startsWith("net/") || name.startsWith("META-INF/services/");
                if (!keep || vyneProvided.contains(name)) continue;
                zos.putNextEntry(new ZipEntry(name));
                try (InputStream is = zf.getInputStream(e)) {
                    int n;
                    while ((n = is.read(buf)) > 0) zos.write(buf, 0, n);
                }
                zos.closeEntry();
            }
        }
        Files.writeString(done, "schema=" + CACHE_SCHEMA);
        VyneLogger.info("[RealNeoAPI] Prepared real API jar (" + dir.getFileName() + ") from "
                + universalJar.getFileName() + ", leaving out " + vyneProvided.size() + " classes Vyne provides itself");
        return out;
    }
}
