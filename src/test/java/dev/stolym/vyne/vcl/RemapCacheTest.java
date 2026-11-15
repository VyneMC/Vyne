/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RemapCacheTest {
    @TempDir
    Path cacheDir;

    @TempDir
    Path jarDir;

    private Path fakeJar(String name, byte[] content) throws Exception {
        Path jar = jarDir.resolve(name);
        Files.write(jar, content);
        return jar;
    }

    @Test
    void putThenGetRoundTrips() {
        RemapCache.setCacheRoot(cacheDir);
        RemapCache.setMappingsFingerprint("fp1");
        RemapCache.put("sha-one", "com/example/A", new byte[]{1, 2, 3});
        assertArrayEquals(new byte[]{1, 2, 3}, RemapCache.get("sha-one", "com/example/A"));
    }

    @Test
    void getOnMissReturnsNull() {
        RemapCache.setCacheRoot(cacheDir);
        RemapCache.setMappingsFingerprint("fp-miss");
        assertNull(RemapCache.get("no-such-jar", "com/example/Missing"));
    }

    @Test
    void nullArgumentsAreNeverCached() {
        RemapCache.setCacheRoot(cacheDir);
        RemapCache.put(null, "a", new byte[]{1});
        RemapCache.put("b", null, new byte[]{1});
        RemapCache.put("b", "a", null);
        assertNull(RemapCache.get(null, "a"));
        assertNull(RemapCache.get("b", null));
    }

    @Test
    void fingerprintChangeInvalidatesMemory() {
        RemapCache.setCacheRoot(cacheDir);
        RemapCache.setMappingsFingerprint("fp-before");
        RemapCache.put("sha-x", "com/example/B", new byte[]{9});
        RemapCache.setMappingsFingerprint("fp-after");
        assertNull(RemapCache.get("sha-x", "com/example/B"),
                "entries from an older mappings fingerprint must not leak into the new one");
    }

    @Test
    void jarSha256IsStableAndMemoized() throws Exception {
        Path jar = fakeJar("mod.jar", new byte[]{4, 5, 6});
        String first = RemapCache.jarSha256(jar);
        String second = RemapCache.jarSha256(jar);
        assertNotNull(first);
        assertEquals(first, second);
        Path other = fakeJar("other.jar", new byte[]{7, 8});
        assertNotEquals(first, RemapCache.jarSha256(other));
    }

    @Test
    void jarSha256HandlesNullAndMissing() {
        assertNull(RemapCache.jarSha256(null));
        assertNull(RemapCache.jarSha256(jarDir.resolve("does-not-exist.jar")));
    }
}
