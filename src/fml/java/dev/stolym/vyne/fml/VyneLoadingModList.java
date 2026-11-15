/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.fml;

import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class VyneLoadingModList {
    private static final VyneLoadingModList INSTANCE = new VyneLoadingModList();

    public static VyneLoadingModList get() {
        return INSTANCE;
    }

    public void addMixinConfigs() {
    }

    public void addAccessTransformers() {
    }

    public void addEnumExtenders() {
    }

    public List<IModFileInfo> getPlugins() {
        return List.of();
    }

    public List<VyneModFileInfo> getModFiles() {
        return VyneMods.files();
    }

    public Path findResource(String name) {
        for (VyneModFileInfo file : VyneMods.files()) {
            Path path = file.getFile().findResource(name);
            if (Files.exists(path)) return path;
        }
        return null;
    }

    public Enumeration<URL> findAllURLsForResource(String name) {
        List<URL> urls = new ArrayList<>();
        for (VyneModFileInfo file : VyneMods.files()) {
            Path path = file.getFile().findResource(name);
            if (!Files.exists(path)) continue;
            try {
                urls.add(path.toUri().toURL());
            } catch (MalformedURLException ignored) {
            }
        }
        return Collections.enumeration(urls);
    }

    public VyneModFileInfo getModFileById(String modId) {
        VyneModInfo mod = VyneMods.mod(modId);
        return mod == null ? null : mod.getOwningFile();
    }

    public List<VyneModInfo> getMods() {
        return VyneMods.mods();
    }

    public List<VyneModInfo> getDependencies(IModInfo mod) {
        return List.of();
    }

    public boolean hasErrors() {
        return VyneModLoader.hasErrors();
    }

    public List<ModLoadingIssue> getModLoadingIssues() {
        return VyneModLoader.getLoadingIssues();
    }
}
