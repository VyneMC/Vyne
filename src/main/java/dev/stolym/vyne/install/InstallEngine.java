/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the Licenses/LICENSE_VYNE file in the project root.
 */

package dev.stolym.vyne.install;

import dev.stolym.vyne.core.VyneLauncher;
import dev.stolym.vyne.install.gui.LauncherProfile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.function.BiConsumer;

public final class InstallEngine {
    private static final String VERSION_MANIFEST =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    public interface Progress extends BiConsumer<Integer, String> {}

    public enum Mode {
        AUTO, COMPONENT, LEGACY_MODS, SERVER, GLOBAL;
    }

    private InstallEngine() {}

    public static InstallResult installTo(LauncherProfile profile, Progress progress)
        throws Exception {
        return installTo(profile, Mode.AUTO, true, progress);
    }

    public static InstallResult installTo(LauncherProfile profile, Mode requestedMode,
                                          boolean replaceFabric, Progress progress) throws Exception {
        Mode mode = resolveMode(requestedMode, profile.kind());
        report(progress, 5, "Preparing " + profile.kind().label() + " (" + mode.name() + ")…");

        switch (mode) {
            case COMPONENT: return installAsPrismComponent(profile, replaceFabric, progress);
            case GLOBAL:    return installGlobalMeta(profile, progress);
            case SERVER:    return installServer(profile, progress);
            case LEGACY_MODS:
            default:        return installAsMod(profile, progress);
        }
    }

    private static Mode resolveMode(Mode requested, LauncherProfile.Kind kind) {
        if (requested != null && requested != Mode.AUTO) return requested;
        return switch (kind) {
            case PRISM, MULTIMC, POLYMC -> Mode.COMPONENT;
            case SERVER         -> Mode.SERVER;
            default             -> Mode.LEGACY_MODS;
        };
    }

    private static InstallResult installAsPrismComponent(LauncherProfile profile,
                                                         boolean replaceFabric,
                                                         Progress progress) throws IOException {
        Path instanceDir = profile.instanceDir();
        if (!Files.exists(instanceDir.resolve("mmc-pack.json"))) {
            report(progress, 15, "mmc-pack.json not found — falling back to mods/ install.");
            return installAsMod(profile, progress);
        }

        report(progress, 20, "Resolving Prism data root…");
        Path prismRoot = PrismComponentInstaller.guessPrismRoot(instanceDir);

        report(progress, 45, "Writing patches/ + mmc-pack.json…");
        PrismComponentInstaller.Result result =
            PrismComponentInstaller.install(instanceDir, prismRoot, replaceFabric);

        report(progress, 80, "Writing Vyne config…");
        writeVyneConfig(instanceDir);
        writeLauncherNotes(instanceDir, profile.kind(),
            instanceDir.resolve(".minecraft").resolve("mods"));

        report(progress, 100, "Component installed.");
        return new InstallResult(result.jarPath(),
            instanceDir.resolve(".minecraft").resolve("mods"), instanceDir,
            Mode.COMPONENT, PrismComponentInstaller.summary(result));
    }

    private static InstallResult installGlobalMeta(LauncherProfile profile, Progress progress)
        throws IOException {
        Path prismRoot = PrismComponentInstaller.guessPrismRoot(profile.instanceDir());
        if (prismRoot == null) {
            prismRoot = profile.instanceDir();
        }
        report(progress, 30, "Writing global meta under " + prismRoot.resolve("meta") + "…");
        PrismGlobalMetaInstaller.Result r = PrismGlobalMetaInstaller.installGlobal(prismRoot);
        report(progress, 100, "Vyne registered globally — pick it from 'Add Component'.");
        return new InstallResult(r.jarPath(), r.metaDir(), prismRoot, Mode.GLOBAL,
            java.util.List.of(
                "Meta entry    :  " + r.versionJson(),
                "Index entry   :  " + r.indexJson(),
                "Library jar   :  " + r.jarPath(),
                "",
                "Open Prism, create/edit any instance, click 'Add Component' → 'Vyne Loader'."
            ));
    }

    private static InstallResult installServer(LauncherProfile profile, Progress progress)
        throws Exception {
        Path instanceDir = profile.instanceDir();
        Files.createDirectories(instanceDir);
        Path destJar = instanceDir.resolve("vyne-loader-" + VyneLauncher.VERSION + ".jar");

        report(progress, 20, "Copying loader JAR…");
        copySelfJarTo(destJar);

        report(progress, 45, "Downloading Minecraft server jar…");
        downloadMinecraftServer(instanceDir);
        writeEula(instanceDir);

        report(progress, 75, "Writing start.sh / start.bat…");
        writeLaunchScripts(instanceDir, destJar);

        report(progress, 90, "Writing Vyne config…");
        writeVyneConfig(instanceDir);
        writeLauncherNotes(instanceDir, profile.kind(), instanceDir);

        report(progress, 100, "Server ready — run ./start.sh (Linux/macOS) or start.bat.");
        return new InstallResult(destJar, instanceDir, instanceDir, Mode.SERVER,
            java.util.List.of("Server jar:   " + destJar, "Run: ./start.sh"));
    }

    private static InstallResult installAsMod(LauncherProfile profile, Progress progress)
        throws IOException {
        Path instanceDir = profile.instanceDir();
        if (!Files.exists(instanceDir)) Files.createDirectories(instanceDir);

        switch (profile.kind()) {
            case VANILLA:     return installVanilla(profile, progress);
            case MODRINTH:    return installModrinth(profile, progress);
            case CURSEFORGE:  return installCurseForge(profile, progress);
            default:          return installGenericMod(profile, progress);
        }
    }

    private static InstallResult installVanilla(LauncherProfile profile, Progress progress)
        throws IOException {
        Path dotMinecraft = profile.instanceDir();
        report(progress, 30, "Copying loader jar to mods/…");
        VanillaLauncherInstaller.Result r = VanillaLauncherInstaller.install(dotMinecraft);
        report(progress, 80, "Writing Vyne config…");
        writeVyneConfig(dotMinecraft);
        report(progress, 100, r.profilePatched()
            ? "Launcher profile \"Vyne (Fabric)\" added — pick it in the launcher."
            : "Loader copied — install Fabric for " + VyneLauncher.TARGET_MC
                + " then rerun for the profile entry.");
        return new InstallResult(r.loaderJar(), r.modsDir(), dotMinecraft, Mode.LEGACY_MODS,
            VanillaLauncherInstaller.summary(r));
    }

    private static InstallResult installModrinth(LauncherProfile profile, Progress progress)
        throws IOException {
        Path instanceDir = profile.instanceDir();
        report(progress, 30, "Replacing Fabric Loader in Modrinth's version metadata…");
        ModrinthInstaller.Result r = ModrinthInstaller.install(instanceDir);
        report(progress, 80, "Writing Vyne config…");
        writeVyneConfig(instanceDir);
        report(progress, 100, r.replacedLoader()
            ? "Done — Vyne replaced Fabric Loader. Launch a Fabric profile in the Modrinth App."
            : "Done — no Fabric metadata found; create and launch a Fabric profile once, then rerun.");
        return new InstallResult(r.loaderJar(), r.modsDir(), instanceDir, Mode.LEGACY_MODS,
            ModrinthInstaller.summary(r));
    }

    private static InstallResult installCurseForge(LauncherProfile profile, Progress progress)
        throws IOException {
        Path instanceDir = profile.instanceDir();
        report(progress, 30, "Copying loader jar to instance mods/…");
        CurseForgeInstaller.Result r = CurseForgeInstaller.install(instanceDir);
        report(progress, 80, "Writing Vyne config…");
        writeVyneConfig(instanceDir);
        report(progress, 100, r.fabricLike()
            ? "Done — launch this instance in the CurseForge app."
            : "Done — switch the instance loader to Fabric in CurseForge.");
        return new InstallResult(r.loaderJar(), r.modsDir(), instanceDir, Mode.LEGACY_MODS,
            CurseForgeInstaller.summary(r));
    }

    private static InstallResult installGenericMod(LauncherProfile profile, Progress progress)
        throws IOException {
        Path instanceDir = profile.instanceDir();
        Path modsDir = resolveModsDir(instanceDir, profile.kind());
        Files.createDirectories(modsDir);

        report(progress, 40, "Copying Vyne jar to " + modsDir.getFileName() + "/…");
        Path destJar = modsDir.resolve("vyne-loader-" + VyneLauncher.VERSION + ".jar");
        copySelfJarTo(destJar);

        report(progress, 85, "Writing Vyne config…");
        writeVyneConfig(instanceDir);
        writeLauncherNotes(instanceDir, profile.kind(), modsDir);

        report(progress, 100, "Done.");
        return new InstallResult(destJar, modsDir, instanceDir, Mode.LEGACY_MODS,
            java.util.List.of("Jar:   " + destJar, "Mods: " + modsDir));
    }

    public record InstallResult(Path loaderJar, Path modsDir, Path instanceDir, Mode mode,
                                 java.util.List<String> summary) {
        public InstallResult(Path loaderJar, Path modsDir, Path instanceDir) {
            this(loaderJar, modsDir, instanceDir, Mode.LEGACY_MODS, java.util.List.of());
        }
    }

    private static void copySelfJarTo(Path dest) throws IOException {
        Path src;
        try { src = getSelfJar(); }
        catch (URISyntaxException e) { throw new IOException(e); }
        if (!Files.exists(src)) {
            throw new FileNotFoundException(
                "Vyne JAR not found at '" + src + "'. Run the shadow JAR, not classes/ from an IDE.");
        }
        if (src.toAbsolutePath().normalize().equals(dest.toAbsolutePath().normalize())) return;
        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void downloadMinecraftServer(Path dir) throws Exception {
        Path serverJar = dir.resolve("minecraft_server." + VyneLauncher.TARGET_MC + ".jar");
        if (Files.exists(serverJar)) return;
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        String manifest = http.send(
            HttpRequest.newBuilder(URI.create(VERSION_MANIFEST)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        ).body();
        String versionUrl = extractVersionUrl(manifest, VyneLauncher.TARGET_MC);
        if (versionUrl == null) {
            throw new IllegalStateException("Version " + VyneLauncher.TARGET_MC + " not in Mojang manifest");
        }
        String versionMeta = http.send(
            HttpRequest.newBuilder(URI.create(versionUrl)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        ).body();
        String serverUrl = extractServerDownloadUrl(versionMeta);
        if (serverUrl == null) {
            throw new IllegalStateException(
                "No server download URL for MC " + VyneLauncher.TARGET_MC);
        }
        http.send(
            HttpRequest.newBuilder(URI.create(serverUrl)).GET().build(),
            HttpResponse.BodyHandlers.ofFile(serverJar)
        );
    }

    private static void writeEula(Path dir) throws IOException {
        Path eula = dir.resolve("eula.txt");
        if (Files.exists(eula)) return;
        Files.writeString(eula,
            "# By changing the setting below to TRUE you are indicating your agreement\n"
            + "# to the Minecraft End User License Agreement (https://aka.ms/MinecraftEULA).\n"
            + "eula=true\n",
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void writeLaunchScripts(Path dir, Path vyneJarDest) throws IOException {
        String vyneJarName = vyneJarDest.getFileName().toString();
        String mcServerName = "minecraft_server." + VyneLauncher.TARGET_MC + ".jar";
        String mainClass = "dev.stolym.vyne.core.VyneLauncher";

        String addOpens =
            "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED "
            + "--add-opens=java.base/jdk.internal.reflect=ALL-UNNAMED "
            + "--add-opens=java.base/java.lang=ALL-UNNAMED "
            + "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED "
            + "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED "
            + "--add-opens=java.base/java.util=ALL-UNNAMED "
            + "--add-opens=java.base/sun.security.util=ALL-UNNAMED";

        Path sh = dir.resolve("start.sh");
        Files.writeString(sh,
            "#!/bin/sh\n"
            + "# Vyne " + VyneLauncher.VERSION + " — server launch script\n"
            + "JVM_ARGS=\"-Xmx4G -Xms1G " + addOpens + "\"\n"
            + "CLASSPATH=\"" + vyneJarName + ":" + mcServerName + "\"\n"
            + "exec java $JVM_ARGS -cp \"$CLASSPATH\" " + mainClass + " --server \"$@\"\n",
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try { sh.toFile().setExecutable(true); } catch (SecurityException ignored) {}

        Path bat = dir.resolve("start.bat");
        Files.writeString(bat,
            "@echo off\r\n"
            + "rem Vyne " + VyneLauncher.VERSION + " - server launch script\r\n"
            + "set JVM_ARGS=-Xmx4G -Xms1G " + addOpens + "\r\n"
            + "set CLASSPATH=" + vyneJarName + ";" + mcServerName + "\r\n"
            + "java %JVM_ARGS% -cp \"%CLASSPATH%\" " + mainClass + " --server %*\r\n"
            + "pause\r\n",
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void writeVyneConfig(Path instanceDir) throws IOException {
        Path config = instanceDir.resolve("vyne.json");
        if (Files.exists(config)) return;
        Files.writeString(config,
            "{\n"
            + "  \"vyne_version\": \"" + VyneLauncher.VERSION + "\",\n"
            + "  \"minecraft\": \"" + VyneLauncher.TARGET_MC + "\",\n"
            + "  \"log_level\": \"INFO\",\n"
            + "  \"show_hud\": true,\n"
            + "  \"loading_screen\": true,\n"
            + "  \"conflict_warnings\": true\n"
            + "}\n",
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void writeLauncherNotes(Path instanceDir, LauncherProfile.Kind kind, Path modsDir)
        throws IOException {
        Path notes = instanceDir.resolve("INSTALL-NOTES.txt");
        StringBuilder sb = new StringBuilder();
        sb.append("Vyne ").append(VyneLauncher.VERSION).append(" — installation notes\n");
        sb.append("=".repeat(48)).append("\n\n");
        sb.append("Launcher  : ").append(kind.label()).append("\n");
        sb.append("Minecraft : ").append(VyneLauncher.TARGET_MC).append("\n");
        sb.append("Mods dir  : ").append(modsDir.toAbsolutePath()).append("\n\n");
        sb.append("Launch Minecraft with a Fabric ").append(VyneLauncher.TARGET_MC)
            .append(" profile active in ").append(kind.label())
            .append(" — Vyne picks up Forge / NeoForge / Fabric mods from ")
            .append(modsDir.getFileName()).append("/.\n\n");
        sb.append("Support : https://github.com/VyneMC/Vyne/issues\n");
        Files.writeString(notes, sb.toString(),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static Path resolveModsDir(Path instanceDir, LauncherProfile.Kind kind) {
        switch (kind) {
            case CURSEFORGE: {
                Path overrides = instanceDir.resolve("overrides").resolve("mods");
                if (Files.exists(overrides)) return overrides;
                return instanceDir.resolve("mods");
            }
            case PRISM:
            case MULTIMC:
            case POLYMC: {
                Path dotMc = instanceDir.resolve(".minecraft");
                if (Files.exists(dotMc)) return dotMc.resolve("mods");
                Path mc = instanceDir.resolve("minecraft");
                if (Files.exists(mc)) return mc.resolve("mods");
                return instanceDir.resolve("mods");
            }
            default:
                return instanceDir.resolve("mods");
        }
    }

    public static Path getSelfJar() throws URISyntaxException {
        return Paths.get(InstallEngine.class
            .getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static String extractVersionUrl(String manifest, String version) {
        String needle = "\"id\":\"" + version + "\"";
        int idx = manifest.indexOf(needle);
        if (idx < 0) {
            needle = "\"id\": \"" + version + "\"";
            idx = manifest.indexOf(needle);
        }
        if (idx < 0) return null;
        int blockStart = manifest.lastIndexOf('{', idx);
        int blockEnd = manifest.indexOf('}', idx);
        if (blockStart < 0 || blockEnd < 0) return null;
        return extractJsonString(manifest.substring(blockStart, blockEnd), "url");
    }

    private static String extractServerDownloadUrl(String meta) {
        int downloadsIdx = meta.indexOf("\"downloads\"");
        if (downloadsIdx < 0) return null;
        int serverIdx = meta.indexOf("\"server\"", downloadsIdx);
        if (serverIdx < 0) return null;
        int blockStart = meta.indexOf('{', serverIdx);
        int blockEnd = meta.indexOf('}', blockStart);
        if (blockStart < 0 || blockEnd < 0) return null;
        return extractJsonString(meta.substring(blockStart, blockEnd), "url");
    }

    private static String extractJsonString(String block, String key) {
        String needle = "\"" + key + "\"";
        int ki = block.indexOf(needle);
        if (ki < 0) return null;
        int colon = block.indexOf(':', ki + needle.length());
        if (colon < 0) return null;
        int q1 = block.indexOf('"', colon + 1) + 1;
        if (q1 <= 0) return null;
        int q2 = block.indexOf('"', q1);
        if (q2 < 0) return null;
        return block.substring(q1, q2);
    }

    private static void report(Progress p, int pct, String msg) {
        if (p != null) {
            try { p.accept(pct, msg); } catch (Throwable ignored) {}
        }
    }
}
