/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import dev.stolym.vyne.util.VyneLogger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FabricDynamicRegistries {
    private static final String API = "net.fabricmc.fabric.api.event.registry.DynamicRegistries";

    private FabricDynamicRegistries() {}

    public static List<Object> merge(List<?> neoForge) {
        List<Object> merged = new ArrayList<>(neoForge);
        try {
            List<?> fabric = (List<?>) Class.forName(API, true, FabricDynamicRegistries.class.getClassLoader())
                    .getMethod("getDynamicRegistries").invoke(null);
            Set<Object> keys = new HashSet<>();
            for (Object entry : neoForge) keys.add(key(entry));
            int added = 0;
            for (Object entry : fabric) {
                if (keys.add(key(entry))) {
                    merged.add(entry);
                    added++;
                }
            }
            if (added > 0) VyneLogger.info("[PatchLayer] Added " + added + " Fabric dynamic registries to NeoForge's datapack registries");
        } catch (ReflectiveOperationException | RuntimeException e) {
            VyneLogger.warn("[PatchLayer] Fabric dynamic registries could not be added: " + e);
        }
        return merged;
    }

    private static Object key(Object registryData) throws ReflectiveOperationException {
        Method key = registryData.getClass().getMethod("comp_985");
        return key.invoke(registryData);
    }
}
