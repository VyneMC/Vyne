/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.fml;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.neoforged.neoforgespi.language.IConfigurable;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VyneModFileInfo implements IModFileInfo, IConfigurable {
    private final VyneModFile file;
    private final VyneConfigurable config;
    private final List<IModInfo> mods = new ArrayList<>();
    private final String license;

    public VyneModFileInfo(VyneModFile file, UnmodifiableConfig toml) {
        this.file = file;
        this.config = new VyneConfigurable(toml);
        this.license = this.config.<String>getConfigElement("license").orElse("");
    }

    void addMod(VyneModInfo mod) {
        mods.add(mod);
    }

    @Override
    public List<IModInfo> getMods() {
        return mods;
    }

    @Override
    public VyneModFile getFile() {
        return file;
    }

    @Override
    public List<LanguageSpec> requiredLanguageLoaders() {
        return List.of();
    }

    @Override
    public Map<String, Object> getFileProperties() {
        return Map.of();
    }

    @Override
    public boolean showAsResourcePack() {
        return false;
    }

    @Override
    public boolean showAsDataPack() {
        return false;
    }

    @Override
    public <T> Optional<T> getConfigElement(String... key) {
        return config.getConfigElement(key);
    }

    @Override
    public List<? extends IConfigurable> getConfigList(String... key) {
        return config.getConfigList(key);
    }

    @Override
    public String getLicense() {
        return license;
    }

    @Override
    public IConfigurable getConfig() {
        return this;
    }

    public URL getIssueURL() {
        return null;
    }

    public Optional<String> getCodeSigningFingerprint() {
        return Optional.empty();
    }

    public Optional<String> getTrustData() {
        return Optional.empty();
    }

    @Override
    public String moduleName() {
        return mods.isEmpty() ? file.getFileName() : mods.get(0).getModId();
    }

    @Override
    public String versionString() {
        return mods.isEmpty() ? "0.0.0" : mods.get(0).getVersion().toString();
    }

    @Override
    public List<String> usesServices() {
        return List.of();
    }

    @Override
    public String toString() {
        return "VyneModFileInfo[" + file.getFileName() + "]";
    }
}
