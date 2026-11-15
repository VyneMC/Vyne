/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic.cache;

import dev.stolym.vyne.util.VyneLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class EngineCache {
    public static final int CACHE_SCHEMA = 90;

    private final Path root;

    public EngineCache(Path root) { this.root = root; }

    public Path dirFor(String sha256) {
        return root.resolve(sha256);
    }

    public boolean isComplete(String sha256) {
        Path done = dirFor(sha256).resolve("done");
        if (!Files.exists(done)) return false;
        try {
            String content = Files.readString(done).trim();

            if (!content.startsWith("schema=")) return false;
            int eq = content.indexOf('=');
            int schema = Integer.parseInt(content.substring(eq + 1).trim());
            return schema == CACHE_SCHEMA;
        } catch (Exception e) {
            return false;
        }
    }

    public Path rewrittenJar(String sha256) { return dirFor(sha256).resolve("mod-rewritten.jar"); }
    public Path stubsJar(String sha256)      { return dirFor(sha256).resolve("stubs.jar"); }
    public Path shimsJar(String sha256)      { return dirFor(sha256).resolve("shims.jar"); }
    public Path manifest(String sha256)      { return dirFor(sha256).resolve("manifest.txt"); }

    public Path nestedJarCacheDir() {
        return root.resolve("nested");
    }

    public void ensureDirForSha(String sha256) throws IOException {
        Files.createDirectories(dirFor(sha256));
    }

    public void markComplete(String sha256) throws IOException {
        Files.writeString(dirFor(sha256).resolve("done"),
            "schema=" + CACHE_SCHEMA + "\n");
    }

    public static void writeClassJar(Path jarPath, Map<String, byte[]> classes)
            throws IOException {
        Files.createDirectories(jarPath.getParent());
        try (OutputStream os = Files.newOutputStream(jarPath);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            for (Map.Entry<String, byte[]> e : classes.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey() + ".class");
                zos.putNextEntry(entry);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
    }

    public static void rewriteJar(Path sourceJar, Path targetPath,
                                  Map<String, byte[]> replacements) throws IOException {
        Files.createDirectories(targetPath.getParent());
        byte[] buf = new byte[8192];
        try (InputStream is = Files.newInputStream(sourceJar);
             ZipInputStream zis = new ZipInputStream(is);
             OutputStream os = Files.newOutputStream(targetPath);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                ZipEntry out = new ZipEntry(name);
                zos.putNextEntry(out);

                if (name.endsWith(".class")) {
                    String internal = name.substring(0, name.length() - ".class".length());
                    byte[] rewritten = replacements.get(internal);
                    if (rewritten != null) {
                        zos.write(rewritten);
                        zis.closeEntry();
                        zos.closeEntry();
                        continue;
                    }
                }
                int n;
                while ((n = zis.read(buf)) > 0) {
                    zos.write(buf, 0, n);
                }
                zis.closeEntry();
                zos.closeEntry();
            }
        }
    }

    public void writeManifest(String sha256, String text) {
        try {
            Files.writeString(manifest(sha256), text);
        } catch (IOException e) {
            VyneLogger.warn("[DynamicEngine] Could not write cache manifest: " + e.getMessage());
        }
    }
}
