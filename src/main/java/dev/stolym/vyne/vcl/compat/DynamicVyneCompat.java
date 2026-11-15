/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl.compat;

import dev.stolym.vyne.util.VyneLogger;
import dev.stolym.vyne.vcl.VCLFieldPopulator;
import net.neoforged.bus.api.VyneEventBus;
import dev.stolym.vyne.registry.VyneGenericRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.*;

public final class DynamicVyneCompat implements VyneCompat {
    public record RegistryPatch(String typeName, String registryPath, String fieldHint) {}

    private final String modId;
    private final String rootPackage;
    private final String mainClass;
    private final String apiClass;
    private final List<RegistryPatch> registryPatches = new ArrayList<>();
    private final List<String> eventBusFields = new ArrayList<>();
    private final List<String> interfaceCandidates = new ArrayList<>();

    public DynamicVyneCompat(String modId, String rootPackage, String mainClass, String apiClass) {
        this.modId = modId;
        this.rootPackage = rootPackage;
        this.mainClass = mainClass;
        this.apiClass = apiClass;

        this.interfaceCandidates.add("net.neoforged.neoforge.registries.IForgeRegistry");
        this.interfaceCandidates.add("net.minecraftforge.registries.IForgeRegistry");
    }

    public DynamicVyneCompat addInterfaceCandidate(String className) {
        interfaceCandidates.add(0, className);
        return this;
    }

    public DynamicVyneCompat addRegistryPatch(String typeName, String registryPath, String fieldHint) {
        registryPatches.add(new RegistryPatch(typeName, registryPath, fieldHint));
        return this;
    }

    public DynamicVyneCompat addEventBusField(String fieldName) {
        eventBusFields.add(fieldName);
        return this;
    }

    @Override
    public void apply(ClassLoader modCl) {
        if (rootPackage != null) {
            VCLFieldPopulator.registerModPackage(rootPackage, modId);
        }

        if (apiClass != null) {
            for (RegistryPatch patch : registryPatches) {
                patchRegistry(modCl, patch);
            }
        }

        if (mainClass != null) {
            wireEventBus(modCl);
        }
    }

    private void patchRegistry(ClassLoader cl, RegistryPatch patch) {
        try {
            Class<?> api = Class.forName(apiClass, true, cl);
            for (Field f : api.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;

                boolean match = f.getName().equals(patch.fieldHint())
                        || f.getType().getName().contains("IChemicalRegistry") 
                        || f.getType().getName().contains("Registry");

                if (!match) continue;

                f.setAccessible(true);
                Object reg = f.get(null);
                if (reg == null) {
                    reg = buildFallbackRegistry(cl, patch.typeName(), patch.registryPath());
                    f.set(null, reg);
                    VyneLogger.info("[DynamicCompat] Injected fallback registry for: " + patch.registryPath());
                } else {
                    VyneLogger.info("[DynamicCompat] Found live registry for: " + patch.registryPath());
                }
                VyneGenericRegistry.register(patch.registryPath(), patch.fieldHint(), reg);
                return;
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            VyneLogger.warn("[DynamicCompat] patchRegistry failed for "
                + patch.registryPath() + ": " + t.getMessage());
        }
    }

    private Object buildFallbackRegistry(ClassLoader cl, String typeName, String registryPath) {
        return Proxy.newProxyInstance(
            cl,
            resolveInterfaces(cl),
            (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "register"     -> args != null && args.length > 0 ? args[0] : null;
                    case "getValue"     -> null;
                    case "getValues"    -> Collections.emptySet();
                    case "containsKey"  -> false;
                    case "getKeys"      -> Collections.emptySet();
                    case "isEmpty"      -> true;
                    case "size"         -> 0;
                    case "toString"     -> "VyneDynamicFallbackRegistry[" + registryPath + "]";
                    default             -> null;
                };
            });
    }

    private Class<?>[] resolveInterfaces(ClassLoader cl) {
        for (String candidate : interfaceCandidates) {
            try {
                return new Class[]{ Class.forName(candidate, false, cl) };
            } catch (ClassNotFoundException ignored) {}
        }
        return new Class[0];
    }

    private void wireEventBus(ClassLoader cl) {
        try {
            Class<?> main = Class.forName(mainClass, false, cl);
            for (Field f : main.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;

                boolean match = eventBusFields.contains(f.getName()) 
                                || f.getType().getName().contains("IEventBus");

                if (!match) continue;

                f.setAccessible(true);
                if (f.get(null) == null) {
                    f.set(null, new VyneEventBus());
                    VyneLogger.info("[DynamicCompat] Injected event bus into " + mainClass + "." + f.getName());
                }
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            VyneLogger.warn("[DynamicCompat] wireEventBus failed for " + mainClass + ": " + t.getMessage());
        }
    }
}
