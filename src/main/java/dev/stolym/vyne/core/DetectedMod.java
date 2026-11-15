/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.core;

import net.fabricmc.loader.impl.metadata.LoaderModMetadata;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DetectedMod {
    public enum LoaderType { FABRIC, NEOFORGE, FORGE, VYNE, UNKNOWN }
    public enum Environment { CLIENT, SERVER, UNIVERSAL }

    private final Path jarPath;
    private final LoaderType loaderType;
    private final String modId;
    private final String name;
    private final String version;
    private final String description;
    private final List<String> authors;
    private final List<String> mixins;
    private final LoaderModMetadata realMetadata;
    private Environment side = Environment.UNIVERSAL;
    private final Map<String, String> dependencies = new java.util.LinkedHashMap<>();
    private final Map<String, String> optionalDependencies = new java.util.LinkedHashMap<>();

    public DetectedMod(Path jarPath, LoaderType loaderType, String modId, String name,
                       String version, String description, List<String> authors) {
        this.jarPath      = jarPath;
        this.loaderType   = loaderType;
        this.modId        = modId;
        this.name         = name != null ? name : modId;
        this.version      = version != null ? version : "unknown";
        this.description  = description != null ? description : "";
        this.authors      = authors != null ? authors : new ArrayList<>();
        this.mixins       = new ArrayList<>();
        this.realMetadata = null;
    }

    public DetectedMod(Path jarPath, LoaderType loaderType, String modId, String name,
                       String version, String description, List<String> mixins, LoaderModMetadata realMetadata) {
        this.jarPath      = jarPath;
        this.loaderType   = loaderType;
        this.modId        = modId;
        this.name         = name != null ? name : modId;
        this.version      = version != null ? version : "unknown";
        this.description  = description != null ? description : "";
        this.authors      = new ArrayList<>();
        this.mixins       = mixins != null ? mixins : new ArrayList<>();
        this.realMetadata = realMetadata;

        if (realMetadata != null) {
            net.fabricmc.loader.api.metadata.ModEnvironment env = realMetadata.getEnvironment();
            if (env == net.fabricmc.loader.api.metadata.ModEnvironment.CLIENT) {
                this.side = Environment.CLIENT;
            } else if (env == net.fabricmc.loader.api.metadata.ModEnvironment.SERVER) {
                this.side = Environment.SERVER;
            } else {
                this.side = Environment.UNIVERSAL;
            }
        }
    }

    public void setSide(Environment side) { this.side = side; }
    public Environment getSide() { return side; }

    public Path getJarPath()                  { return jarPath; }
    public LoaderType getLoaderType()         { return loaderType; }
    public String getModId()                  { return modId; }

    public DetectedMod withLoaderType(LoaderType type) {
        DetectedMod m = realMetadata != null 
            ? new DetectedMod(jarPath, type, modId, name, version, description, mixins, realMetadata)
            : new DetectedMod(jarPath, type, modId, name, version, description, authors);
        m.setSide(this.side);
        m.dependencies.putAll(this.dependencies);
        m.optionalDependencies.putAll(this.optionalDependencies);
        return m;
    }

    public String getName()                   { return name; }
    public String getVersion()                { return version; }
    public String getDescription()            { return description; }
    public List<String> getAuthors()          { return authors; }
    public List<String> getMixins()           { return mixins; }
    public LoaderModMetadata getRealMetadata(){ return realMetadata; }
    public boolean hasFabricMetadata()        { return realMetadata != null; }
    public Map<String, String> getDependencies() { return dependencies; }
    public Map<String, String> getOptionalDependencies() { return optionalDependencies; }
    public void addDependency(String modId, String versionConstraint) { dependencies.put(modId, versionConstraint); }
    public void addOptionalDependency(String modId, String versionConstraint) { optionalDependencies.put(modId, versionConstraint); }

    @Override
    public String toString() { return modId + "@" + version + " [" + loaderType + "]"; }
}
