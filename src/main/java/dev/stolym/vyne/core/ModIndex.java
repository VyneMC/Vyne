/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModIndex {
    private static volatile Map<String, String> versions = Map.of();

    private ModIndex() {}

    public static void seed(Map<String, String> idToVersion) {
        versions = idToVersion == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(idToVersion));
    }

    public static Map<String, String> versions() {
        return versions;
    }
}
