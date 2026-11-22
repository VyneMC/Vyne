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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class RealNeoForgeApiMount {
    public static final int CACHE_SCHEMA = 3;

    private RealNeoForgeApiMount() {}

    public static Path prepareFullApiJar(Path universalJar) throws IOException {
        String gameDir = System.getProperty("vyne.gameDir");
        Path base = (gameDir != null && !gameDir.isBlank()) ? Path.of(gameDir) : Path.of(System.getProperty("user.dir", "."));
        Path dir = base.toAbsolutePath().normalize().resolve(".vyne").resolve("cache").resolve("realneo").resolve(sha256(universalJar) + "-full");
        Path out = dir.resolve("api-net.jar");
        Path done = dir.resolve("done");
        if (Files.isRegularFile(done) && Files.isRegularFile(out) && ("schema=" + CACHE_SCHEMA).equals(Files.readString(done).trim())) {
            dev.stolym.vyne.convert.CacheCleanup.keepOnly(dir.getParent(), dir);
            return out;
        }
        Files.createDirectories(dir);
        try (ZipFile zf = new ZipFile(universalJar.toFile());
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(out))) {
            java.util.Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory()) continue;
                String name = e.getName();
                boolean signature = name.startsWith("META-INF/") && name.indexOf('/', "META-INF/".length()) < 0
                        && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"));
                if (signature) continue;
                zos.putNextEntry(new ZipEntry(name));
                try (InputStream is = zf.getInputStream(e)) {
                    is.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
        Files.writeString(done, "schema=" + CACHE_SCHEMA);
        dev.stolym.vyne.convert.CacheCleanup.keepOnly(dir.getParent(), dir);
        VyneLogger.info("[RealNeoAPI] Prepared the NeoForge API jar (" + dir.getFileName() + ") from " + universalJar.getFileName());
        return out;
    }

    private static String sha256(Path jar) throws IOException {
        try (InputStream in = Files.newInputStream(jar)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
