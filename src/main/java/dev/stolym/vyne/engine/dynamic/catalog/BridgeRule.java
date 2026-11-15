/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BridgeRule {
    public enum BodyKind {
        AUTO_DEFAULT,
        NEW_INSTANCE_SELF,
        RETURN_FIELD,
        DELEGATE_STATIC,
        RETURN_ARG,
        EMPTY_OPTIONAL,
        EMPTY_LIST,
        EMPTY_SET,
        EMPTY_MAP;

        public static BodyKind parse(String s) {
            if (s == null) return AUTO_DEFAULT;
            try {
                return BodyKind.valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return AUTO_DEFAULT;
            }
        }
    }

    public static final class Cls {
        public final String internal;
        public String superclass = "java/lang/Object";
        public List<String> interfaces = new ArrayList<>();

        public Map<String, String> singletons = new LinkedHashMap<>();

        public boolean markerOnly;

        public Cls(String internal) { this.internal = internal; }

        static Cls parse(JsonObject o) {
            if (!o.has("internal")) return null;
            Cls c = new Cls(o.get("internal").getAsString());
            if (o.has("superclass") && o.get("superclass").isJsonPrimitive()) {
                c.superclass = o.get("superclass").getAsString();
            }
            if (o.has("interfaces") && o.get("interfaces").isJsonArray()) {
                for (JsonElement el : o.getAsJsonArray("interfaces")) {
                    c.interfaces.add(el.getAsString());
                }
            }
            if (o.has("singletons") && o.get("singletons").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e
                        : o.getAsJsonObject("singletons").entrySet()) {
                    c.singletons.put(e.getKey(), e.getValue().getAsString());
                }
            }
            if (o.has("markerOnly")) c.markerOnly = o.get("markerOnly").getAsBoolean();
            return c;
        }

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("internal", internal);
            o.addProperty("superclass", superclass);
            JsonArray i = new JsonArray();
            for (String s : interfaces) i.add(s);
            o.add("interfaces", i);
            JsonObject s = new JsonObject();
            singletons.forEach(s::addProperty);
            o.add("singletons", s);
            if (markerOnly) o.addProperty("markerOnly", true);
            return o;
        }
    }

    public static final class Method {
        public final String owner;
        public final String name;
        public final String desc;
        public BodyKind body = BodyKind.AUTO_DEFAULT;

        public String field;

        public String target;

        public String targetDesc;

        public int argIndex = -1;

        public Method(String owner, String name, String desc) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }

        public String key() { return owner + "." + name + desc; }

        static Method parse(JsonObject o) {
            if (!o.has("owner") || !o.has("name") || !o.has("desc")) return null;
            Method m = new Method(
                o.get("owner").getAsString(),
                o.get("name").getAsString(),
                o.get("desc").getAsString());
            if (o.has("body")) m.body = BodyKind.parse(o.get("body").getAsString());
            if (o.has("field") && o.get("field").isJsonPrimitive()) {
                m.field = o.get("field").getAsString();
            }
            if (o.has("target") && o.get("target").isJsonPrimitive()) {
                m.target = o.get("target").getAsString();
            }
            if (o.has("targetDesc") && o.get("targetDesc").isJsonPrimitive()) {
                m.targetDesc = o.get("targetDesc").getAsString();
            }
            if (o.has("argIndex") && o.get("argIndex").isJsonPrimitive()) {
                m.argIndex = o.get("argIndex").getAsInt();
            }
            return m;
        }

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("owner", owner);
            o.addProperty("name", name);
            o.addProperty("desc", desc);
            o.addProperty("body", body.name());
            if (field != null) o.addProperty("field", field);
            if (target != null) o.addProperty("target", target);
            if (argIndex >= 0) o.addProperty("argIndex", argIndex);
            return o;
        }
    }
}
