/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.fml.loading;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class FMLConfig {
    public enum ConfigValue {
        EARLY_WINDOW_CONTROL(Boolean.FALSE),
        MAX_THREADS(-1),
        VERSION_CHECK(Boolean.FALSE),
        DEFAULT_CONFIG_PATH("defaultconfigs"),
        DISABLE_OPTIMIZED_DFU(Boolean.TRUE),
        EARLY_WINDOW_PROVIDER("none"),
        EARLY_WINDOW_WIDTH(854),
        EARLY_WINDOW_HEIGHT(480),
        EARLY_WINDOW_FBSCALE(1),
        EARLY_WINDOW_SKIP_GL_VERSIONS(List.of()),
        EARLY_WINDOW_SQUIR(Boolean.FALSE),
        DEPENDENCY_OVERRIDES(List.of());

        private final Object defaultValue;

        ConfigValue(Object defaultValue) {
            this.defaultValue = defaultValue;
        }

        public Object defaultValue() {
            return defaultValue;
        }
    }

    public static String defaultConfigPath() {
        return (String) ConfigValue.DEFAULT_CONFIG_PATH.defaultValue();
    }

    public static String getConfigValue(ConfigValue v) {
        return String.valueOf(v.defaultValue());
    }

    public static boolean getBoolConfigValue(ConfigValue v) {
        return v.defaultValue() instanceof Boolean b && b;
    }

    public static int getIntConfigValue(ConfigValue v) {
        return v.defaultValue() instanceof Integer i ? i : 0;
    }

    @SuppressWarnings("unchecked")
    public static <A> List<A> getListConfigValue(ConfigValue v) {
        return v.defaultValue() instanceof List<?> l ? (List<A>) l : List.of();
    }

    public static Path getConfigPath() {
        return Paths.get("config");
    }

    public static Path getDefaultConfigPath() {
        return Paths.get(defaultConfigPath());
    }
}
