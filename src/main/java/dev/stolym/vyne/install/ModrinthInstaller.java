/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the Licenses/LICENSE_VYNE file in the project root.
 */

package dev.stolym.vyne.install;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.stolym.vyne.core.VyneLauncher;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class ModrinthInstaller {
    public static final String VYNE_GROUP = "com.stolym.vyne";
    public static final String VYNE_ARTIFACT = "vyne-loader";
    public static final String MAIN_CLASS = "dev.stolym.vyne.core.VyneLauncher";
    private static final String BACKUP_SUFFIX = ".vyne-backup";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private ModrinthInstaller() {}

    public record Result(Path loaderJar, Path modsDir, List<Path> patchedVersions,
                         String detectedLoader, String detectedMc, boolean replacedLoader) {}

    public static Result install(Path profileDir) throws IOException {
        if (!Files.isDirectory(profileDir)) {
            throw new IOException("Not a Modrinth profile directory: " + profileDir);
        }
        Path modsDir = profileDir.resolve("mods");
        Files.createDirectories(modsDir);

        String loader = readLegacyField(profileDir.resolve("profile.json"), "loader");
        String mc = readLegacyField(profileDir.resolve("profile.json"), "game_version");

        Path root = findAppRoot(profileDir);
        List<Path> versionJsons = root == null ? List.of() : findFabricVersionJsons(root, mc);

        if (versionJsons.isEmpty()) {
            Path destJar = modsDir.resolve("vyne-loader-" + VyneLauncher.VERSION + ".jar");
            copySelfJarTo(destJar);
            return new Result(destJar, modsDir, List.of(), loader, mc, false);
        }

        Path libsRoot = root.resolve("meta").resolve("libraries");
        Path libJar = libsRoot
            .resolve(VYNE_GROUP.replace('.', '/')).resolve(VYNE_ARTIFACT)
            .resolve(VyneLauncher.VERSION)
            .resolve(VYNE_ARTIFACT + "-" + VyneLauncher.VERSION + ".jar");
        Files.createDirectories(libJar.getParent());
        copySelfJarTo(libJar);

        List<Path> patched = new ArrayList<>();
        for (Path json : versionJsons) {
            patchVersionJson(json, libsRoot);
            patched.add(json);
        }
        removeStaleModsJars(modsDir);
        return new Result(libJar, modsDir, patched, "fabric", mc, true);
    }

    public static Path findAppRoot(Path profileDir) {
        Path p = profileDir.toAbsolutePath().normalize().getParent();
        while (p != null) {
            if (Files.isDirectory(p.resolve("meta")) && Files.isDirectory(p.resolve("profiles"))) {
                return p;
            }
            p = p.getParent();
        }
        return null;
    }

    static List<Path> findFabricVersionJsons(Path root, String mc) {
        Path versions = root.resolve("meta").resolve("versions");
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(versions)) return out;
        String prefix = (mc != null && !mc.isBlank() ? mc : VyneLauncher.TARGET_MC) + "-";
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(versions)) {
            for (Path dir : ds) {
                String name = dir.getFileName().toString();
                if (!Files.isDirectory(dir) || !name.startsWith(prefix)) continue;
                Path json = dir.resolve(name + ".json");
                if (!Files.isRegularFile(json)) continue;
                String main = readMainClass(json);
                if (main != null && (main.contains("fabricmc") || main.equals(MAIN_CLASS))) {
                    out.add(json);
                }
            }
        } catch (IOException ignored) {
        }
        out.sort(java.util.Comparator.comparing(Path::toString));
        return out;
    }

    private static String readMainClass(Path json) {
        try {
            JsonObject o = GSON.fromJson(Files.readString(json), JsonObject.class);
            JsonElement e = o == null ? null : o.get("mainClass");
            return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    static void patchVersionJson(Path json, Path libsRoot) throws IOException {
        Path backup = json.resolveSibling(json.getFileName() + BACKUP_SUFFIX);
        if (!Files.exists(backup) && !MAIN_CLASS.equals(readMainClass(json))) {
            Files.copy(json, backup, StandardCopyOption.COPY_ATTRIBUTES);
        }
        JsonObject root = GSON.fromJson(Files.readString(json), JsonObject.class);
        root.addProperty("mainClass", MAIN_CLASS);

        JsonArray old = root.has("libraries") && root.get("libraries").isJsonArray()
            ? root.getAsJsonArray("libraries") : new JsonArray();
        JsonArray libs = new JsonArray();
        libs.add(vyneLibrary(libsRoot));
        for (JsonElement el : old) {
            String name = el.isJsonObject() && el.getAsJsonObject().has("name")
                ? el.getAsJsonObject().get("name").getAsString() : "";
            if (name.startsWith("net.fabricmc:fabric-loader:")) continue;
            if (name.startsWith(VYNE_GROUP + ":" + VYNE_ARTIFACT + ":")) continue;
            libs.add(el);
        }
        root.add("libraries", libs);
        Files.writeString(json, GSON.toJson(root),
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
    }

    private static JsonObject vyneLibrary(Path libsRoot) {
        String url = libsRoot.toUri().toString();
        if (!url.endsWith("/")) url = url + "/";
        JsonObject lib = new JsonObject();
        lib.addProperty("name", VYNE_GROUP + ":" + VYNE_ARTIFACT + ":" + VyneLauncher.VERSION);
        lib.addProperty("url", url);
        lib.addProperty("include_in_classpath", true);
        lib.addProperty("downloadable", false);
        return lib;
    }

    private static void removeStaleModsJars(Path modsDir) {
        try (Stream<Path> s = Files.list(modsDir)) {
            s.filter(f -> {
                String n = f.getFileName().toString();
                return n.startsWith("vyne-loader-") && n.endsWith(".jar");
            }).forEach(f -> {
                try { Files.deleteIfExists(f); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {
        }
    }

    public static List<String> summary(Result r) {
        List<String> lines = new ArrayList<>();
        lines.add("Loader jar   :  " + r.loaderJar());
        if (r.replacedLoader()) {
            for (Path p : r.patchedVersions()) {
                lines.add("Patched      :  " + p);
            }
            lines.add("Fabric Loader:  replaced by Vyne (original saved as *.json" + BACKUP_SUFFIX + ")");
            lines.add("");
            lines.add("Open the Modrinth App → pick a Fabric " + VyneLauncher.TARGET_MC
                + " profile → Play.");
            lines.add("Every profile using the patched Fabric version now launches through Vyne.");
            lines.add("If Modrinth re-downloads its Fabric metadata, run this installer again.");
        } else {
            lines.add("Profile mods :  " + r.modsDir());
            lines.add("");
            lines.add("No cached Fabric " + VyneLauncher.TARGET_MC + " version was found in Modrinth,");
            lines.add("so Vyne was copied to mods/ only. To make it replace the loader:");
            lines.add("create a Fabric " + VyneLauncher.TARGET_MC
                + " profile, launch it once, then run this installer again.");
        }
        return lines;
    }

    private static String readLegacyField(Path p, String field) {
        if (!Files.exists(p)) return null;
        try {
            JsonObject o = GSON.fromJson(Files.readString(p), JsonObject.class);
            JsonElement e = o == null ? null : o.get(field);
            return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void copySelfJarTo(Path dest) throws IOException {
        try {
            Path self = Path.of(ModrinthInstaller.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
            if (!Files.exists(self) || !Files.isRegularFile(self)) {
                throw new IOException("Vyne jar not a regular file: " + self);
            }
            if (self.toAbsolutePath().normalize().equals(dest.toAbsolutePath().normalize())) return;
            Files.copy(self, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Could not resolve Vyne jar location: " + e.getMessage(), e);
        }
    }
}
