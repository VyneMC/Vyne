/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.smoke;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JarCompositionTest {
    private static File findShadowJar() {
        File dir = java.nio.file.Paths.get("build", "libs").toFile();
        File[] jars = dir == null ? null
                : dir.listFiles((d, n) -> n.startsWith("vyne-loader-") && n.endsWith(".jar"));
        assertNotNull(jars, "build/libs does not exist — run ./gradlew shadowJar");
        assertTrue(jars.length > 0, "no vyne-loader jar in build/libs — run ./gradlew shadowJar");
        return Arrays.stream(jars).max(Comparator.comparingLong(File::lastModified)).orElseThrow();
    }

    @Test
    public void jarContainsNoMinecraftStubs() throws Exception {
        try (JarFile jar = new JarFile(findShadowJar())) {
            String stubs = jar.stream()
                    .map(ZipEntry::getName)
                    .filter(n -> n.startsWith("net/minecraft/"))
                    .collect(Collectors.joining("\n  "));
            assertTrue(stubs.isEmpty(),
                    "Compile-only net/minecraft stubs leaked into the jar:\n  " + stubs
                    + "\nShadow jar must not carry them — real classes come from the game at runtime.");
        }
    }

    @Test
    public void jarHasCorrectEntrypoints() throws Exception {
        try (JarFile jar = new JarFile(findShadowJar())) {
            Attributes main = jar.getManifest().getMainAttributes();
            assertEquals("dev.stolym.vyne.core.VyneLauncher",
                    main.getValue(Attributes.Name.MAIN_CLASS),
                    "Fabric Loader launches Vyne through this main class");

            assertNotNull(jar.getJarEntry("dev/stolym/vyne/core/VyneLauncher.class"),
                    "VyneLauncher class missing from jar");
            assertNotNull(jar.getEntry("META-INF/services/net.fabricmc.loader.impl.game.GameProvider"),
                    "GameProvider service registration missing — Fabric Loader would not discover Vyne");
            assertNull(jar.getJarEntry("net/neoforged/neoforge/common/NeoForge.class"),
                    "Vyne's jar must not carry NeoForge classes; NeoForge comes from META-INF/vyne/neoforge.jar");
        }
    }
}
