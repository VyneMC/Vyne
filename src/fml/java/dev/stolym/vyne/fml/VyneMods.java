/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.fml;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import dev.stolym.vyne.convert.ModConverter;
import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.core.VyneLauncher;
import dev.stolym.vyne.core.VyneMode;
import dev.stolym.vyne.util.VyneLogger;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.modscan.ModAnnotation;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class VyneMods {
    private static final Type MOD_ANNOTATION = Type.getType("Lnet/neoforged/fml/common/Mod;");

    private static final List<VyneModFileInfo> FILES = new ArrayList<>();
    private static final List<VyneModInfo> MODS = new ArrayList<>();
    private static final List<VyneModContainer> CONTAINERS = new ArrayList<>();
    private static final Map<String, VyneModInfo> BY_ID = new LinkedHashMap<>();
    private static final Map<String, VyneModContainer> CONTAINERS_BY_ID = new LinkedHashMap<>();
    private static volatile boolean discovered;

    private VyneMods() {}

    public static synchronized void discover() {
        if (discovered) return;
        discovered = true;
        net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(VyneFMLLoader.getGamePath());
        try {
            net.neoforged.fml.loading.FMLConfig.load();
        } catch (Throwable t) {
            VyneLogger.warn("[VyneFML] Could not load fml.toml: " + t);
        }
        addMinecraft();
        Path neoforge = VyneMode.neoForgeApiJar();
        if (neoforge != null) addJar(neoforge, VyneLauncher.getNeoForgeApiJar(), null);
        List<DetectedMod> detected = VyneLauncher.getDetectedMods();
        if (detected != null) {
            for (DetectedMod mod : detected) {
                if (mod.getLoaderType() != DetectedMod.LoaderType.NEOFORGE || mod.getJarPath() == null) continue;
                if (dev.stolym.vyne.core.ModActivity.isInactive(mod.getModId())) continue;
                Path runtime = ModConverter.runtimeJar(mod.getJarPath());
                addJar(runtime, runtime, mod.getVersion());
            }
        }
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StringBuilder caller = new StringBuilder();
        for (int i = 2; i < Math.min(stack.length, 9); i++) caller.append(" < ").append(stack[i].getClassName()).append('.').append(stack[i].getMethodName());
        VyneLogger.info("[VyneFML] " + MODS.size() + " NeoForge mod(s) known: " + BY_ID.keySet() + " (discovered from" + caller + ")");
    }

    static List<VyneModFileInfo> files() {
        discover();
        return FILES;
    }

    static List<VyneModInfo> mods() {
        discover();
        return MODS;
    }

    static List<VyneModContainer> containers() {
        discover();
        return CONTAINERS;
    }

    static VyneModInfo mod(String modId) {
        discover();
        return BY_ID.get(modId);
    }

    static VyneModContainer container(String modId) {
        discover();
        return CONTAINERS_BY_ID.get(modId);
    }

    static String neoForgeVersion() {
        VyneModInfo neo = BY_ID.get("neoforge");
        return neo != null ? neo.getVersion().toString() : "unknown";
    }

    private static void addMinecraft() {
        Path gameJar = null;
        try {
            gameJar = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft")
                    .flatMap(c -> c.getOrigin().getPaths().stream().findFirst()).orElse(null);
        } catch (Throwable ignored) {
        }
        Config fileConfig = Config.inMemory();
        fileConfig.set("license", "All Rights Reserved");
        Config modConfig = Config.inMemory();
        modConfig.set("modId", "minecraft");
        modConfig.set("displayName", "Minecraft");
        modConfig.set("version", VyneLauncher.TARGET_MC);
        VyneModFile file = new VyneModFile(gameJar != null ? gameJar : Path.of("minecraft.jar"), new ModFileScanData());
        register(file, fileConfig, List.of(modConfig), null, Map.of());
    }

    private static void addJar(Path jar, Path metadataJar, String jarVersion) {
        try {
            UnmodifiableConfig toml = readToml(metadataJar != null ? metadataJar : jar);
            if (toml == null) {
                VyneLogger.warn("[VyneFML] " + jar.getFileName() + " has no mods.toml; skipped");
                return;
            }
            Object modsValue = toml.get("mods");
            List<UnmodifiableConfig> modConfigs = new ArrayList<>();
            if (modsValue instanceof List<?> list) {
                for (Object entry : list) if (entry instanceof UnmodifiableConfig c) modConfigs.add(c);
            }
            ModFileScanData scan = ModScan.scan(jar);
            register(new VyneModFile(jar, scan), toml, modConfigs, jarVersion, modClasses(scan));
        } catch (IOException e) {
            VyneLogger.error("[VyneFML] Could not read " + jar.getFileName() + ": " + e.getMessage(), e);
        }
    }

    private static void register(VyneModFile file, UnmodifiableConfig fileConfig, List<? extends UnmodifiableConfig> modConfigs,
                                 String jarVersion, Map<String, List<String>> classes) {
        VyneModFileInfo info = new VyneModFileInfo(file, fileConfig);
        file.setInfo(info);
        for (UnmodifiableConfig modConfig : modConfigs) {
            Object id = modConfig.get("modId");
            if (!(id instanceof String modId) || BY_ID.containsKey(modId)) continue;
            String version = modConfig.get("version") instanceof String v && !v.contains("${") ? v : jarVersion;
            VyneModInfo mod = new VyneModInfo(info, new VyneConfigurable(modConfig), modId, version);
            info.addMod(mod);
            MODS.add(mod);
            BY_ID.put(modId, mod);
            VyneModContainer container = new VyneModContainer(mod, classes.getOrDefault(modId, List.of()));
            CONTAINERS.add(container);
            CONTAINERS_BY_ID.put(modId, container);
            dev.stolym.vyne.core.ModBusRegistry.register(modId, container.getEventBus());
        }
        FILES.add(info);
    }

    private static Map<String, List<String>> modClasses(ModFileScanData scan) {
        Dist dist = VyneFMLLoader.getDist();
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (ModFileScanData.AnnotationData data : scan.getAnnotations()) {
            if (!MOD_ANNOTATION.equals(data.annotationType())) continue;
            Object value = data.annotationData().get("value");
            if (!(value instanceof String modId)) continue;
            if (!matchesDist(data.annotationData().get("dist"), dist)) continue;
            out.computeIfAbsent(modId, k -> new ArrayList<>()).add(data.clazz().getClassName());
        }
        return out;
    }

    static boolean matchesDist(Object value, Dist dist) {
        if (!(value instanceof List<?> list) || list.isEmpty()) return true;
        for (Object entry : list) {
            if (entry instanceof ModAnnotation.EnumHolder holder && holder.value().equals(dist.name())) return true;
        }
        return false;
    }

    private static UnmodifiableConfig readToml(Path jar) throws IOException {
        if (!Files.isRegularFile(jar)) return null;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("META-INF/neoforge.mods.toml");
            if (entry == null) entry = zip.getEntry("META-INF/mods.toml");
            if (entry == null) return null;
            try (InputStream in = zip.getInputStream(entry)) {
                return new TomlParser().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }
}
