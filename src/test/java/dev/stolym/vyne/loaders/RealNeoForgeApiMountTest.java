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

    private static byte[] classReferencing(String name, String... refs) {
        org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(0);
        cw.visit(org.objectweb.asm.Opcodes.V21, org.objectweb.asm.Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        int i = 0;
        for (String ref : refs) {
            cw.visitField(org.objectweb.asm.Opcodes.ACC_PUBLIC, "f" + i++, "L" + ref + ";", null, null).visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    void integrationClassesAreThoseReachableFromVyneCode() {
        java.util.Map<String, byte[]> classes = new java.util.HashMap<>();
        classes.put("dev/stolym/vyne/Bridge.class", classReferencing("dev/stolym/vyne/Bridge", "net/neoforged/A"));
        classes.put("net/neoforged/A.class", classReferencing("net/neoforged/A", "net/neoforged/B"));
        classes.put("net/neoforged/B.class", classReferencing("net/neoforged/B"));
        classes.put("net/neoforged/B$Inner.class", classReferencing("net/neoforged/B$Inner"));
        classes.put("net/neoforged/Unused.class", classReferencing("net/neoforged/Unused"));
        assertEquals(Set.of("net/neoforged/A.class", "net/neoforged/B.class", "net/neoforged/B$Inner.class"),
                RealNeoForgeApiMount.integrationClasses(classes));
    }

    @Test
    void classesVyneProvidesAreLeftOut() throws IOException {
        Path out = RealNeoForgeApiMount.prepareStrippedApiJar(universalJar(), Set.of("net/neoforged/neoforge/common/NeoForge.class"));
        assertEquals(Set.of("net/neoforged/neoforge/common/Tags.class", "META-INF/services/example.Service"), entries(out));
    }

    @Test
    void differentProvidedSetGetsItsOwnCacheEntry() throws IOException {
        Path jar = universalJar();
        Path first = RealNeoForgeApiMount.prepareStrippedApiJar(jar, Set.of("net/neoforged/neoforge/common/NeoForge.class"));
        Path second = RealNeoForgeApiMount.prepareStrippedApiJar(jar, Set.of());
        assertNotEquals(first, second);
        assertTrue(entries(second).contains("net/neoforged/neoforge/common/NeoForge.class"));
    }
}
