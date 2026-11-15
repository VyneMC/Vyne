/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the Licenses/LICENSE_VYNE file in the project root.
 */

package dev.stolym.vyne.install;

import dev.stolym.vyne.core.VyneLauncher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class PrismGlobalMetaInstaller {
    private PrismGlobalMetaInstaller() {}

    public static Result installGlobal(Path prismRoot) throws IOException {
        if (prismRoot == null) {
            throw new IOException("Could not locate the Prism/MultiMC data root.");
        }
        String version = VyneLauncher.VERSION;
        Path meta = prismRoot.resolve("meta").resolve(PrismComponentInstaller.VYNE_UID);
        Files.createDirectories(meta);

        Path libsRoot = prismRoot.resolve("libraries");
        Path mavenPath = libsRoot
            .resolve("com").resolve("stolym").resolve("vyne")
            .resolve("vyne-loader").resolve(version)
            .resolve("vyne-loader-" + version + ".jar");
        Files.createDirectories(mavenPath.getParent());
        Path selfJar = selfJar();
        Files.copy(selfJar, mavenPath, StandardCopyOption.REPLACE_EXISTING);

        Path versionJson = meta.resolve(version + ".json");
        Files.writeString(versionJson, buildVersionJson(version, libsRoot),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        Path indexJson = meta.resolve("index.json");
        Files.writeString(indexJson, buildIndexJson(version),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        return new Result(meta, versionJson, indexJson, mavenPath);
    }

    public record Result(Path metaDir, Path versionJson, Path indexJson, Path jarPath) {}

    private static String buildVersionJson(String version, Path libsRoot) {
        String url = libsRoot.toUri().toString();
        if (!url.endsWith("/")) url = url + "/";
        return "{\n"
            + "    \"formatVersion\": 1,\n"
            + "    \"name\": \"" + PrismComponentInstaller.VYNE_NAME + "\",\n"
            + "    \"uid\": \"" + PrismComponentInstaller.VYNE_UID + "\",\n"
            + "    \"version\": \"" + version + "\",\n"
            + "    \"requires\": [\n"
            + "        { \"uid\": \"" + PrismComponentInstaller.INTERMEDIARY_UID
                + "\", \"equals\": \"" + VyneLauncher.TARGET_MC + "\" }\n"
            + "    ],\n"
            + "    \"libraries\": [\n"
            + "        {\n"
            + "            \"name\": \"com.stolym.vyne:vyne-loader:" + version + "\",\n"
            + "            \"url\": \"" + url + "\"\n"
            + "        }\n"
            + "    ],\n"
            + "    \"mainClass\": \"" + PrismComponentInstaller.MAIN_CLASS + "\",\n"
            + "    \"type\": \"release\",\n"
            + "    \"releaseTime\": \"1970-01-01T00:00:00+00:00\"\n"
            + "}\n";
    }

    private static String buildIndexJson(String version) {
        return "{\n"
            + "    \"formatVersion\": 1,\n"
            + "    \"uid\": \"" + PrismComponentInstaller.VYNE_UID + "\",\n"
            + "    \"name\": \"" + PrismComponentInstaller.VYNE_NAME + "\",\n"
            + "    \"versions\": [\n"
            + "        {\n"
            + "            \"version\": \"" + version + "\",\n"
            + "            \"requires\": [\n"
            + "                { \"uid\": \"" + PrismComponentInstaller.INTERMEDIARY_UID
                + "\", \"equals\": \"" + VyneLauncher.TARGET_MC + "\" }\n"
            + "            ],\n"
            + "            \"type\": \"release\",\n"
            + "            \"releaseTime\": \"1970-01-01T00:00:00+00:00\"\n"
            + "        }\n"
            + "    ]\n"
            + "}\n";
    }

    private static Path selfJar() throws IOException {
        try {
            return Path.of(PrismGlobalMetaInstaller.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}
