/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the Licenses/LICENSE_VYNE file in the project root.
 */

package dev.stolym.vyne.install.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class LauncherDetector {
    private LauncherDetector() {}

    public static List<LauncherProfile> detectAll() {
        List<LauncherProfile> out = new ArrayList<>();
        addAll(out, detectPrism());
        addAll(out, detectMultiMC());
        addAll(out, detectPolyMC());
        addAll(out, detectATLauncher());
        addAll(out, detectModrinth());
        addAll(out, detectCurseForge());
        addAll(out, detectVanilla());
        out.sort(Comparator
            .comparing((LauncherProfile p) -> p.kind().ordinal())
            .thenComparing(LauncherProfile::displayName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static void addAll(List<LauncherProfile> dst, List<LauncherProfile> src) {
        if (src != null && !src.isEmpty()) dst.addAll(src);
    }

    public static List<LauncherProfile> detectPrism() {
        return scanInstanceRoots(familyInstanceRoots("PrismLauncher", "org.prismlauncher.PrismLauncher",
                "prismlauncher.cfg"),
            LauncherProfile.Kind.PRISM, LauncherDetector::isPrismInstance, LauncherDetector::probePrismInstance);
    }

    public static List<LauncherProfile> detectPolyMC() {
        return scanInstanceRoots(familyInstanceRoots("PolyMC", "org.polymc.PolyMC", "polymc.cfg"),
            LauncherProfile.Kind.POLYMC, LauncherDetector::isPrismInstance, LauncherDetector::probePrismInstance);
    }

    private static List<Path> dataDirs(String name, String flatpakId) {
        List<Path> dirs = new ArrayList<>();
        String os = osName();
        String home = System.getProperty("user.home", ".");
        if (os.contains("win")) {
            String appdata = System.getenv().getOrDefault("APPDATA", home + "\\AppData\\Roaming");
            dirs.add(Paths.get(appdata, name));
            dirs.add(Paths.get(home, "AppData", "Roaming", name));
        } else if (os.contains("mac")) {
            dirs.add(Paths.get(home, "Library", "Application Support", name));
        } else {
            String xdg = System.getenv("XDG_DATA_HOME");
            if (xdg != null && !xdg.isBlank()) dirs.add(Paths.get(xdg, name));
            dirs.add(Paths.get(home, ".local", "share", name));
            if (flatpakId != null) {
                dirs.add(Paths.get(home, ".var", "app", flatpakId, "data", name));
            }
        }
        return dirs;
    }

    private static List<Path> familyInstanceRoots(String name, String flatpakId, String cfgName) {
        List<Path> roots = new ArrayList<>();
        for (Path data : dataDirs(name, flatpakId)) {
            roots.add(data.resolve("instances"));
            Path custom = readConfiguredInstanceDir(data.resolve(cfgName), data);
            if (custom != null) roots.add(custom);
        }
        return roots;
    }

    private static Path readConfiguredInstanceDir(Path cfg, Path dataDir) {
        if (!Files.isRegularFile(cfg)) return null;
        try {
            for (String line : Files.readAllLines(cfg)) {
                if (line.startsWith("InstanceDir=")) {
                    String v = line.substring("InstanceDir=".length()).trim();
                    if (v.isEmpty()) return null;
                    Path p = Paths.get(v);
                    return p.isAbsolute() ? p : dataDir.resolve(p);
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return null;
    }

    private static boolean isPrismInstance(Path p) {
        return Files.exists(p.resolve("instance.cfg")) || Files.exists(p.resolve("mmc-pack.json"));
    }

    private static LauncherProfile probePrismInstance(Path p) {
        String mc = readInstanceMcVersion(p);
        String loader = readPrismLoaderHint(p);
        return LauncherProfile.of(LauncherProfile.Kind.PRISM, p.getFileName().toString(), p, mc, loader);
    }

    private static String readPrismLoaderHint(Path p) {
        Path pack = p.resolve("mmc-pack.json");
        if (!Files.exists(pack)) return null;
        try {
            String j = Files.readString(pack);
            if (j.contains("\"net.fabricmc.fabric-loader\"")) return "Fabric";
            if (j.contains("\"net.neoforged\"")) return "NeoForge";
            if (j.contains("\"net.minecraftforge\"")) return "Forge";
        } catch (IOException ignored) {
        }
        return null;
    }

    public static List<LauncherProfile> detectMultiMC() {
        List<Path> roots = new ArrayList<>(familyInstanceRoots("MultiMC", null, "multimc.cfg"));
        roots.addAll(familyInstanceRoots("multimc", null, "multimc.cfg"));
        String home = System.getProperty("user.home", ".");
        roots.add(Paths.get(home, "MultiMC", "instances"));
        roots.add(Paths.get(home, "multimc", "instances"));
        return scanInstanceRoots(roots, LauncherProfile.Kind.MULTIMC,
            LauncherDetector::isPrismInstance, LauncherDetector::probePrismInstance);
    }

    public static List<LauncherProfile> detectATLauncher() {
        List<Path> roots = new ArrayList<>();
        for (Path d : dataDirs("ATLauncher", "com.atlauncher.ATLauncher")) roots.add(d.resolve("instances"));
        String home = System.getProperty("user.home", ".");
        roots.add(Paths.get(home, "ATLauncher", "instances"));
        roots.add(Paths.get(home, ".atlauncher", "instances"));
        return scanInstanceRoots(roots, LauncherProfile.Kind.ATLAUNCHER,
            p -> Files.exists(p.resolve("instance.json")), LauncherDetector::probeATLauncherInstance);
    }

    private static LauncherProfile probeATLauncherInstance(Path p) {
        String mc = readProfileJsonField(p.resolve("instance.json"), "minecraft");
        return LauncherProfile.of(LauncherProfile.Kind.ATLAUNCHER, p.getFileName().toString(), p, mc, null);
    }

    public static List<LauncherProfile> detectModrinth() {
        List<Path> roots = new ArrayList<>();
        for (String name : new String[] {"ModrinthApp", "com.modrinth.theseus"}) {
            for (Path d : dataDirs(name, "com.modrinth.ModrinthApp")) roots.add(d.resolve("profiles"));
        }
        return scanInstanceRoots(roots, LauncherProfile.Kind.MODRINTH,
            LauncherDetector::isModrinthInstance, LauncherDetector::probeModrinthInstance);
    }

    private static boolean isModrinthInstance(Path p) {
        return Files.exists(p.resolve("profile.json"))
            || Files.exists(p.resolve("mods"))
            || Files.exists(p.resolve(".modrinth"));
    }

    private static LauncherProfile probeModrinthInstance(Path p) {
        String mc = readProfileJsonField(p.resolve("profile.json"), "game_version");
        String loader = readProfileJsonField(p.resolve("profile.json"), "loader");
        if (loader != null) loader = capitalize(loader);
        return LauncherProfile.of(LauncherProfile.Kind.MODRINTH, p.getFileName().toString(), p, mc, loader);
    }

    public static List<LauncherProfile> detectCurseForge() {
        List<Path> roots = new ArrayList<>();
        String os = osName();
        String home = System.getProperty("user.home", ".");
        if (os.contains("win")) {
            roots.add(Paths.get(home, "curseforge", "minecraft", "Instances"));
            roots.add(Paths.get(home, "Documents", "Curse", "Minecraft", "Instances"));
        } else if (os.contains("mac")) {
            roots.add(Paths.get(home, "Documents", "curseforge", "minecraft", "Instances"));
        } else {
            roots.add(Paths.get(home, "curseforge", "minecraft", "Instances"));
        }
        return scanInstanceRoots(roots, LauncherProfile.Kind.CURSEFORGE,
            LauncherDetector::isCurseForgeInstance, LauncherDetector::probeCurseForgeInstance);
    }

    private static boolean isCurseForgeInstance(Path p) {
        return Files.exists(p.resolve("minecraftinstance.json"))
            || Files.exists(p.resolve("manifest.json"));
    }

    private static LauncherProfile probeCurseForgeInstance(Path p) {
        String mc = readProfileJsonField(p.resolve("minecraftinstance.json"), "gameVersion");
        if (mc == null) mc = readManifestField(p.resolve("manifest.json"));
        return LauncherProfile.of(LauncherProfile.Kind.CURSEFORGE, p.getFileName().toString(), p, mc, null);
    }

    public static List<LauncherProfile> detectVanilla() {
        List<LauncherProfile> out = new ArrayList<>();
        Path dot = vanillaDotMinecraft();
        if (Files.isDirectory(dot)) {
            out.add(LauncherProfile.of(LauncherProfile.Kind.VANILLA, ".minecraft", dot));
        }
        return out;
    }

    public static Path vanillaDotMinecraft() {
        String os = osName();
        String home = System.getProperty("user.home", ".");
        if (os.contains("win")) {
            String appdata = System.getenv().getOrDefault("APPDATA", home + "\\AppData\\Roaming");
            return Paths.get(appdata, ".minecraft");
        }
        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "minecraft");
        }
        return Paths.get(home, ".minecraft");
    }

    private static List<LauncherProfile> scanInstanceRoots(
        List<Path> roots,
        LauncherProfile.Kind kind,
        java.util.function.Predicate<Path> isInstance,
        java.util.function.Function<Path, LauncherProfile> probe
    ) {
        List<LauncherProfile> out = new ArrayList<>();
        java.util.Set<Path> seen = new java.util.HashSet<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            if (!seen.add(realOrNormalized(root))) continue;
            try (Stream<Path> list = Files.list(root)) {
                list.filter(Files::isDirectory).forEach(p -> {
                    try {
                        if (isInstance.test(p)) {
                            out.add(probe.apply(p));
                        }
                    } catch (Throwable ignored) {
                    }
                });
            } catch (IOException ignored) {
            }
        }
        return out;
    }

    private static Path realOrNormalized(Path p) {
        try {
            return p.toRealPath();
        } catch (IOException e) {
            return p.toAbsolutePath().normalize();
        }
    }

    private static String readInstanceMcVersion(Path instance) {
        Path cfg = instance.resolve("instance.cfg");
        if (Files.exists(cfg)) {
            try {
                for (String line : Files.readAllLines(cfg)) {
                    if (line.startsWith("IntendedVersion=")) {
                        return line.substring("IntendedVersion=".length()).trim();
                    }
                }
            } catch (IOException ignored) {
            }
        }
        Path pack = instance.resolve("mmc-pack.json");
        if (Files.exists(pack)) {
            String j = null;
            try { j = Files.readString(pack); } catch (IOException ignored) {}
            if (j != null) {
                int mi = j.indexOf("\"net.minecraft\"");
                if (mi < 0) mi = j.indexOf("\"uid\":\"net.minecraft\"");
                if (mi >= 0) {
                    String slice = j.substring(mi, Math.min(j.length(), mi + 400));
                    String v = extractJsonString(slice, "version");
                    if (v != null) return v;
                }
            }
        }
        return null;
    }

    private static String readProfileJsonField(Path p, String field) {
        if (!Files.exists(p)) return null;
        try {
            return extractJsonString(Files.readString(p), field);
        } catch (IOException e) {
            return null;
        }
    }

    private static String readManifestField(Path manifest) {
        if (!Files.exists(manifest)) return null;
        try {
            String json = Files.readString(manifest);
            int mi = json.indexOf("\"minecraft\"");
            if (mi < 0) return null;
            int ve = json.indexOf("\"version\"", mi);
            if (ve < 0) return null;
            return extractJsonString(json.substring(ve, Math.min(json.length(), ve + 120)), "version");
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

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String osName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }
}
