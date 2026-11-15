/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the Licenses/LICENSE_VYNE file in the project root.
 */

package dev.stolym.vyne.resources;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.core.VyneLauncher;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class VyneModPackInjector {
    private static final Logger LOG = LogManager.getLogger("VynePacks");

    private static final Set<String> REGISTERED_IDS = new HashSet<>();

    private VyneModPackInjector() {}

    public static void install() {
        List<DetectedMod> mods = VyneLauncher.getDetectedMods();
        if (mods == null || mods.isEmpty()) {
            LOG.warn("[VynePacks] No detected mods to register.");
            return;
        }

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Class<?> helperCls;
        Class<?> resourceLocationCls;
        Class<?> modContainerCls;
        Class<?> activationCls;
        Method registerMethod;
        Object defaultEnabled;
        Method fromNamespaceAndPath;
        try {
            helperCls = Class.forName("net.fabricmc.fabric.impl.resource.loader.ResourceManagerHelperImpl", true, cl);
            resourceLocationCls = Class.forName("net.minecraft.class_2960", true, cl);
            modContainerCls = Class.forName("net.fabricmc.loader.api.ModContainer", true, cl);
            activationCls = Class.forName("net.fabricmc.fabric.api.resource.ResourcePackActivationType", true, cl);
            defaultEnabled = Enum.valueOf(activationCls.asSubclass(Enum.class), "DEFAULT_ENABLED");
            registerMethod = helperCls.getMethod(
                "registerBuiltinResourcePack",
                resourceLocationCls, String.class, modContainerCls, activationCls
            );
            fromNamespaceAndPath = findResourceLocationFactory(resourceLocationCls);
        } catch (Throwable t) {
            LOG.error("[VynePacks] Prerequisite classes unavailable (Fabric API missing?): {}", t.toString(), t);
            return;
        }

        int registered = 0;
        int skipped = 0;
        int failed = 0;
        for (DetectedMod mod : mods) {
            if (mod == null) continue;
            String modId = mod.getModId();
            if (modId == null || modId.isEmpty()) continue;

            if (mod.getLoaderType() == DetectedMod.LoaderType.FABRIC) {
                skipped++;
                continue;
            }
            Path jar = mod.getJarPath();
            if (jar == null || !Files.exists(jar) || Files.isDirectory(jar)) {
                skipped++;
                continue;
            }
            if (!REGISTERED_IDS.add(modId)) {
                skipped++;
                continue;
            }

            try {
                VyneModContainer container = new VyneModContainer(
                    jar, modId, mod.getName(), mod.getVersion()
                );

                String ns = sanitizeNamespace(modId);
                Object packId = fromNamespaceAndPath.invoke(null, ns, ns);

                String subPath = "";
                Object result = registerMethod.invoke(null, packId, subPath, container, defaultEnabled);
                if (Boolean.TRUE.equals(result)) {
                    registered++;
                    LOG.info("[VynePacks] Registered {} ({}) as builtin resource+data pack.",
                        modId, jar.getFileName());
                } else {
                    skipped++;
                    LOG.warn("[VynePacks] {} — registerBuiltinResourcePack returned false (check jar roots / namespaces).",
                        modId);
                }
            } catch (Throwable t) {
                failed++;
                Throwable root = t.getCause() != null ? t.getCause() : t;
                LOG.error("[VynePacks] Failed to register {}: {}", modId, root.toString(), t);
            }
        }
        LOG.info("[VynePacks] Summary: registered={} skipped={} failed={} of {} detected mod(s).",
            registered, skipped, failed, mods.size());
    }

    private static Method findResourceLocationFactory(Class<?> rl) throws NoSuchMethodException {
        String[] candidates = {"method_60655", "method_12829", "method_12836", "fromNamespaceAndPath", "of"};
        for (String name : candidates) {
            try {
                Method m = rl.getMethod(name, String.class, String.class);
                if (rl.isAssignableFrom(m.getReturnType())) return m;
            } catch (NoSuchMethodException ignored) { }
        }

        for (Method m : rl.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
            if (!rl.isAssignableFrom(m.getReturnType())) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 2 && p[0] == String.class && p[1] == String.class) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new NoSuchMethodException("No (String, String) factory on " + rl.getName());
    }

    private static String sanitizeNamespace(String modId) {
        StringBuilder sb = new StringBuilder(modId.length());
        for (int i = 0; i < modId.length(); i++) {
            char c = modId.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '_' || c == '-' || c == '.') {
                sb.append(c);
            } else if (c >= 'A' && c <= 'Z') {
                sb.append((char) (c - 'A' + 'a'));
            } else {
                sb.append('_');
            }
        }
        return sb.length() == 0 ? "vyne" : sb.toString();
    }
}
