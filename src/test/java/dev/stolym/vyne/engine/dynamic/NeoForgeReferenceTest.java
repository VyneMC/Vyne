/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic;

import dev.stolym.vyne.config.VyneContainmentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NeoForgeReferenceTest {
    @TempDir
    Path tempDir;

    @Test
    void testDownloadReference() {
        String version = "21.1.233";
        Path refDir = tempDir.resolve("references");

        Path jar = NeoForgeReferenceDownloader.resolve(version, refDir);

        assertNotNull(jar, "Downloader should return a path");
        assertTrue(Files.exists(jar), "JAR file should exist");
        assertTrue(jar.getFileName().toString().contains("-universal.jar"), "JAR name should contain -universal.jar");

        try {
            assertTrue(Files.size(jar) > 1024 * 1024, "JAR should be larger than 1MB");
        } catch (Exception e) {
            fail("Could not check JAR size: " + e.getMessage());
        }

        Path jar2 = NeoForgeReferenceDownloader.resolve(version, refDir);
        assertEquals(jar, jar2, "Should return the same path for cached JAR");
    }

    @Test
    void testInvalidVersion() {
        Path refDir = tempDir.resolve("references");
        Path jar = NeoForgeReferenceDownloader.resolve("99.9.999-invalid", refDir);
        assertNull(jar, "Invalid version should return null");
    }
}
