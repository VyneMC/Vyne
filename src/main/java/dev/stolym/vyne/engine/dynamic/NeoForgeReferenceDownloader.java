/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic;

import dev.stolym.vyne.util.VyneLogger;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class NeoForgeReferenceDownloader {
    private static final String MAVEN_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge/";

    private NeoForgeReferenceDownloader() {}

    public static Path resolve(String version, Path referenceDir) {
        if (version == null || version.isEmpty()) return null;

        String universalJarName = "neoforge-" + version + "-universal.jar";
        String plainJarName = "neoforge-" + version + ".jar";

        Path target = referenceDir.resolve(universalJarName);
        if (Files.exists(target)) return target;

        VyneLogger.info("[NeoForgeDownloader] Attempting to fetch NeoForge " + version + " (universal)...");
        if (tryDownload(version, universalJarName, target)) {
            return target;
        }

        target = referenceDir.resolve(plainJarName);
        if (Files.exists(target)) return target;

        VyneLogger.info("[NeoForgeDownloader] Universal not found, trying plain JAR...");
        if (tryDownload(version, plainJarName, target)) {
            return target;
        }

        VyneLogger.error("[NeoForgeDownloader] Failed to resolve NeoForge " + version + " from Maven.");
        return null;
    }

    public static boolean tryDownload(String version, String fileName, Path target) {
        String urlStr = MAVEN_URL + version + "/" + fileName;
        int maxRedirects = 5;
        int redirectCount = 0;

        while (redirectCount < maxRedirects) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "Vyne-Modloader");

                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == 307 ||
                    code == 308) {
                    String newUrl = conn.getHeaderField("Location");
                    if (newUrl != null && !newUrl.isEmpty()) {
                        urlStr = newUrl;
                        redirectCount++;
                        VyneLogger.info("[NeoForgeDownloader] Redirecting to " + urlStr);
                        continue;
                    }
                }

                if (code == HttpURLConnection.HTTP_OK) {
                    Files.createDirectories(target.getParent());
                    Path tmp = target.resolveSibling(fileName + ".part");
                    try (InputStream is = conn.getInputStream()) {
                        Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
                    }
                    String expected = fetchSha1(urlStr);
                    if (expected != null && !expected.equalsIgnoreCase(sha1(tmp))) {
                        Files.deleteIfExists(tmp);
                        VyneLogger.error("[NeoForgeDownloader] Checksum mismatch for " + fileName + "; discarded the download");
                        return false;
                    }
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    VyneLogger.success("[NeoForgeDownloader] Successfully downloaded " + fileName);
                    return true;
                } else {
                    VyneLogger.warn("[NeoForgeDownloader] Failed to download " + fileName + ", HTTP status: " + code);
                }
            } catch (Exception e) {
                VyneLogger.error("[NeoForgeDownloader] Error downloading " + fileName + ": " + e.getMessage(), e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            break;
        }
        return false;
    }

    private static String fetchSha1(String artifactUrl) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(artifactUrl + ".sha1").openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Vyne-Modloader");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream is = conn.getInputStream()) {
                String text = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII).trim();
                int space = text.indexOf(' ');
                return space > 0 ? text.substring(0, space) : text;
            }
        } catch (Exception e) {
            VyneLogger.warn("[NeoForgeDownloader] No checksum available for " + artifactUrl + "; skipping verification");
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String sha1(Path file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = is.read(buffer)) > 0) digest.update(buffer, 0, read);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
}
