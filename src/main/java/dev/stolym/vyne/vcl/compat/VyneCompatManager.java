/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl.compat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.stolym.vyne.util.VyneLogger;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class VyneCompatManager {
    private static final Map<String, VyneCompat> COMPATS = new ConcurrentHashMap<>();
    private static final Gson GSON = new Gson();

    public static void register(String modId, VyneCompat compat) {
        COMPATS.put(modId, compat);
    }

    public static void apply(String modId, ClassLoader modCl) {
        VyneCompat compat = COMPATS.get(modId);
        if (compat != null) {
            try {
                VyneLogger.info("[CompatManager] Applying compat for: " + modId);
                compat.apply(modCl);
            } catch (Throwable t) {
                VyneLogger.warn("[CompatManager] Failed to apply compat for " + modId + ": " + t.getMessage());
            }
        }
    }

    public static void loadExternalCompats() {
        try {
            Path configDir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
            Path compatFile = configDir.resolve("vyne_compats.json");

            if (Files.exists(compatFile)) {
                try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(compatFile))) {
                    List<CompatRule> rules = GSON.fromJson(reader, new TypeToken<List<CompatRule>>(){}.getType());
                    for (CompatRule rule : rules) {
                        DynamicVyneCompat dynamic = new DynamicVyneCompat(
                            rule.modId, rule.rootPackage, rule.mainClass, rule.apiClass);
                        if (rule.registryPatches != null) {
                            for (DynamicVyneCompat.RegistryPatch patch : rule.registryPatches) {
                                dynamic.addRegistryPatch(patch.typeName(), patch.registryPath(), patch.fieldHint());
                            }
                        }
                        if (rule.eventBusFields != null) {
                            for (String field : rule.eventBusFields) {
                                dynamic.addEventBusField(field);
                            }
                        }
                        if (rule.interfaceCandidates != null) {
                            for (String candidate : rule.interfaceCandidates) {
                                dynamic.addInterfaceCandidate(candidate);
                            }
                        }
                        register(rule.modId, dynamic);
                        VyneLogger.info("[CompatManager] Loaded external compat for: " + rule.modId);
                    }
                }
            }
        } catch (Throwable t) {
            VyneLogger.warn("[CompatManager] Could not load external compats: " + t.getMessage());
        }
    }

    private static class CompatRule {
        String modId;
        String rootPackage;
        String mainClass;
        String apiClass;
        List<DynamicVyneCompat.RegistryPatch> registryPatches;
        List<String> eventBusFields;
        List<String> interfaceCandidates;
    }

    static {
        loadExternalCompats();
    }
}
