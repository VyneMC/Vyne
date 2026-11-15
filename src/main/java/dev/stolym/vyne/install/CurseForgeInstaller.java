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
import java.util.ArrayList;
import java.util.List;

public final class CurseForgeInstaller {
    private CurseForgeInstaller() {}

    public static Result install(Path instanceDir) throws IOException {
        if (!Files.isDirectory(instanceDir)) {
            throw new IOException("Not a CurseForge instance directory: " + instanceDir);
        }
        Path modsDir = preferredModsDir(instanceDir);
        Files.createDirectories(modsDir);
        Path destJar = modsDir.resolve("vyne-loader-" + VyneLauncher.VERSION + ".jar");
        copySelfJarTo(destJar);

        String mc = readMinecraftInstanceField(instanceDir, "gameVersion");
        String loader = readCfBaseModLoader(instanceDir);
        boolean fabricLike = loader != null && loader.toLowerCase().contains("fabric");

        Path readme = instanceDir.resolve("VYNE-README.txt");
        Files.writeString(readme, buildReadme(loader, mc, modsDir, fabricLike),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return new Result(destJar, modsDir, mc, loader, fabricLike);
    }

    public record Result(Path loaderJar, Path modsDir, String detectedMc, String detectedLoader,
                         boolean fabricLike) {}

    public static List<String> summary(Result r) {
        List<String> lines = new ArrayList<>();
        lines.add("Loader jar      :  " + r.loaderJar());
        lines.add("Instance mods   :  " + r.modsDir());
        lines.add("Instance MC     :  " + (r.detectedMc() != null ? r.detectedMc() : "(unknown)"));
        lines.add("CF base loader  :  " + (r.detectedLoader() != null ? r.detectedLoader() : "(unknown)"));
        lines.add("");
        if (r.fabricLike()) {
            lines.add("Open the CurseForge app → launch this instance. Vyne rides the instance's");
            lines.add("Fabric Loader and handles Forge / NeoForge / Fabric mods from mods/.");
        } else {
            lines.add("This instance is not Fabric. In the CurseForge app, open the instance →");
            lines.add("Modloader tab → switch to Fabric (matching MC version) and save before");
            lines.add("launching so Vyne can activate.");
        }
        return lines;
    }

    private static Path preferredModsDir(Path instanceDir) {
        Path overrides = instanceDir.resolve("overrides").resolve("mods");
        if (Files.isDirectory(overrides)) return overrides;
        return instanceDir.resolve("mods");
    }

    private static String readMinecraftInstanceField(Path instanceDir, String key) {
        Path p = instanceDir.resolve("minecraftinstance.json");
        if (!Files.exists(p)) {
            p = instanceDir.resolve("manifest.json");
            if (!Files.exists(p)) return null;
        }
        try {
            return extractJsonString(Files.readString(p), key);
        } catch (IOException e) {
            return null;
        }
    }

    private static String readCfBaseModLoader(Path instanceDir) {
        Path p = instanceDir.resolve("minecraftinstance.json");
        if (!Files.exists(p)) return null;
        try {
            String json = Files.readString(p);
            int idx = json.indexOf("\"baseModLoader\"");
            if (idx < 0) idx = json.indexOf("\"modLoader\"");
            if (idx < 0) return null;
            String slice = json.substring(idx, Math.min(json.length(), idx + 800));
            String name = extractJsonString(slice, "name");
            if (name != null) return name;
            return extractJsonString(slice, "id");
        } catch (IOException e) {
            return null;
        }
    }

    private static String extractJsonString(String json, String key) {
        String needle = "\"" + key + "\"";
        int ki = json.indexOf(needle);
        if (ki < 0) return null;
        int colon = json.indexOf(':', ki + needle.length());
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon + 1) + 1;
        if (q1 <= 0) return null;
        int q2 = json.indexOf('"', q1);
        if (q2 < 0) return null;
        return json.substring(q1, q2);
    }

    private static String buildReadme(String loader, String mc, Path modsDir, boolean fabricLike) {
        StringBuilder sb = new StringBuilder();
        sb.append("Vyne ").append(VyneLauncher.VERSION).append(" — CurseForge instance notes\n");
        sb.append("=".repeat(48)).append("\n\n");
        sb.append("Minecraft    : ").append(mc != null ? mc : "(unknown)").append("\n");
        sb.append("CF loader    : ").append(loader != null ? loader : "(unknown)").append("\n");
        sb.append("Mods folder  : ").append(modsDir.toAbsolutePath()).append("\n\n");
        if (fabricLike) {
            sb.append("This instance is Fabric-based — Vyne will activate automatically.\n");
            sb.append("Drop any Forge / NeoForge / Fabric mod into mods/ and launch the instance.\n");
        } else {
            sb.append("This instance is not Fabric. Switch the instance's loader to Fabric in the\n");
            sb.append("CurseForge app (Modloader tab) before launching so Vyne can take over.\n");
        }
        return sb.toString();
    }

    private static void copySelfJarTo(Path dest) throws IOException {
        try {
            Path self = Path.of(CurseForgeInstaller.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
            if (!Files.exists(self) || !Files.isRegularFile(self)) {
                throw new IOException("Vyne jar not a regular file: " + self);
            }
            if (self.toAbsolutePath().normalize().equals(dest.toAbsolutePath().normalize())) return;
            Files.copy(self, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new IOException("Could not resolve Vyne jar location: " + e.getMessage(), e);
        }
    }
}
