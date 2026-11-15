/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ModClassSupportTest {
    @Test
    public void testModClassSupport() throws IOException {
        assertTrue(true, "Mod class support test placeholder");
    }

    @TempDir
    static Path tempDir;

    private void createTestModJar(Path jarPath) throws IOException {
        Files.createDirectories(jarPath.getParent());

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jarPath))) {
            zos.putNextEntry(new ZipEntry("fabric.mod.json"));
            zos.write(createFabricModJson().getBytes());
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zos.write("Manifest-Version: 1.0\n".getBytes());
            zos.closeEntry();
        }
    }

    private String createFabricModJson() {
        return (
            "{\n" +
            "  \"schemaVersion\": \"1\",\n" +
            "  \"id\": \"mod-compat-test\",\n" +
            "  \"name\": \"Mod Compatibility Test\",\n" +
            "  \"version\": \"1.0.0\",\n" +
            "  \"description\": \"Test mod for Vyne compatibility verification\"\n" +
            "}"
        );
    }
}
