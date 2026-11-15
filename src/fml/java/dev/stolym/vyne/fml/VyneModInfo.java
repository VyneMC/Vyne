/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.fml;

import net.neoforged.neoforgespi.language.IConfigurable;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.IModLanguageLoader;
import net.neoforged.neoforgespi.locating.ForgeFeature;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VyneModInfo implements IModInfo, IConfigurable {
    private final VyneModFileInfo owningFile;
    private final VyneConfigurable config;
    private final String modId;
    private final String displayName;
    private final String description;
    private final ArtifactVersion version;
    private final String namespace;

    public VyneModInfo(VyneModFileInfo owningFile, VyneConfigurable config, String modId, String version) {
        this.owningFile = owningFile;
        this.config = config;
        this.modId = modId;
        this.namespace = config.<String>getConfigElement("namespace").orElse(modId);
        this.displayName = config.<String>getConfigElement("displayName").orElse(modId);
        this.description = config.<String>getConfigElement("description").orElse("").trim();
        String raw = version;
        if (raw == null || raw.isBlank() || raw.contains("${")) {
            raw = config.<String>getConfigElement("version").filter(v -> !v.contains("${")).orElse("0.0.0");
        }
        this.version = new DefaultArtifactVersion(raw);
    }

    @Override
    public VyneModFileInfo getOwningFile() {
        return owningFile;
    }

    @Override
    public IModLanguageLoader getLoader() {
        return null;
    }

    @Override
    public String getModId() {
        return modId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public ArtifactVersion getVersion() {
        return version;
    }

    @Override
    public List<? extends ModVersion> getDependencies() {
        return List.of();
    }

    @Override
    public List<? extends ForgeFeature.Bound> getForgeFeatures() {
        return List.of();
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public Map<String, Object> getModProperties() {
        return Map.of();
    }

    @Override
    public Optional<URL> getUpdateURL() {
        return url("updateJSONURL");
    }

    @Override
    public Optional<URL> getModURL() {
        return url("displayURL");
    }

    @Override
    public Optional<String> getLogoFile() {
        Optional<String> own = config.getConfigElement("logoFile");
        return own.isPresent() ? own : owningFile.getConfigElement("logoFile");
    }

    @Override
    public boolean getLogoBlur() {
        return config.<Boolean>getConfigElement("logoBlur").orElse(true);
    }

    @Override
    public IConfigurable getConfig() {
        return this;
    }

    @Override
    public <T> Optional<T> getConfigElement(String... key) {
        return config.getConfigElement(key);
    }

    @Override
    public List<? extends IConfigurable> getConfigList(String... key) {
        return config.getConfigList(key);
    }

    private Optional<URL> url(String key) {
        try {
            Optional<String> value = config.getConfigElement(key);
            if (value.isEmpty() || value.get().isBlank()) return Optional.empty();
            return Optional.of(URI.create(value.get()).toURL());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return "VyneModInfo[" + modId + " " + version + "]";
    }
}
