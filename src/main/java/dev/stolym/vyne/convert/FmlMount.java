/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;


import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class FmlMount {
    private FmlMount() {}

    public static Path prepare(Path fmlJar, Path cacheDir) throws IOException {
        String key = Files.size(fmlJar) + ":" + Files.getLastModifiedTime(fmlJar).toMillis() + ":" + Routing.fingerprint();
        Path out = cacheDir.resolve("fml-" + Integer.toHexString(key.hashCode()) + ".jar");
        if (Files.isRegularFile(out)) return out;
        Files.createDirectories(cacheDir);
        Path tmp = out.resolveSibling(out.getFileName() + "." + ProcessHandle.current().pid() + ".tmp");
        try (ZipFile in = new ZipFile(fmlJar.toFile());
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmp))) {
            for (ZipEntry entry : Collections.list(in.entries())) {
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".class")) continue;
                if (Routing.isRouted(name.substring(0, name.length() - ".class".length()))) continue;
                zos.putNextEntry(new ZipEntry(name));
                try (InputStream data = in.getInputStream(entry)) {
                    zos.write(Routing.apply(data.readAllBytes()));
                }
                zos.closeEntry();
            }
        }
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        return out;
    }
}
