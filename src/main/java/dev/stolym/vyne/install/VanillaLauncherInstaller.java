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
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public final class VanillaLauncherInstaller {
    public static final String PROFILE_ID = "vyne-loader";
    public static final String PROFILE_NAME = "Vyne (Fabric)";

    private VanillaLauncherInstaller() {}

    public static Result install(Path dotMinecraft) throws IOException {
        if (!Files.isDirectory(dotMinecraft)) {
            throw new IOException("Not a .minecraft directory: " + dotMinecraft);
        }
        Path modsDir = dotMinecraft.resolve("mods");
        Files.createDirectories(modsDir);
        Path destJar = modsDir.resolve("vyne-loader-" + VyneLauncher.VERSION + ".jar");
        copySelfJarTo(destJar);

        String fabricVersionId = detectFabricVersionId(dotMinecraft, VyneLauncher.TARGET_MC).orElse(null);
        Path profilesPath = dotMinecraft.resolve("launcher_profiles.json");
        boolean profilePatched = false;
        if (fabricVersionId != null && Files.exists(profilesPath)) {
            String original = Files.readString(profilesPath);
            String patched = upsertProfile(original, fabricVersionId, dotMinecraft);
            if (!patched.equals(original)) {
                Files.writeString(profilesPath, patched,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
                profilePatched = true;
            }
        }
        return new Result(destJar, modsDir, fabricVersionId, profilePatched, profilesPath);
    }

    public record Result(Path loaderJar, Path modsDir, String fabricVersionId,
                         boolean profilePatched, Path launcherProfiles) {}

    public static List<String> summary(Result r) {
        List<String> lines = new ArrayList<>();
        lines.add("Loader jar       :  " + r.loaderJar());
        lines.add("Mods directory   :  " + r.modsDir());
        if (r.fabricVersionId() != null) {
            lines.add("Fabric version   :  " + r.fabricVersionId());
            if (r.profilePatched()) {
                lines.add("Launcher profile :  added/updated \"" + PROFILE_NAME + "\" in "
                    + r.launcherProfiles().getFileName());
                lines.add("");
                lines.add("Open the Minecraft launcher → Installations → pick \"" + PROFILE_NAME
                    + "\" → Play.");
            } else {
                lines.add("Launcher profile :  no changes (already registered)");
            }
        } else {
            lines.add("");
            lines.add("No Fabric install detected for Minecraft " + VyneLauncher.TARGET_MC + ".");
            lines.add("Install Fabric Loader for " + VyneLauncher.TARGET_MC
                + " from https://fabricmc.net/use/ then relaunch the installer to add the profile.");
        }
        return lines;
    }

    public static Optional<String> detectFabricVersionId(Path dotMinecraft, String mc) {
        Path versions = dotMinecraft.resolve("versions");
        if (!Files.isDirectory(versions)) return Optional.empty();
        try (Stream<Path> list = Files.list(versions)) {
            return list
                .filter(Files::isDirectory)
                .map(p -> p.getFileName().toString())
                .filter(n -> {
                    String lc = n.toLowerCase(Locale.ROOT);
                    return lc.startsWith("fabric-loader-") && lc.endsWith("-" + mc);
                })
                .sorted(java.util.Comparator.reverseOrder())
                .findFirst();
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    static String upsertProfile(String json, String fabricVersionId, Path dotMinecraft) {
        if (json == null || json.isBlank()) return json;
        int profilesIdx = json.indexOf("\"profiles\"");
        if (profilesIdx < 0) return json;
        int objStart = json.indexOf('{', profilesIdx);
        int objEnd = findMatchingBracket(json, objStart, '{', '}');
        if (objStart < 0 || objEnd < 0) return json;

        String before = json.substring(0, objStart + 1);
        String objBody = json.substring(objStart + 1, objEnd);
        String after = json.substring(objEnd);

        String created = nowIso();
        String icon = "Grass";
        String modsDir = dotMinecraft.resolve("mods").toAbsolutePath().toString()
            .replace("\\", "\\\\").replace("\"", "\\\"");
        String entry = ""
            + "    \"" + PROFILE_ID + "\": {\n"
            + "      \"created\": \"" + created + "\",\n"
            + "      \"gameDir\": \"" + dotMinecraft.toAbsolutePath().toString()
                    .replace("\\", "\\\\").replace("\"", "\\\"") + "\",\n"
            + "      \"icon\": \"" + icon + "\",\n"
            + "      \"lastUsed\": \"" + created + "\",\n"
            + "      \"lastVersionId\": \"" + fabricVersionId + "\",\n"
            + "      \"name\": \"" + PROFILE_NAME + "\",\n"
            + "      \"type\": \"custom\"\n"
            + "    }";

        boolean alreadyPresent = objBody.contains("\"" + PROFILE_ID + "\"");
        String newBody;
        if (alreadyPresent) {
            int entryStart = objBody.indexOf("\"" + PROFILE_ID + "\"");
            int entryObjStart = objBody.indexOf('{', entryStart);
            int entryObjEnd = findMatchingBracket(objBody, entryObjStart, '{', '}');
            if (entryObjStart < 0 || entryObjEnd < 0) return json;
            int fullStart = entryStart;
            int fullEnd = entryObjEnd + 1;
            while (fullEnd < objBody.length()
                && (objBody.charAt(fullEnd) == ',' || Character.isWhitespace(objBody.charAt(fullEnd)))) {
                fullEnd++;
            }
            String trailing = objBody.substring(entryObjEnd + 1, fullEnd);
            String replacement = entry + (trailing.contains(",") ? "," : "") + "\n";
            newBody = objBody.substring(0, fullStart) + replacement + objBody.substring(fullEnd);

            newBody = newBody.replaceAll(",\\s*}", "}");
        } else {
            String trimmed = objBody.trim();
            if (trimmed.isEmpty()) {
                newBody = "\n" + entry + "\n  ";
            } else {
                String body = objBody.stripTrailing();
                String separator = body.endsWith(",") ? "\n" : ",\n";
                newBody = body + separator + entry + "\n  ";
            }
        }

        String candidate = before + newBody + after;
        if (countUnescaped(candidate, '{') != countUnescaped(candidate, '}')) return json;
        return candidate;
    }

    private static int countUnescaped(String s, char c) {
        int n = 0;
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inString = !inString;
            if (inString) continue;
            if (ch == c) n++;
        }
        return n;
    }

    private static int findMatchingBracket(String s, int startAt, char open, char close) {
        if (startAt < 0 || startAt >= s.length() || s.charAt(startAt) != open) return -1;
        int depth = 0;
        boolean inString = false;
        for (int i = startAt; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inString = !inString;
            if (inString) continue;
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static String nowIso() {
        return java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static void copySelfJarTo(Path dest) throws IOException {
        try {
            Path self = Path.of(VanillaLauncherInstaller.class.getProtectionDomain()
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
