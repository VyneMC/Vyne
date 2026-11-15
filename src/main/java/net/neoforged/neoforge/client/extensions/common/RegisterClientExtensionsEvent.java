/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.client.extensions.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class RegisterClientExtensionsEvent {
    private final Map<IClientItemExtensions, List<Object>> itemRegistrations =
            Collections.synchronizedMap(new IdentityHashMap<>());

    public void registerItem(IClientItemExtensions extensions, Object... items) {
        if (extensions == null || items == null || items.length == 0) {
            return;
        }
        itemRegistrations
                .computeIfAbsent(extensions, k -> new ArrayList<>())
                .addAll(java.util.Arrays.asList(items));
    }

    public Map<IClientItemExtensions, List<Object>> snapshotItemRegistrations() {
        synchronized (itemRegistrations) {
            Map<IClientItemExtensions, List<Object>> copy = new IdentityHashMap<>(itemRegistrations.size());
            for (Map.Entry<IClientItemExtensions, List<Object>> e : itemRegistrations.entrySet()) {
                copy.put(e.getKey(), List.copyOf(e.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
    }
}
