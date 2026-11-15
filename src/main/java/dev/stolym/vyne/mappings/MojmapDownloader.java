/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mappings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stolym.vyne.util.VyneLogger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MojmapDownloader {
    private static final String VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String INTERMEDIARY_MAVEN = "https://maven.fabricmc.net/net/fabricmc/intermediary/";
    private static final Path CACHE_DIR = resolveCacheDir();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private MojmapDownloader() {}

    private static Path resolveCacheDir() {
        String gameDir = System.getProperty("vyne.gameDir");
        Path base = (gameDir != null && !gameDir.isBlank())
            ? Path.of(gameDir)
            : Path.of(System.getProperty("user.dir", "."));
        return base.toAbsolutePath().normalize().resolve(".vyne").resolve("mappings");
    }

    public static Path getMojmapCachePath(String mcVersion) {
        return CACHE_DIR.resolve("client-" + mcVersion + ".txt");
    }

    public static Path getIntermediaryCachePath(String mcVersion) {
        return CACHE_DIR.resolve("intermediary-" + mcVersion + ".tiny");
    }

    public static Path getIntermediaryJarPath(String mcVersion) {
        return CACHE_DIR.resolve("intermediary-" + mcVersion + ".jar");
    }

    public static Path getClientJarPath(String mcVersion) {
        return CACHE_DIR.resolve("client-" + mcVersion + ".jar");
    }

    public static Path getMojmapPath(String mcVersion) throws Exception {
        Path cached = getMojmapCachePath(mcVersion);
        if (Files.exists(cached)) {
            VyneLogger.info("[MojmapDownloader] Using cached Mojmap: " + cached);
            return cached;
        }
        Download mappings = findDownload(fetchVersionMeta(mcVersion), "client_mappings");
        if (mappings == null) {
            throw new IllegalStateException("No client mappings found in version meta for " + mcVersion);
        }
        VyneLogger.info("[MojmapDownloader] Downloading client mappings from Mojang...");
        writeAtomically(cached, downloadVerified(mappings.url(), mappings.sha1()));
        VyneLogger.info("[MojmapDownloader] Mojmap saved to: " + cached);
        return cached;
    }

    public static Path getIntermediaryPath(String mcVersion) throws Exception {
        Path cached = getIntermediaryCachePath(mcVersion);
        Path jarCached = getIntermediaryJarPath(mcVersion);
        if (Files.exists(cached) && Files.exists(jarCached)) {
            VyneLogger.info("[MojmapDownloader] Using cached intermediary: " + cached);
            return cached;
        }

        String url = INTERMEDIARY_MAVEN + mcVersion + "/intermediary-" + mcVersion + "-v2.jar";
        VyneLogger.info("[MojmapDownloader] Downloading intermediary mappings...");
        byte[] jarBytes = downloadVerified(url, fetchMavenSha1(url));

        byte[] tiny = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(jarBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("mappings/mappings.tiny")) {
                    tiny = zis.readAllBytes();
                    break;
                }
            }
        }
        if (tiny == null) {
            throw new IllegalStateException("mappings/mappings.tiny not found in intermediary jar");
        }

        writeAtomically(jarCached, jarBytes);
        writeAtomically(cached, tiny);
        VyneLogger.info("[MojmapDownloader] Intermediary saved to: " + cached);
        return cached;
    }

    public static Path getClientJar(String mcVersion) throws Exception {
        Path cached = getClientJarPath(mcVersion);
        if (Files.exists(cached)) {
            VyneLogger.info("[MojmapDownloader] Using cached client jar: " + cached);
            return cached;
        }
        Download client = findDownload(fetchVersionMeta(mcVersion), "client");
        if (client == null) {
            throw new IllegalStateException("No client jar found in version meta for " + mcVersion);
        }
        VyneLogger.info("[MojmapDownloader] Downloading Minecraft client jar (~30 MB, cached after this)...");
        writeAtomically(cached, downloadVerified(client.url(), client.sha1()));
        VyneLogger.info("[MojmapDownloader] Client jar saved to: " + cached);
        return cached;
    }

    public static Path getServerJar(String mcVersion) throws Exception {
        Path cached = CACHE_DIR.resolve("server-" + mcVersion + ".jar");
        if (Files.exists(cached)) return cached;
        Download server = findDownload(fetchVersionMeta(mcVersion), "server");
        if (server == null) {
            throw new IllegalStateException("No server jar found in version meta for " + mcVersion);
        }
        VyneLogger.info("[MojmapDownloader] Downloading Minecraft server jar (cached after this)...");
        byte[] bundler = downloadVerified(server.url(), server.sha1());
        byte[] inner = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bundler))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("META-INF/versions/") && entry.getName().endsWith(".jar")) {
                    inner = zis.readAllBytes();
                    break;
                }
            }
        }
        writeAtomically(cached, inner != null ? inner : bundler);
        return cached;
    }

    public static Path getServerMojmapPath(String mcVersion) throws Exception {
        Path cached = CACHE_DIR.resolve("server-" + mcVersion + ".txt");
        if (Files.exists(cached)) return cached;
        Download mappings = findDownload(fetchVersionMeta(mcVersion), "server_mappings");
        if (mappings == null) {
            throw new IllegalStateException("No server mappings found in version meta for " + mcVersion);
        }
        writeAtomically(cached, downloadVerified(mappings.url(), mappings.sha1()));
        return cached;
    }

    record Download(String url, String sha1) {}

    static String findVersionUrl(String manifestJson, String version) {
        JsonObject manifest = JsonParser.parseString(manifestJson).getAsJsonObject();
        for (JsonElement element : manifest.getAsJsonArray("versions")) {
            JsonObject entry = element.getAsJsonObject();
            if (version.equals(stringOrNull(entry, "id"))) {
                return stringOrNull(entry, "url");
            }
        }
        return null;
    }

    static Download findDownload(String versionMetaJson, String key) {
        JsonObject meta = JsonParser.parseString(versionMetaJson).getAsJsonObject();
        if (!meta.has("downloads")) return null;
        JsonObject downloads = meta.getAsJsonObject("downloads");
        if (!downloads.has(key)) return null;
        JsonObject file = downloads.getAsJsonObject(key);
        String url = stringOrNull(file, "url");
        return url == null ? null : new Download(url, stringOrNull(file, "sha1"));
    }

    private static String stringOrNull(JsonObject obj, String key) {
        JsonElement value = obj.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static String fetchVersionMeta(String mcVersion) throws Exception {
        VyneLogger.info("[MojmapDownloader] Fetching version manifest...");
        String manifest = new String(get(VERSION_MANIFEST), java.nio.charset.StandardCharsets.UTF_8);
        String versionUrl = findVersionUrl(manifest, mcVersion);
        if (versionUrl == null) {
            throw new IllegalStateException("Version " + mcVersion + " not found in Mojang's version manifest");
        }
        VyneLogger.info("[MojmapDownloader] Fetching version meta for " + mcVersion + "...");
        return new String(get(versionUrl), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String fetchMavenSha1(String artifactUrl) {
        try {
            String text = new String(get(artifactUrl + ".sha1"), java.nio.charset.StandardCharsets.US_ASCII).trim();
            int space = text.indexOf(' ');
            return space > 0 ? text.substring(0, space) : text;
        } catch (Exception e) {
            VyneLogger.warn("[MojmapDownloader] No checksum available for " + artifactUrl + "; skipping verification");
            return null;
        }
    }

    private static byte[] downloadVerified(String url, String expectedSha1) throws Exception {
        byte[] data = get(url);
        if (expectedSha1 != null && !expectedSha1.isBlank()) {
            String actual = sha1(data);
            if (!actual.equalsIgnoreCase(expectedSha1)) {
                throw new IOException("Checksum mismatch for " + url + ": expected " + expectedSha1 + ", got " + actual);
            }
        }
        return data;
    }

    private static byte[] get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "Vyne-Modloader")
                .GET()
                .build();
        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " while downloading " + url);
        }
        return response.body();
    }

    private static void writeAtomically(Path target, byte[] data) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        Files.write(tmp, data);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required by the Java platform", e);
        }
    }
}
