/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic.deps;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.util.VyneLogger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class DependencyAnalyzer {
    private static final Set<String> ENVIRONMENT_IDS = Set.of(
        "minecraft", "java", "forge", "neoforge", "fabric", "fabricloader",
        "fabric-api", "vyne"
    );

    public enum Severity { INFO, WARNING, ERROR }

    public static final class Issue {
        public final String owningMod;
        public final String declaredDep;
        public final String versionConstraint;
        public final boolean required;
        public final Severity severity;
        public final String suggestion; 
        public final String message;

        public Issue(String owningMod, String declaredDep, String versionConstraint,
                     boolean required, Severity severity, String suggestion, String message) {
            this.owningMod = owningMod;
            this.declaredDep = declaredDep;
            this.versionConstraint = versionConstraint;
            this.required = required;
            this.severity = severity;
            this.suggestion = suggestion;
            this.message = message;
        }
    }

    public static final class Report {
        public final List<Issue> issues = new ArrayList<>();
        public final Set<String> modsToDisable = new LinkedHashSet<>();

        public boolean hasErrors() {
            for (Issue i : issues) if (i.severity == Severity.ERROR) return true;
            return false;
        }
    }

    private DependencyAnalyzer() {}

    public static Report analyze(List<DetectedMod> mods) {
        Report report = new Report();
        Set<String> availableIds = new LinkedHashSet<>();
        Map<String, String> idToVersion = new LinkedHashMap<>();
        for (DetectedMod m : mods) {
            String id = m.getModId().toLowerCase(Locale.ROOT);
            availableIds.add(id);
            idToVersion.put(id, m.getVersion());
        }

        for (DetectedMod m : mods) {
            if (m.getJarPath() == null) continue;
            for (String jijId : readJarInJarProvidedIds(m.getJarPath().toFile().toPath())) {
                availableIds.add(jijId.toLowerCase(Locale.ROOT));
            }
        }

        for (DetectedMod m : mods) {
            Map<String, DeclaredDep> declared = declaredDepsFor(m);
            if (declared.isEmpty()) continue;

            for (DeclaredDep dep : declared.values()) {
                String depId = dep.modId.toLowerCase(Locale.ROOT);
                if (ENVIRONMENT_IDS.contains(depId)) continue;

                String installedVersion = idToVersion.get(depId);
                if (installedVersion == null && availableIds.contains(depId)) {
                    continue;
                }
                if (installedVersion != null) {
                    if (!dev.stolym.vyne.util.VersionConstraintMatcher.matches(installedVersion, dep.versionConstraint)) {
                        if (dep.required) {
                            Issue issue = new Issue(m.getModId(), dep.modId, dep.versionConstraint,
                                true, Severity.ERROR, null,
                                "[Deps] '" + m.getModId() + "' requires '" + dep.modId
                                    + "' (version " + dep.versionConstraint + ") but installed version is "
                                    + installedVersion + ". Disabling this mod until a compatible version is installed.");
                            report.issues.add(issue);
                            report.modsToDisable.add(m.getModId());
                        } else {
                            Issue issue = new Issue(m.getModId(), dep.modId, dep.versionConstraint,
                                false, Severity.INFO, null,
                                "[Deps] '" + m.getModId() + "' optionally depends on '" + dep.modId
                                    + "' (version " + dep.versionConstraint + "); installed version "
                                    + installedVersion + " does not satisfy it. Continuing without it.");
                            report.issues.add(issue);
                        }
                    }
                    continue;
                }

                String suggestion = dep.required ? fuzzyMatch(depId, availableIds) : null;
                if (dep.required && suggestion != null) {
                    Issue issue = new Issue(m.getModId(), dep.modId, dep.versionConstraint,
                        dep.required, Severity.WARNING, suggestion,
                        "[Deps] '" + m.getModId() + "' declares dependency on '" + dep.modId
                            + "' but no such mod is installed. Did you mean '" + suggestion + "'?");
                    report.issues.add(issue);
                } else if (dep.required) {
                    Issue issue = new Issue(m.getModId(), dep.modId, dep.versionConstraint,
                        dep.required, Severity.ERROR, null,
                        "[Deps] '" + m.getModId() + "' requires '" + dep.modId
                            + "' (version " + dep.versionConstraint + ") which is not installed. "
                            + "Disabling this mod until the dependency is provided.");
                    report.issues.add(issue);
                    report.modsToDisable.add(m.getModId());
                } else {
                    Issue issue = new Issue(m.getModId(), dep.modId, dep.versionConstraint,
                        dep.required, Severity.INFO, null,
                        "[Deps] '" + m.getModId() + "' optionally depends on '" + dep.modId
                            + "' which is not installed. Continuing without it.");
                    report.issues.add(issue);
                }
            }
        }
        return report;
    }

    public static void logReport(Report report) {
        boolean verbose = Boolean.getBoolean("vyne.verbose");
        for (Issue i : report.issues) {
            switch (i.severity) {
                case INFO -> {
                    if (verbose) VyneLogger.info(i.message);
                }
                case WARNING -> VyneLogger.warn(i.message);
                case ERROR -> VyneLogger.error(i.message);
            }
        }
        if (!report.modsToDisable.isEmpty()) {
            VyneLogger.warn("[Deps] " + report.modsToDisable.size()
                + " mod(s) disabled due to missing required dependencies: "
                + String.join(", ", report.modsToDisable));
        }
    }

    static String fuzzyMatch(String wanted, Set<String> available) {
        String best = null;
        int bestScore = Integer.MAX_VALUE;
        int len = wanted.length();
        int threshold = Math.max(2, len / 3); 

        for (String a : available) {
            int score = editDistance(wanted, a);

            if (score > threshold && (a.contains(wanted) || wanted.contains(a))) {
                score = Math.min(score, threshold);
            }
            if (score < bestScore) {
                bestScore = score;
                best = a;
            }
        }
        return bestScore <= threshold ? best : null;
    }

    static int editDistance(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[m];
    }

    private static final class DeclaredDep {
        final String modId;
        final String versionConstraint;
        final boolean required;
        DeclaredDep(String id, String v, boolean req) {
            this.modId = id; this.versionConstraint = v; this.required = req;
        }
    }

    private static Map<String, DeclaredDep> declaredDepsFor(DetectedMod mod) {
        Map<String, DeclaredDep> out = new LinkedHashMap<>();

        for (Map.Entry<String, String> e : mod.getDependencies().entrySet()) {
            out.put(e.getKey().toLowerCase(Locale.ROOT),
                new DeclaredDep(e.getKey(), e.getValue(), true));
        }

        for (Map.Entry<String, String> e : mod.getOptionalDependencies().entrySet()) {
            String key = e.getKey().toLowerCase(Locale.ROOT);
            if (out.containsKey(key)) continue;
            out.put(key, new DeclaredDep(e.getKey(), e.getValue(), false));
        }

        if (mod.getJarPath() == null) return out;
        try (ZipFile zf = new ZipFile(mod.getJarPath().toFile())) {
            parseTomlIfPresent(zf, "META-INF/mods.toml", out);
            parseTomlIfPresent(zf, "META-INF/neoforge.mods.toml", out);
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void parseTomlIfPresent(ZipFile zf, String path,
                                           Map<String, DeclaredDep> out) {
        ZipEntry entry = zf.getEntry(path);
        if (entry == null) return;
        try (InputStream is = zf.getInputStream(entry)) {
            String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            parseToml(text, out);
        } catch (Exception ignored) {
        }
    }

    private static final Pattern DEP_TABLE_START =
        Pattern.compile("^\\s*\\[\\[dependencies\\.[^\\]]+\\]\\]\\s*$");
    private static final Pattern KV = Pattern.compile(
        "^\\s*(modId|mandatory|versionRange|type)\\s*=\\s*(.*?)\\s*$");

    private enum DepKind { REQUIRED, OPTIONAL, DROPPED }

    static void parseToml(String text, Map<String, DeclaredDep> out) {
        String[] lines = text.split("\\R");
        String curModId = null;
        String curType = null;    
        Boolean curMandatory = null; 
        String curVersion = "any";

        for (String rawLine : lines) {
            String line = rawLine;
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash);
            line = line.trim();
            if (line.isEmpty()) continue;

            if (DEP_TABLE_START.matcher(line).matches()) {
                if (curModId != null) flushDep(out, curModId, curVersion, curType, curMandatory);
                curModId = null;
                curType = null;
                curMandatory = null;
                curVersion = "any";
                continue;
            }

            if (line.startsWith("[") && !line.startsWith("[[")) {
                if (curModId != null) {
                    flushDep(out, curModId, curVersion, curType, curMandatory);
                    curModId = null;
                    curType = null;
                    curMandatory = null;
                }
                continue;
            }

            Matcher kv = KV.matcher(line);
            if (!kv.matches()) continue;
            String key = kv.group(1);
            String value = kv.group(2);

            if (value.startsWith("\"") || value.startsWith("'")) {
                int endQuote = value.indexOf(value.charAt(0), 1);
                if (endQuote > 0) value = value.substring(1, endQuote);
            }
            switch (key) {
                case "modId" -> curModId = value;
                case "mandatory" -> {
                    String v = value.toLowerCase(Locale.ROOT).trim();
                    curMandatory = v.equals("true");
                }
                case "versionRange" -> curVersion = value;
                case "type" -> curType = value;
            }
        }
        if (curModId != null) flushDep(out, curModId, curVersion, curType, curMandatory);
    }

    private static void flushDep(Map<String, DeclaredDep> out,
                                 String modId, String version,
                                 String type, Boolean mandatory) {
        DepKind kind = classify(type, mandatory);
        if (kind == DepKind.DROPPED) return;
        out.putIfAbsent(modId.toLowerCase(Locale.ROOT),
            new DeclaredDep(modId, version, kind == DepKind.REQUIRED));
    }

    private static DepKind classify(String type, Boolean mandatory) {
        if (type != null) {
            String t = type.toLowerCase(Locale.ROOT).trim();
            return switch (t) {
                case "required" -> DepKind.REQUIRED;
                case "optional" -> DepKind.OPTIONAL;

                case "incompatible", "discouraged" -> DepKind.DROPPED;
                default -> DepKind.REQUIRED;
            };
        }
        if (mandatory != null) return mandatory ? DepKind.REQUIRED : DepKind.OPTIONAL;
        return DepKind.REQUIRED;
    }

    static Set<String> readJarInJarProvidedIds(Path host) {
        Set<String> out = new LinkedHashSet<>();
        if (host == null) return out;
        try (ZipFile zf = new ZipFile(host.toFile())) {
            java.util.Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                boolean isJarJar = (name.startsWith("META-INF/jarjar/")
                        || name.startsWith("META-INF/jars/"))
                    && name.endsWith(".jar") && !entry.isDirectory();
                if (!isJarJar) continue;
                try (InputStream is = zf.getInputStream(entry)) {
                    byte[] embed = is.readAllBytes();
                    String embedId = extractModIdFromEmbedJar(embed);
                    if (embedId != null && !embedId.isEmpty()) {
                        out.add(embedId);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static String extractModIdFromEmbedJar(byte[] jarBytes) {
        if (jarBytes == null || jarBytes.length == 0) return null;

        java.nio.file.Path tmp = null;
        try {
            tmp = java.nio.file.Files.createTempFile("vyne-jij-", ".jar");
            java.nio.file.Files.write(tmp, jarBytes);
            try (ZipFile zf = new ZipFile(tmp.toFile())) {
                ZipEntry fmj = zf.getEntry("fabric.mod.json");
                if (fmj != null) {
                    try (InputStream is = zf.getInputStream(fmj)) {
                        String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        String id = parseJsonStringField(json, "id");
                        if (id != null) return id;
                    }
                }
                ZipEntry neoToml = zf.getEntry("META-INF/neoforge.mods.toml");
                if (neoToml != null) {
                    try (InputStream is = zf.getInputStream(neoToml)) {
                        String toml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        String id = parseTomlTopLevelModId(toml);
                        if (id != null) return id;
                    }
                }
                ZipEntry forgeToml = zf.getEntry("META-INF/mods.toml");
                if (forgeToml != null) {
                    try (InputStream is = zf.getInputStream(forgeToml)) {
                        String toml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        String id = parseTomlTopLevelModId(toml);
                        if (id != null) return id;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (tmp != null) {
                try { java.nio.file.Files.deleteIfExists(tmp); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static final Pattern JSON_STRING_FIELD_SHAPE =
        Pattern.compile("\"([a-zA-Z_][\\w-]*)\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");

    static String parseJsonStringField(String json, String field) {
        if (json == null || field == null) return null;
        Matcher m = JSON_STRING_FIELD_SHAPE.matcher(json);
        while (m.find()) {
            if (field.equals(m.group(1))) {
                return m.group(2).replace("\\\"", "\"").replace("\\\\", "\\");
            }
        }
        return null;
    }

    private static final Pattern MODS_TABLE_START =
        Pattern.compile("^\\s*\\[\\[mods\\]\\]\\s*$");
    private static final Pattern MODID_KV =
        Pattern.compile("^\\s*modId\\s*=\\s*\"([^\"]+)\"\\s*$");

    static String parseTomlTopLevelModId(String text) {
        if (text == null) return null;
        boolean inModsBlock = false;
        for (String rawLine : text.split("\\R")) {
            String line = rawLine;
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash);
            line = line.trim();
            if (line.isEmpty()) continue;
            if (MODS_TABLE_START.matcher(line).matches()) {
                inModsBlock = true;
                continue;
            }
            if (line.startsWith("[") && !MODS_TABLE_START.matcher(line).matches()) {
                inModsBlock = false;
                continue;
            }
            if (!inModsBlock) continue;
            Matcher kv = MODID_KV.matcher(line);
            if (kv.matches()) return kv.group(1);
        }
        return null;
    }

    public static Map<String, String> parseTomlForTests(String text) {
        Map<String, DeclaredDep> parsed = new LinkedHashMap<>();
        parseToml(text, parsed);
        Map<String, String> simple = new LinkedHashMap<>();
        for (DeclaredDep dd : parsed.values()) simple.put(dd.modId, dd.versionConstraint);
        return Collections.unmodifiableMap(simple);
    }
}
