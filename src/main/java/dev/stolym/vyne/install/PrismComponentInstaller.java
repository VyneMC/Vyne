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

public final class PrismComponentInstaller {
    public static final String VYNE_UID           = "com.stolym.vyne.vyne-loader";
    public static final String VYNE_NAME          = "Vyne Loader";
    public static final String FABRIC_LOADER_UID  = "net.fabricmc.fabric-loader";
    public static final String INTERMEDIARY_UID   = "net.fabricmc.intermediary";
    public static final String MAIN_CLASS         = "dev.stolym.vyne.core.VyneLauncher";

    private PrismComponentInstaller() {}

    public static Result install(Path instanceDir, Path prismRoot, boolean replaceFabric)
        throws IOException {
        String version = VyneLauncher.VERSION;
        Path jarSource = jarSource();
        Path libsRoot = (prismRoot != null ? prismRoot : instanceDir).resolve("libraries");
        Path mavenPath = libsRoot
            .resolve("com").resolve("stolym").resolve("vyne")
            .resolve("vyne-loader").resolve(version)
            .resolve("vyne-loader-" + version + ".jar");
        Files.createDirectories(mavenPath.getParent());
        Files.copy(jarSource, mavenPath, StandardCopyOption.REPLACE_EXISTING);

        Path patches = instanceDir.resolve("patches");
        Files.createDirectories(patches);
        Path componentJson = patches.resolve(VYNE_UID + ".json");
        Files.writeString(componentJson, buildComponentJson(version, libsRoot),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        Path mmcPack = instanceDir.resolve("mmc-pack.json");
        if (!Files.exists(mmcPack)) {
            throw new IOException("mmc-pack.json not found at " + mmcPack
                + " — is this a valid Prism/MultiMC instance directory?");
        }
        String original = Files.readString(mmcPack);
        String rewritten = rewriteMmcPack(original, version, replaceFabric);
        if (!rewritten.equals(original)) {
            Files.writeString(mmcPack, rewritten,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        }

        return new Result(componentJson, mmcPack, mavenPath, replaceFabric);
    }

    public record Result(Path componentJson, Path mmcPack, Path jarPath, boolean replacedFabric) {}

    private static String buildComponentJson(String version, Path libsRoot) {
        String url = libsRoot.toUri().toString();
        if (!url.endsWith("/")) url = url + "/";
        return "{\n"
            + "    \"formatVersion\": 1,\n"
            + "    \"name\": \"" + VYNE_NAME + "\",\n"
            + "    \"uid\": \"" + VYNE_UID + "\",\n"
            + "    \"version\": \"" + version + "\",\n"
            + "    \"requires\": [\n"
            + "        { \"uid\": \"" + INTERMEDIARY_UID + "\", \"equals\": \""
                + VyneLauncher.TARGET_MC + "\" }\n"
            + "    ],\n"
            + "    \"libraries\": [\n"
            + "        {\n"
            + "            \"name\": \"com.stolym.vyne:vyne-loader:" + version + "\",\n"
            + "            \"url\": \"" + url + "\"\n"
            + "        }\n"
            + "    ],\n"
            + "    \"mainClass\": \"" + MAIN_CLASS + "\"\n"
            + "}\n";
    }

    static String rewriteMmcPack(String json, String version, boolean replaceFabric) {
        if (json == null || json.isBlank()) return json;
        String result = json;
        result = toggleFabricDisabled(result, replaceFabric);
        if (!result.contains("\"" + VYNE_UID + "\"")) {
            result = insertVyneComponent(result, version);
        } else {
            result = bumpVyneVersion(result, version);
        }
        return result;
    }

    private static String insertVyneComponent(String json, String version) {
        int componentsIdx = json.indexOf("\"components\"");
        if (componentsIdx < 0) return json;
        int arrStart = json.indexOf('[', componentsIdx);
        int arrEnd = findMatchingBracket(json, arrStart, '[', ']');
        if (arrStart < 0 || arrEnd < 0) return json;
        String before = json.substring(0, arrEnd);
        String after = json.substring(arrEnd);
        String trimmedInner = before.substring(arrStart + 1).trim();
        boolean emptyArray = trimmedInner.isEmpty();
        String entry = ""
            + "        {\n"
            + "            \"cachedName\": \"" + VYNE_NAME + "\",\n"
            + "            \"cachedRequires\": [\n"
            + "                { \"equals\": \"" + VyneLauncher.TARGET_MC + "\", \"uid\": \""
                + INTERMEDIARY_UID + "\" }\n"
            + "            ],\n"
            + "            \"cachedVersion\": \"" + version + "\",\n"
            + "            \"uid\": \"" + VYNE_UID + "\",\n"
            + "            \"version\": \"" + version + "\"\n"
            + "        }";
        String sep = emptyArray ? "\n" : ",\n";
        return before + sep + entry + "\n    " + after;
    }

    private static String bumpVyneVersion(String json, String version) {
        int uidIdx = json.indexOf("\"" + VYNE_UID + "\"");
        if (uidIdx < 0) return json;
        int objStart = findObjectStart(json, uidIdx);
        int objEnd = findMatchingBracket(json, objStart, '{', '}');
        if (objStart < 0 || objEnd < 0) return json;
        String block = json.substring(objStart, objEnd + 1);
        String patched = block
            .replaceAll("\"cachedVersion\"\\s*:\\s*\"[^\"]*\"",
                "\"cachedVersion\": \"" + version + "\"")
            .replaceAll("\"version\"\\s*:\\s*\"[^\"]*\"",
                "\"version\": \"" + version + "\"");
        return json.substring(0, objStart) + patched + json.substring(objEnd + 1);
    }

    private static String toggleFabricDisabled(String json, boolean disable) {
        int uidIdx = json.indexOf("\"" + FABRIC_LOADER_UID + "\"");
        if (uidIdx < 0) return json;
        int objStart = findObjectStart(json, uidIdx);
        int objEnd = findMatchingBracket(json, objStart, '{', '}');
        if (objStart < 0 || objEnd < 0) return json;
        String block = json.substring(objStart, objEnd + 1);
        boolean hasDisabled = block.contains("\"disabled\"");
        String updated;
        if (disable) {
            if (hasDisabled) {
                updated = block.replaceAll("\"disabled\"\\s*:\\s*(true|false)",
                    "\"disabled\": true");
            } else {
                int insertAt = block.indexOf('{') + 1;
                updated = block.substring(0, insertAt)
                    + "\n            \"disabled\": true,"
                    + block.substring(insertAt);
            }
        } else {
            if (hasDisabled) {
                updated = block.replaceAll(
                    "(\"disabled\"\\s*:\\s*(true|false)\\s*,?\\s*)", "");

                updated = updated.replaceAll(",\\s*}", "}");
            } else {
                updated = block;
            }
        }
        return json.substring(0, objStart) + updated + json.substring(objEnd + 1);
    }

    private static int findObjectStart(String s, int anywhereInside) {
        int depth = 0;
        for (int i = anywhereInside; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == '}') depth++;
            else if (c == '{') {
                if (depth == 0) return i;
                depth--;
            }
        }
        return -1;
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

    private static Path jarSource() throws IOException {
        try {
            Path self = Path.of(PrismComponentInstaller.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
            if (Files.exists(self) && Files.isRegularFile(self)) return self;
            throw new IOException("Vyne jar not a regular file: " + self);
        } catch (Exception e) {
            throw new IOException("Could not resolve Vyne jar location: " + e.getMessage(), e);
        }
    }

    public static Path guessPrismRoot(Path instanceDir) {
        Path p = instanceDir.getParent();
        while (p != null) {
            if (Files.isDirectory(p.resolve("libraries")) || Files.isDirectory(p.resolve("meta"))) {
                return p;
            }
            p = p.getParent();
        }
        return null;
    }

    public static List<String> summary(Result r) {
        List<String> lines = new ArrayList<>();
        lines.add("Component patch:  " + r.componentJson());
        lines.add("mmc-pack.json  :  " + r.mmcPack());
        lines.add("Library jar    :  " + r.jarPath());
        lines.add("Fabric Loader  :  " + (r.replacedFabric() ? "disabled (Vyne replaces it)"
            : "kept enabled (Vyne runs alongside it)"));
        return lines;
    }
}
