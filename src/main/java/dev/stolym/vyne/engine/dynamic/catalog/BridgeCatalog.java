/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic.catalog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stolym.vyne.util.VyneLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BridgeCatalog {
    private static final String BUNDLED_RESOURCE = "vyne-bridges.json";

    private final Map<String, BridgeRule.Cls> classes = new LinkedHashMap<>();
    private final Map<String, BridgeRule.Method> methods = new LinkedHashMap<>();

    private BridgeCatalog() {}

    public static BridgeCatalog load(Path userOverridePath) {
        BridgeCatalog cat = new BridgeCatalog();
        cat.loadBundledDefaults();
        if (userOverridePath != null && Files.exists(userOverridePath)) {
            try {
                cat.loadFromJson(Files.readString(userOverridePath, StandardCharsets.UTF_8));
                VyneLogger.info("[DynamicEngine] User bridge overrides loaded from "
                    + userOverridePath);
            } catch (IOException e) {
                VyneLogger.warn("[DynamicEngine] Failed to read user bridge overrides at "
                    + userOverridePath + ": " + e.getMessage());
            }
        }
        VyneLogger.info("[DynamicEngine] Catalog loaded: "
            + cat.classes.size() + " class rule(s), "
            + cat.methods.size() + " method rule(s)");
        return cat;
    }

    private void loadBundledDefaults() {
        try (InputStream is = BridgeCatalog.class.getClassLoader()
                .getResourceAsStream(BUNDLED_RESOURCE)) {
            if (is == null) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is,
                    StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            loadFromJson(sb.toString());
        } catch (IOException e) {
            VyneLogger.warn("[DynamicEngine] Bundled catalog read failed: " + e.getMessage());
        }
    }

    private void loadFromJson(String text) {
        JsonElement root = JsonParser.parseString(text);
        if (!root.isJsonObject()) return;
        JsonObject obj = root.getAsJsonObject();
        if (obj.has("classes") && obj.get("classes").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("classes")) {
                if (!el.isJsonObject()) continue;
                BridgeRule.Cls c = BridgeRule.Cls.parse(el.getAsJsonObject());
                if (c != null) classes.put(c.internal, c);
            }
        }
        if (obj.has("methods") && obj.get("methods").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("methods")) {
                if (!el.isJsonObject()) continue;
                BridgeRule.Method m = BridgeRule.Method.parse(el.getAsJsonObject());
                if (m != null) methods.put(m.key(), m);
            }
        }
    }

    public void registerMethodRule(BridgeRule.Method rule) {
        if (rule != null) {
            methods.put(rule.key(), rule);
        }
    }

    public BridgeRule.Cls classRule(String internal) {
        return classes.get(internal);
    }

    public BridgeRule.Method methodRule(String owner, String name, String desc) {
        BridgeRule.Method exact = methods.get(owner + "." + name + desc);
        if (exact != null) return exact;

        return methods.get(owner + "." + name);
    }

    public int classRuleCount() { return classes.size(); }
    public int methodRuleCount() { return methods.size(); }

    public String toDiagnosticJson() {
        Gson gson = new Gson();
        JsonObject out = new JsonObject();
        JsonArray cls = new JsonArray();
        for (BridgeRule.Cls c : classes.values()) cls.add(c.toJson());
        JsonArray mth = new JsonArray();
        for (BridgeRule.Method m : methods.values()) mth.add(m.toJson());
        out.add("classes", cls);
        out.add("methods", mth);
        return gson.toJson(out);
    }

    public List<String> listRuleKeys() {
        List<String> out = new ArrayList<>();
        out.addAll(classes.keySet());
        for (String k : methods.keySet()) out.add("method:" + k);
        return Collections.unmodifiableList(out);
    }
}
