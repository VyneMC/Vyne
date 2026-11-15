/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.detector;

import dev.stolym.vyne.core.DetectedMod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class JarInJarDetectionTest {
    @TempDir
    Path gameDir;

    private static byte[] neoforgeJar(String modId, String version, Map<String, byte[]> nested) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("META-INF/neoforge.mods.toml"));
            String toml = "modLoader=\"javafml\"\nloaderVersion=\"[1,)\"\nlicense=\"MIT\"\n[[mods]]\nmodId=\"" + modId + "\"\nversion=\"" + version + "\"\n";
            zip.write(toml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (Map.Entry<String, byte[]> e : nested.entrySet()) {
                zip.putNextEntry(new ZipEntry("META-INF/jarjar/" + e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private List<DetectedMod> scan() throws IOException {
        return new ModDetector(gameDir.resolve("mods")).scan();
    }

    private static Map<String, String> versions(List<DetectedMod> mods) {
        return mods.stream().collect(Collectors.toMap(DetectedMod::getModId, DetectedMod::getVersion, (a, b) -> a + "," + b));
    }

    @Test
    void bundledLibraryIsDetectedWithHighestVersion() throws IOException {
        Path mods = Files.createDirectories(gameDir.resolve("mods"));
        Files.write(mods.resolve("a.jar"), neoforgeJar("modA", "1.0", Map.of("lib-1.2.0.jar", neoforgeJar("examplelib", "1.2.0", Map.of()))));
        Files.write(mods.resolve("b.jar"), neoforgeJar("modB", "1.0", Map.of("lib-1.1.0.jar", neoforgeJar("examplelib", "1.1.0", Map.of()))));

        Map<String, String> found = versions(scan());
        assertEquals("1.2.0", found.get("examplelib"));
        assertTrue(found.containsKey("modA") && found.containsKey("modB"));
    }

    @Test
    void standaloneCopyInModsFolderWins() throws IOException {
        Path mods = Files.createDirectories(gameDir.resolve("mods"));
        Files.write(mods.resolve("a.jar"), neoforgeJar("modA", "1.0", Map.of("lib-9.0.jar", neoforgeJar("examplelib", "9.0", Map.of()))));
        Files.write(mods.resolve("examplelib-1.0.jar"), neoforgeJar("examplelib", "1.0", Map.of()));

        assertEquals("1.0", versions(scan()).get("examplelib"));
    }

    @Test
    void nestedBundlesAreFollowed() throws IOException {
        Path mods = Files.createDirectories(gameDir.resolve("mods"));
        byte[] inner = neoforgeJar("innerlib", "2.0", Map.of());
        byte[] middle = neoforgeJar("middlelib", "1.0", Map.of("inner.jar", inner));
        Files.write(mods.resolve("a.jar"), neoforgeJar("modA", "1.0", Map.of("middle.jar", middle)));

        Map<String, String> found = versions(scan());
        assertEquals("1.0", found.get("middlelib"));
        assertEquals("2.0", found.get("innerlib"));
    }
}
