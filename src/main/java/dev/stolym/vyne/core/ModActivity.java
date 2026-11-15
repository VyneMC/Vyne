/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.core;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModActivity {
    public static final Set<String> FAILED = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public static final Set<String> INACTIVE = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private ModActivity() {}

    public static boolean isInactive(String modId) {
        return modId != null && (INACTIVE.contains(modId) || FAILED.contains(modId));
    }
}
