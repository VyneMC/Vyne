/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mappings;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MojmapDownloaderTest {
    private static final String MANIFEST = """
            {
              "latest": { "release": "1.21.1", "snapshot": "24w99a" },
              "versions": [
                { "id": "24w99a", "type": "snapshot", "url": "https://example/24w99a.json", "sha1": "aaa" },
                { "id": "1.21.1", "type": "release",  "url": "https://example/1.21.1.json", "sha1": "bbb" },
                { "id": "1.21",   "type": "release",  "url": "https://example/1.21.json",   "sha1": "ccc" }
              ]
            }
            """;

    private static final String VERSION_META = """
            {
              "id": "1.21.1",
              "downloads": {
                "client":          { "sha1": "c1", "size": 1, "url": "https://example/client.jar" },
                "client_mappings": { "sha1": "m1", "size": 1, "url": "https://example/client.txt" },
                "server":          { "sha1": "s1", "size": 1, "url": "https://example/server.jar" }
              }
            }
            """;

    @Test
    void findsVersionUrlEvenWhenVersionIsLatest() {
        assertEquals("https://example/1.21.1.json", MojmapDownloader.findVersionUrl(MANIFEST, "1.21.1"));
    }

    @Test
    void versionIdMustMatchExactly() {
        assertEquals("https://example/1.21.json", MojmapDownloader.findVersionUrl(MANIFEST, "1.21"));
        assertNull(MojmapDownloader.findVersionUrl(MANIFEST, "1.2"));
    }

    @Test
    void distinguishesClientFromClientMappings() {
        var client = MojmapDownloader.findDownload(VERSION_META, "client");
        var mappings = MojmapDownloader.findDownload(VERSION_META, "client_mappings");
        assertEquals(new MojmapDownloader.Download("https://example/client.jar", "c1"), client);
        assertEquals(new MojmapDownloader.Download("https://example/client.txt", "m1"), mappings);
        assertNull(MojmapDownloader.findDownload(VERSION_META, "server_mappings"));
    }

    @Test
    void sha1MatchesKnownVector() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d",
                MojmapDownloader.sha1("abc".getBytes(StandardCharsets.US_ASCII)));
    }
}
