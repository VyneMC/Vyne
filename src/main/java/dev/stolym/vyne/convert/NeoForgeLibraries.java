/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import dev.stolym.vyne.util.VyneLogger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public record NeoForgeLibraries(Path fml, Path bus, List<Path> libraries) {
    private static final String NEOFORGED = "https://maven.neoforged.net/releases/";
    private static final String CENTRAL = "https://repo1.maven.org/maven2/";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private record Artifact(String repository, String group, String name, String version) {
        String path() {
            return group.replace('.', '/') + "/" + name + "/" + version + "/" + name + "-" + version + ".jar";
        }
    }

    private static final Artifact FML = new Artifact(NEOFORGED, "net.neoforged.fancymodloader", "loader", "4.0.45");
    private static final Artifact BUS = new Artifact(NEOFORGED, "net.neoforged", "bus", "8.0.5");
    private static final List<Artifact> COMMON = List.of(
            new Artifact(NEOFORGED, "cpw.mods", "securejarhandler", "3.0.8"));
    private static final List<Artifact> SERVER = List.of(
            new Artifact(CENTRAL, "org.jline", "jline-reader", "3.20.0"),
            new Artifact(CENTRAL, "org.jline", "jline-terminal", "3.20.0"),
            new Artifact(CENTRAL, "net.minecrell", "terminalconsoleappender", "1.3.0"));

    public static NeoForgeLibraries ensure(boolean dedicatedServer, Path baseDir) throws IOException {
        Path dir = baseDir.resolve(".vyne").resolve("libraries");
        List<Path> libraries = new ArrayList<>();
        for (Artifact artifact : COMMON) libraries.add(fetch(artifact, dir));
        if (dedicatedServer) {
            for (Artifact artifact : SERVER) libraries.add(fetch(artifact, dir));
        }
        return new NeoForgeLibraries(fetch(FML, dir), fetch(BUS, dir), List.copyOf(libraries));
    }

    private static Path fetch(Artifact artifact, Path dir) throws IOException {
        Path target = dir.resolve(artifact.path());
        if (Files.isRegularFile(target)) return target;
        String url = artifact.repository() + artifact.path();
        VyneLogger.info("[Hooks] Downloading " + artifact.name() + " " + artifact.version());
        byte[] data = get(url);
        String expected = new String(get(url + ".sha1"), StandardCharsets.US_ASCII).trim().split("\\s+")[0];
        if (!expected.equalsIgnoreCase(HookPatches.sha1(data))) {
            throw new IOException("Checksum mismatch for " + url);
        }
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        Files.write(tmp, data);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static byte[] get(String url) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(2))
                    .header("User-Agent", "Vyne-Modloader")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " for " + url);
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + url, e);
        }
    }
}
