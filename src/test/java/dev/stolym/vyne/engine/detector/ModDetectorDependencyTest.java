/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.detector;

import dev.stolym.vyne.core.DetectedMod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ModDetectorDependencyTest {
    @TempDir
    Path tempDir;

    @Test
    void testNeoForgeDependencyRecording() throws IOException {
        Path modsDir = tempDir.resolve("mods");
        Files.createDirectories(modsDir);
        Path modJar = modsDir.resolve("testmod.jar");

        createNeoForgeJar(modJar, "testmod", "1.0.0", "[[dependencies.testmod]]\nmodId=\"neoforge\"\nversionRange=\"[21.1,)\"\n");

        ModDetector detector = new ModDetector(modsDir);
        List<DetectedMod> results = detector.scan();

        assertEquals(1, results.size());
        DetectedMod mod = results.get(0);
        assertEquals("testmod", mod.getModId());

        assertTrue(mod.getDependencies().containsKey("neoforge"), "Should contain neoforge dependency");
        assertEquals("[21.1,)", mod.getDependencies().get("neoforge"));
    }

    private void createNeoForgeJar(Path path, String modId, String version, String tomlContent) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(path.toFile()))) {
            JarEntry entry = new JarEntry("META-INF/neoforge.mods.toml");
            jos.putNextEntry(entry);
            String content = "modLoader=\"javafml\"\nloaderVersion=\"[1,)\"\n[[mods]]\nmodId=\"" + modId + "\"\nversion=\"" + version + "\"\n" + tomlContent;
            jos.write(content.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
    }
}
