/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.detector;

import dev.stolym.vyne.core.DetectedMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.Person;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.NestedJarEntry;

import java.util.*;

public final class VyneVirtualMetadata implements LoaderModMetadata {
    private final String id;
    private Version version;
    private final String name;
    private final String description;
    private Collection<ModDependency> dependencies = new ArrayList<>();
    private List<String> mixinConfigs = new ArrayList<>();
    private final LoaderModMetadata delegate; 
    private final DetectedMod.LoaderType loaderType;

    public VyneVirtualMetadata(LoaderModMetadata delegate, DetectedMod.LoaderType loaderType) {
        this.delegate = delegate;
        this.loaderType = loaderType;
        this.id = delegate.getId();
        this.name = delegate.getName();
        this.description = delegate.getDescription();
        this.version = delegate.getVersion();
        this.dependencies = new ArrayList<>(delegate.getDependencies());
    }

    public VyneVirtualMetadata(DetectedMod.LoaderType loaderType, String id, String version, String name, String description, List<String> mixins) {
        this.delegate = null;
        this.loaderType = loaderType;
        this.id = id;
        this.name = name != null ? name : id;
        this.description = description != null ? description : "";
        this.mixinConfigs = mixins != null ? mixins : new ArrayList<>();
        try {
            this.version = Version.parse(version != null ? version : "1.0.0");
        } catch (Exception e) {
            try { this.version = Version.parse("1.0.0"); } catch (Exception ignored) {}
        }
    }

    @Override public String getId() { return id; }
    @Override public String getType() { return "fabric"; }
    @Override public Version getVersion() { return version; }
    @Override public void setVersion(Version version) { this.version = version; }
    @Override public String getName() { return name; }
    @Override public String getDescription() { return description; }
    @Override public int getSchemaVersion() { return delegate != null ? delegate.getSchemaVersion() : 1; }
    @Override public ModEnvironment getEnvironment() { return delegate != null ? delegate.getEnvironment() : ModEnvironment.UNIVERSAL; }
    @Override public boolean loadsInEnvironment(EnvType type) { return delegate != null ? delegate.loadsInEnvironment(type) : true; }

    @Override public Collection<String> getMixinConfigs(EnvType type) { 
        if (dev.stolym.vyne.core.VyneLauncher.isHardDisabled(id)) {
            return Collections.emptyList();
        }
        if (loaderType == DetectedMod.LoaderType.NEOFORGE || loaderType == DetectedMod.LoaderType.FORGE) {
            return Collections.emptyList();
        }
        if (delegate != null) return delegate.getMixinConfigs(type);
        return mixinConfigs;
    }

    @Override public List<EntrypointMetadata> getEntrypoints(String type) { return delegate != null ? delegate.getEntrypoints(type) : Collections.emptyList(); }
    @Override public Collection<String> getEntrypointKeys() { return delegate != null ? delegate.getEntrypointKeys() : Collections.emptyList(); }
    @Override public Map<String, String> getLanguageAdapterDefinitions() { return delegate != null ? delegate.getLanguageAdapterDefinitions() : Collections.emptyMap(); }
    @Override public Collection<NestedJarEntry> getJars() { return delegate != null ? delegate.getJars() : Collections.emptyList(); }
    @Override public String getClassTweaker() { return delegate != null ? delegate.getClassTweaker() : null; }
    @Override public Collection<String> getOldInitializers() { return delegate != null ? delegate.getOldInitializers() : Collections.emptyList(); }
    @Override public Collection<String> getProvides() { return delegate != null ? delegate.getProvides() : Collections.emptyList(); }
    @Override public Collection<Person> getAuthors() { return delegate != null ? delegate.getAuthors() : Collections.emptyList(); }
    @Override public Collection<Person> getContributors() { return delegate != null ? delegate.getContributors() : Collections.emptyList(); }
    @Override public ContactInformation getContact() { return delegate != null ? delegate.getContact() : ContactInformation.EMPTY; }
    @Override public Collection<String> getLicense() { return delegate != null ? delegate.getLicense() : Collections.singletonList("Unknown"); }
    @Override public Optional<String> getIconPath(int size) { return delegate != null ? delegate.getIconPath(size) : Optional.empty(); }
    @Override public boolean containsCustomValue(String key) { return delegate != null && delegate.containsCustomValue(key); }
    @Override public CustomValue getCustomValue(String key) { return delegate != null ? delegate.getCustomValue(key) : null; }
    @Override public Map<String, CustomValue> getCustomValues() { return delegate != null ? delegate.getCustomValues() : Collections.emptyMap(); }
    @Override public boolean containsCustomElement(String key) { return delegate != null && delegate.containsCustomElement(key); }

    @Override public void emitFormatWarnings() { if (delegate != null) delegate.emitFormatWarnings(); }
    @Override public void setDependencies(Collection<ModDependency> dependencies) { this.dependencies = dependencies; }
    @Override public Collection<ModDependency> getDependencies() { return dependencies; }
}
