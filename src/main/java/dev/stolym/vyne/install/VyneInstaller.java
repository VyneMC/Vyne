/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.install;

import dev.stolym.vyne.core.VyneLauncher;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.Scanner;

public final class VyneInstaller {
    private static final String RESET  = "\u001B[0m";
    private static final String CYAN   = "\u001B[36m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";
    private static final String BOLD   = "\u001B[1m";
    private static final String DIM    = "\u001B[2m";

    private static final String VERSION_MANIFEST =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private VyneInstaller() {}

    public static void run(String[] args) throws Exception {
        if (containsArg(args, "--server")) {
            println("  Detected --server flag, running setup...");
            installToInstance(Paths.get("."), "Server");
            println("  Setup complete. Please run using the generated start.sh / start.bat or restart without --server.");
            return;
        }

        Scanner scanner = new Scanner(System.in);
        println("");
        println(BOLD + "  Select installation type:" + RESET);
        println("");
        println("  " + CYAN + "1" + RESET + "  PrismLauncher instance");
        println("  " + CYAN + "2" + RESET + "  Modrinth App instance");
        println("  " + CYAN + "3" + RESET + "  CurseForge instance");
        println("  " + CYAN + "4" + RESET + "  Default Minecraft Launcher (.minecraft)");
        println("  " + CYAN + "5" + RESET + "  Server (install to current directory)");
        println("  " + CYAN + "6" + RESET + "  Custom path");
        println("");
        print("  Enter choice [1-6]: ");

        try {
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1": installPrism(scanner);           break;
                case "2": installModrinth(scanner);        break;
                case "3": installCurseForge(scanner);      break;
                case "4": installDefaultLauncher(scanner); break;
                case "5": installToInstance(Paths.get("."), "Server"); break;
                case "6": installCustom(scanner);          break;
                default:
                    println(RED + "  Invalid choice. Exiting." + RESET);
            }
        } catch (Exception e) {
            println("");
            println(RED + BOLD + "  INSTALLATION FAILED!" + RESET);
            println(RED + "  Error: " + e.getMessage() + RESET);
            println("");
            println(DIM + "  Full trace for debugging:" + RESET);
            e.printStackTrace();
        }
    }

    private static void installPrism(Scanner scanner) throws Exception {
        println("");
        println(DIM + "  Tip: In PrismLauncher, right-click your instance → 'Folder'" + RESET);
        println("  Paste the instance folder path (the one containing mmc-pack.json):");
        print("  Path: ");
        Path instanceDir = Paths.get(scanner.nextLine().trim());
        warnIfMissing(instanceDir, "mmc-pack.json", "PrismLauncher");
        installToInstance(instanceDir, "PrismLauncher");
    }

    private static void installModrinth(Scanner scanner) throws Exception {
        println("");
        Path defaultPath = detectModrinthDefault();
        println("  Default Modrinth instances path: " + DIM + defaultPath + RESET);
        println("  Enter specific instance folder (ENTER to use default):");
        print("  Path: ");
        String raw = scanner.nextLine().trim();
        installToInstance(raw.isEmpty() ? defaultPath : Paths.get(raw), "Modrinth");
    }

    private static void installCurseForge(Scanner scanner) throws Exception {
        println("");
        Path defaultPath = detectCurseForgeDefault();
        println("  Default CurseForge instances path: " + DIM + defaultPath + RESET);
        println("  Enter specific instance / modpack folder (ENTER to use default):");
        print("  Path: ");
        String raw = scanner.nextLine().trim();
        installToInstance(raw.isEmpty() ? defaultPath : Paths.get(raw), "CurseForge");
    }

    private static void installDefaultLauncher(Scanner scanner) throws Exception {
        println("");
        Path dotMinecraft = detectDotMinecraft();
        println("  Detected .minecraft at: " + DIM + dotMinecraft + RESET);
        print("  Path [" + dotMinecraft + "]: ");
        String raw = scanner.nextLine().trim();
        installToInstance(raw.isEmpty() ? dotMinecraft : Paths.get(raw), "Default Launcher");
    }

    private static void installCustom(Scanner scanner) throws Exception {
        println("");
        println("  Enter the full path to your instance folder:");
        print("  Path: ");
        installToInstance(Paths.get(scanner.nextLine().trim()), "Custom");
    }

    private static void installToInstance(Path instanceDir, String launcherName) throws Exception {
        println("");
        println(BOLD + "  Installing to " + launcherName + " instance..." + RESET);

        if (!Files.exists(instanceDir)) {
            println(YELLOW + "  Warning: Directory not found — creating: " + instanceDir + RESET);
            try {
                Files.createDirectories(instanceDir);
            } catch (IOException e) {
                throw new IOException("Cannot create instance directory '" + instanceDir + "': "
                    + e.getMessage() + "\n  Check that the path is correct and you have write permissions.", e);
            }
        }

        Path modsDir = resolveModsDir(instanceDir, launcherName);
        if (!Files.exists(modsDir)) {
            println("  Creating " + modsDir.getFileName() + "/ directory...");
            try {
                Files.createDirectories(modsDir);
            } catch (IOException e) {
                throw new IOException("Cannot create mods directory '" + modsDir + "': " + e.getMessage(), e);
            }
        }

        Path vyneJar;
        try {
            vyneJar = getSelfJar();
        } catch (Exception e) {
            throw new Exception("Cannot locate Vyne JAR: " + e.getMessage()
                + "\n  Are you running from an IDE? Use the shadowJar/fatJar artifact instead.", e);
        }
        if (!Files.exists(vyneJar)) {
            throw new FileNotFoundException("Self JAR not found at '" + vyneJar + "'."
                + "\n  Are you running from an IDE? Use the shadowJar/fatJar artifact instead.");
        }

        Path destJar = instanceDir.resolve("vyne-loader-" + VyneLauncher.VERSION + ".jar");
        println("  Copying " + DIM + vyneJar.getFileName() + RESET);
        println("    -> " + DIM + destJar + RESET);
        try {
            if (!vyneJar.equals(destJar)) {
                Files.copy(vyneJar, destJar, StandardCopyOption.REPLACE_EXISTING);
            }
            println(GREEN + "  [OK] Vyne JAR established" + RESET);
        } catch (IOException e) {
            throw new IOException("Failed to copy/verify Vyne JAR at '" + destJar + "': " + e.getMessage()
                + "\n  Check disk space and write permissions.", e);
        }

        if ("Server".equals(launcherName)) {
            downloadMinecraftServer(instanceDir);
            writeEula(instanceDir);
            writeLaunchScripts(instanceDir, destJar);
        }

        writeVyneConfig(instanceDir);
        writeLauncherNotes(instanceDir, launcherName, modsDir);

        if ("Server".equals(launcherName)) {
             println("");
             println(GREEN + BOLD + "  Vyne Server has been successfully installed!" + RESET);
             println("  You can now start your server using: " + CYAN + "./start.sh" + RESET);
             return;
        }

        println("");
        println(GREEN + BOLD + "  +===================================================+" + RESET);
        println(GREEN + BOLD + "  |  Vyne " + padRight(VyneLauncher.VERSION + " installed successfully!", 43) + "|" + RESET);
        println(GREEN + BOLD + "  +===================================================+" + RESET);
        println("");
        println(BOLD + "  Next steps:" + RESET);
        if ("Server".equals(launcherName)) {
            println("  1. Run: " + CYAN + "./start.sh" + RESET + "  (Linux/Mac)  or  "
                + CYAN + "start.bat" + RESET + "  (Windows)");
            println("  2. The server will auto-detect all mods in: " + CYAN + modsDir.getFileName() + RESET);
            println("  3. Add mods to " + CYAN + modsDir.getFileName() + "/" + RESET + " — Fabric, Forge & NeoForge all work.");
        } else {
            println("  1. Install a Fabric " + VyneLauncher.TARGET_MC + " profile in your launcher");
            println("  2. Double check that Fabric Loader is active");
            println("  3. Your mods are in: " + CYAN + modsDir.getFileName() + RESET);
            println("  4. Launch & Enjoy.");
        }
        println("");
    }

    private static void downloadMinecraftServer(Path dir) throws Exception {
        Path serverJar = dir.resolve("minecraft_server." + VyneLauncher.TARGET_MC + ".jar");
        if (Files.exists(serverJar)) {
            println(GREEN + "  [OK] minecraft_server." + VyneLauncher.TARGET_MC + ".jar already present — skipping" + RESET);
            return;
        }

        println("  Fetching Mojang version manifest...");
        HttpClient http = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .build();

        String manifest = http.send(
            HttpRequest.newBuilder(URI.create(VERSION_MANIFEST)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        ).body();

        String versionUrl = extractVersionUrl(manifest, VyneLauncher.TARGET_MC);
        if (versionUrl == null) {
            throw new IllegalStateException(
                "Minecraft " + VyneLauncher.TARGET_MC + " not found in Mojang manifest. "
                + "Check that TARGET_MC is a valid release version.");
        }

        println("  Fetching version meta for " + VyneLauncher.TARGET_MC + "...");
        String versionMeta = http.send(
            HttpRequest.newBuilder(URI.create(versionUrl)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        ).body();

        String serverUrl = extractServerDownloadUrl(versionMeta);
        if (serverUrl == null) {
            throw new IllegalStateException(
                "No server download URL found in version meta for " + VyneLauncher.TARGET_MC + ". "
                + "This version may not have a dedicated server.");
        }

        println("  Downloading minecraft_server." + VyneLauncher.TARGET_MC + ".jar");
        println("  " + DIM + "(this may take a moment...)" + RESET);

        http.send(
            HttpRequest.newBuilder(URI.create(serverUrl)).GET().build(),
            HttpResponse.BodyHandlers.ofFile(serverJar)
        );

        println(GREEN + "  [OK] minecraft_server." + VyneLauncher.TARGET_MC + ".jar downloaded" + RESET);
    }

    private static void writeEula(Path dir) throws IOException {
        Path eula = dir.resolve("eula.txt");
        if (Files.exists(eula)) {
            println(DIM + "  eula.txt already exists — skipping" + RESET);
            return;
        }
        Files.writeString(eula,
            "#By changing the setting below to TRUE you are indicating your agreement\n"
            + "#to the Minecraft End User License Agreement (https://account.mojang.com/documents/minecraft_eula)\n"
            + "eula=true\n",
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        println(GREEN + "  [OK] eula.txt written (eula=true)" + RESET);
    }

    private static void writeLaunchScripts(Path dir, Path vyneJarDest) throws IOException {
        String vyneJarName  = vyneJarDest.getFileName().toString();
        String mcServerName = "minecraft_server." + VyneLauncher.TARGET_MC + ".jar";
        String mainClass    = "dev.stolym.vyne.core.VyneLauncher";

        Path sh = dir.resolve("start.sh");
        String shContent =
            "#!/bin/sh\n"
            + "# Vyne " + VyneLauncher.VERSION + " — Server launch script\n"
            + "# Generated by VyneInstaller. Edit JVM_ARGS to tune memory.\n"
            + "\n"
            + "JVM_ARGS=\"-Xmx4G -Xms1G\"\n"
            + "CLASSPATH=\"" + vyneJarName + ":" + mcServerName + "\"\n"
            + "\n"
            + "exec java $JVM_ARGS -cp \"$CLASSPATH\" " + mainClass + " --server \"$@\"\n";
        Files.writeString(sh, shContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        sh.toFile().setExecutable(true);
        println(GREEN + "  [OK] start.sh written to " + dir.toAbsolutePath() + RESET);

        Path bat = dir.resolve("start.bat");
        String batContent =
            "@echo off\r\n"
            + "rem Vyne " + VyneLauncher.VERSION + " - Server launch script\r\n"
            + "rem Generated by VyneInstaller. Edit JVM_ARGS to tune memory.\r\n"
            + "\r\n"
            + "set JVM_ARGS=-Xmx4G -Xms1G\r\n"
            + "set CLASSPATH=" + vyneJarName + ";" + mcServerName + "\r\n"
            + "\r\n"
            + "java %JVM_ARGS% -cp \"%CLASSPATH%\" " + mainClass + " --server %*\r\n"
            + "pause\r\n";
        Files.writeString(bat, batContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        println(GREEN + "  [OK] start.bat written to " + dir.toAbsolutePath() + RESET);
    }

    private static void writeVyneConfig(Path instanceDir) throws IOException {
        Path config = instanceDir.resolve("vyne.json");
        String json = "{\n"
                + "  \"vyne_version\": \"" + VyneLauncher.VERSION + "\",\n"
                + "  \"minecraft\": \"" + VyneLauncher.TARGET_MC + "\",\n"
                + "  \"log_level\": \"INFO\",\n"
                + "  \"show_hud\": true,\n"
                + "  \"loading_screen\": true,\n"
                + "  \"conflict_warnings\": true\n"
                + "}\n";
        Files.writeString(config, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        println(GREEN + "  [OK] vyne.json written" + RESET);
    }

    private static void writeLauncherNotes(Path instanceDir, String launcher, Path modsDir) throws IOException {
        Path notes = instanceDir.resolve("INSTALL-NOTES.txt");
        StringBuilder sb = new StringBuilder();
        sb.append("Vyne ").append(VyneLauncher.VERSION).append(" - Installation Notes\n");
        sb.append("=".repeat(52)).append("\n\n");
        sb.append("Launcher  : ").append(launcher).append("\n");
        sb.append("Minecraft : ").append(VyneLauncher.TARGET_MC).append("\n");
        sb.append("Mods dir  : ").append(modsDir.toAbsolutePath()).append("\n\n");

        sb.append("NOTES\n--------------\n");
        sb.append("Vyne is still in Alpha! That means you may encounter bugs and issues.\n");
        sb.append("Please report any issues you encounter on GitHub (link below).\n\n");

        sb.append("LAUNCHER STEPS\n--------------\n");
        switch (launcher) {
            case "Server":
                sb.append("1. Run ./start.sh (Linux/Mac) or start.bat (Windows)\n");
                sb.append("2. minecraft_server." + VyneLauncher.TARGET_MC + ".jar is already here\n");
                sb.append("3. Drop any Fabric/Forge/NeoForge mods into mods/\n");
                sb.append("4. Vyne handles detection and routing automatically\n");
                break;
            case "PrismLauncher":
                sb.append("1. Select your 1.21.1 Fabric instance in PrismLauncher\n");
                sb.append("2. Click Edit > Version — confirm Fabric Loader is listed\n");
                sb.append("3. The Vyne JAR is already in mods/ — just launch the instance\n");
                break;
            case "Modrinth":
                sb.append("1. Open Modrinth App > your 1.21.1 Fabric instance\n");
                sb.append("2. Go to Mods — you'll see vyne-loader listed\n");
                sb.append("3. Launch — Vyne activates automatically\n");
                break;
            case "CurseForge":
                sb.append("1. Open CurseForge > My Modpacks > your Fabric 1.21.1 pack\n");
                sb.append("2. Click the folder icon, open mods/ — Vyne JAR is there\n");
                sb.append("3. Launch — Vyne activates automatically\n");
                break;
            case "Default Launcher":
                sb.append("1. Open the Minecraft Launcher\n");
                sb.append("2. Confirm a Fabric ").append(VyneLauncher.TARGET_MC).append(" profile exists\n");
                sb.append("3. Select it and launch — Vyne activates via mods/\n");
                break;
            default:
                sb.append("Launch Minecraft with a Fabric ").append(VyneLauncher.TARGET_MC).append(" profile.\n");
                sb.append("Vyne activates automatically from the mods/ folder.\n");
        }

        sb.append("\nSUPPORT\n-------\n");
        sb.append("GitHub : https://github.com/VyneMC/Vyne/issues\n");
        sb.append("Site   : https://vyne.ryvione.dev\n");

        Files.writeString(notes, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        println(GREEN + "  [OK] INSTALL-NOTES.txt written" + RESET);
    }

    private static Path resolveModsDir(Path instanceDir, String launcherName) {
        switch (launcherName) {
            case "CurseForge":
                Path cfOverrides = instanceDir.resolve("overrides").resolve("mods");
                if (Files.exists(cfOverrides)) return cfOverrides;
                return instanceDir.resolve("mods");
            case "PrismLauncher":
                Path prismMc = instanceDir.resolve(".minecraft");
                if (Files.exists(prismMc)) return prismMc.resolve("mods");
                return instanceDir.resolve("mods");
            default:
                return instanceDir.resolve("mods");
        }
    }

    private static Path detectDotMinecraft() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return Paths.get(System.getenv().getOrDefault("APPDATA", "."), ".minecraft");
        } else if (os.contains("mac")) {
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support", "minecraft");
        }
        return Paths.get(System.getProperty("user.home"), ".minecraft");
    }

    private static Path detectModrinthDefault() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return Paths.get(System.getenv().getOrDefault("APPDATA", "."), "com.modrinth.theseus", "instances");
        } else if (os.contains("mac")) {
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support", "com.modrinth.theseus", "instances");
        }
        return Paths.get(System.getProperty("user.home"), ".local", "share", "com.modrinth.theseus", "instances");
    }

    private static Path detectCurseForgeDefault() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return Paths.get(System.getenv().getOrDefault("USERPROFILE", "."), "curseforge", "minecraft", "Instances");
        } else if (os.contains("mac")) {
            return Paths.get(System.getProperty("user.home"), "Documents", "curseforge", "minecraft", "Instances");
        }
        return Paths.get(System.getProperty("user.home"), "curseforge", "minecraft", "Instances");
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
        if (blockStart < 0) return null;
        int blockEnd = manifest.indexOf('}', idx);
        if (blockEnd < 0) return null;
        String block = manifest.substring(blockStart, blockEnd);
        return extractJsonString(block, "url");
    }

    private static String extractServerDownloadUrl(String meta) {
        int downloadsIdx = meta.indexOf("\"downloads\"");
        if (downloadsIdx < 0) return null;
        int serverIdx = meta.indexOf("\"server\"", downloadsIdx);
        if (serverIdx < 0) return null;
        int blockStart = meta.indexOf('{', serverIdx);
        if (blockStart < 0) return null;
        int blockEnd = meta.indexOf('}', blockStart);
        if (blockEnd < 0) return null;
        String block = meta.substring(blockStart, blockEnd);
        return extractJsonString(block, "url");
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

    private static Path getSelfJar() throws URISyntaxException {
        return Paths.get(VyneInstaller.class
                .getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static void warnIfMissing(Path dir, String marker, String launcher) {
        if (!Files.exists(dir)) {
            println(YELLOW + "  Warning: Directory not found: " + dir + RESET);
        } else if (!Files.exists(dir.resolve(marker))) {
            println(YELLOW + "  Warning: " + marker + " not found in " + dir
                    + " — is this really a " + launcher + " instance folder?" + RESET);
        }
    }

    private static boolean containsArg(String[] args, String target) {
        for (String a : args) if (target.equals(a)) return true;
        return false;
    }

    private static void println(String s) { System.out.println(s); }
    private static void print(String s)   { System.out.print(s); System.out.flush(); }
    private static String padRight(String s, int n) { return String.format("%-" + n + "s", s); }
}
