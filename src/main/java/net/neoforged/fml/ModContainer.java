/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.fml;

import net.neoforged.neoforgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

public class ModContainer {
    private final String modId;
    private final Object eventBus;
    private final String modVersion;

    public ModContainer(String modId) {
        this(modId, null, null);
    }

    public ModContainer(String modId, Object eventBus) {
        this(modId, eventBus, null);
    }

    public ModContainer(String modId, Object eventBus, String modVersion) {
        this.modId = modId;
        this.eventBus = eventBus;
        this.modVersion = (modVersion != null && !modVersion.isEmpty()) ? modVersion : "0.0.0";
    }

    public String getModId() {
        return modId;
    }

    public IModInfo getModInfo() {
        String v = modVersion;
        if (v == null || v.isEmpty() || "0.0.0".equals(v)) {
            String discovered = ModList.getModVersion(modId);
            if (discovered != null) v = discovered;
        }
        return new net.neoforged.neoforgespi.language.VyneModInfo(modId, v);
    }

    private static final class VyneModInfo implements IModInfo {
        private final String modId;
        private final ArtifactVersion version;

        VyneModInfo(String modId, String rawVersion) {
            this.modId = modId != null ? modId : "unknown";
            this.version = new DefaultArtifactVersion(rawVersion);
        }

        @Override public String getModId() { return modId; }
        @Override public String getDisplayName() { return modId; }
        @Override public ArtifactVersion getVersion() { return version; }
    }

    public net.neoforged.bus.api.IEventBus getEventBus() {
        if (eventBus instanceof net.neoforged.bus.api.IEventBus bus) {
            return bus;
        }
        return net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
    }

    public void registerConfig(net.neoforged.fml.config.ModConfig.Type type,
                               net.neoforged.fml.config.IConfigSpec spec,
                               String fileName) {
        String resolved = fileName != null ? fileName
            : (modId + "-" + (type != null ? type.name().toLowerCase() : "common") + ".toml");
        dev.stolym.vyne.config.VyneConfigBridge.register(modId, type, spec, resolved);
    }

    public void registerConfig(net.neoforged.fml.config.ModConfig.Type type,
                               net.neoforged.fml.config.IConfigSpec spec) {
        registerConfig(type, spec, null);
    }

    private final java.util.Map<Class<?>, java.util.function.Supplier<?>> extensionPoints =
            new java.util.concurrent.ConcurrentHashMap<>();

    @SuppressWarnings("rawtypes")
    public <T extends IExtensionPoint> void registerExtensionPoint(Class<T> point, T extension) {
        if (point == null || extension == null) return;
        extensionPoints.put(point, () -> extension);
    }

    @SuppressWarnings("rawtypes")
    public <T extends IExtensionPoint> void registerExtensionPoint(Class<T> point,
                                                                    java.util.function.Supplier<T> extension) {
        if (point == null || extension == null) return;
        extensionPoints.put(point, extension);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public <T extends IExtensionPoint> java.util.Optional<T> getCustomExtension(Class<T> point) {
        java.util.function.Supplier<?> supplier = extensionPoints.get(point);
        if (supplier == null) return java.util.Optional.empty();
        return java.util.Optional.ofNullable((T) supplier.get());
    }

    public String getNamespace() {
        return modId;
    }
}
