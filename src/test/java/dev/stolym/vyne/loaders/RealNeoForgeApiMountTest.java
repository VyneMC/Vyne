/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.loaders;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class RealNeoForgeApiMountTest {
    @TempDir
    Path dir;
    private String previousGameDir;

    @BeforeEach
    void pointGameDirAtTemp() {
        previousGameDir = System.getProperty("vyne.gameDir");
        System.setProperty("vyne.gameDir", dir.toString());
    }

    @AfterEach
    void restoreGameDir() {
        if (previousGameDir == null) System.clearProperty("vyne.gameDir");
        else System.setProperty("vyne.gameDir", previousGameDir);
    }

    private Path universalJar() throws IOException {
        Path jar = dir.resolve("neoforge-universal.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (String name : new String[] {
                    "net/neoforged/neoforge/common/NeoForge.class",
                    "net/neoforged/neoforge/common/Tags.class",
                    "META-INF/services/example.Service",
                    "data/neoforge/tags/item/x.json"}) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(1);
                zip.closeEntry();
            }
        }
        return jar;
    }

    private static Set<String> entries(Path jar) throws IOException {
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            return zf.stream().map(ZipEntry::getName).collect(Collectors.toSet());
        }
    }

    @Test
    void fullApiJarKeepsClassesAndResourcesButNotSignatures() throws IOException {
        Path jar = universalJar();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(dir.resolve("signed.jar")))) {
            for (String name : new String[] {"net/neoforged/neoforge/common/NeoForge.class", "data/neoforge/x.json", "META-INF/NEOFORGE.SF", "META-INF/NEOFORGE.RSA"}) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(1);
                zip.closeEntry();
            }
        }
        Set<String> full = entries(RealNeoForgeApiMount.prepareFullApiJar(jar));
        assertTrue(full.containsAll(entries(jar)));
        Set<String> signed = entries(RealNeoForgeApiMount.prepareFullApiJar(dir.resolve("signed.jar")));
        assertEquals(Set.of("net/neoforged/neoforge/common/NeoForge.class", "data/neoforge/x.json"), signed);
    }

    @Test
    void preparedJarIsReusedFromTheCache() throws IOException {
        Path jar = universalJar();
        Path first = RealNeoForgeApiMount.prepareFullApiJar(jar);
        long stamp = Files.getLastModifiedTime(first).toMillis();
        Path second = RealNeoForgeApiMount.prepareFullApiJar(jar);
        assertEquals(first, second);
        assertEquals(stamp, Files.getLastModifiedTime(second).toMillis());
    }
}
