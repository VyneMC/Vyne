/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import dev.stolym.vyne.core.LoaderSides;
import dev.stolym.vyne.core.VyneLauncher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class VyneGameJar {
    private static volatile Path prepared;

    private VyneGameJar() {}

    public static Path vyneJar() {
        try {
            return Path.of(VyneLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    public static synchronized Path prepare() throws IOException {
        if (prepared != null) return prepared;
        Path self = vyneJar();
        if (!Files.isRegularFile(self)) {
            prepared = self;
            return self;
        }
        String key = Files.size(self) + ":" + Files.getLastModifiedTime(self).toMillis();
        Path dir = Path.of(System.getProperty("vyne.gameDir", ".")).toAbsolutePath().normalize()
                .resolve(".vyne").resolve("cache").resolve("vyne-game");
        Path out = dir.resolve(Integer.toHexString(key.hashCode()) + ".jar");
        if (!Files.isRegularFile(out)) {
            Files.createDirectories(dir);
            Path tmp = out.resolveSibling(out.getFileName() + "." + ProcessHandle.current().pid() + ".tmp");
            try (ZipFile in = new ZipFile(self.toFile());
                 ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmp))) {
                for (ZipEntry entry : Collections.list(in.entries())) {
                    if (entry.isDirectory() || !isGameSide(entry.getName())) continue;
                    zos.putNextEntry(new ZipEntry(entry.getName()));
                    try (InputStream data = in.getInputStream(entry)) {
                        data.transferTo(zos);
                    }
                    zos.closeEntry();
                }
            }
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
            try (var old = Files.newDirectoryStream(dir, "*.jar")) {
                for (Path stale : old) {
                    if (!stale.equals(out)) Files.deleteIfExists(stale);
                }
            }
        }
        prepared = out;
        return out;
    }

    static boolean isGameSide(String entryName) {
        if (!entryName.endsWith(".class")) return false;
        if (entryName.startsWith("net/neoforged/") || entryName.startsWith("net/minecraftforge/")
                || entryName.startsWith("cpw/mods/")) {
            return true;
        }
        if (!entryName.startsWith("dev/stolym/vyne/")) return false;
        String name = entryName.substring(0, entryName.length() - ".class".length());
        return !LoaderSides.isLauncherSide(name);
    }
}
