/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.fml;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.ModFileScanData;
import net.neoforged.neoforgespi.locating.IModFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public class VyneModList {
    private static final VyneModList INSTANCE = new VyneModList();

    public static VyneModList get() {
        return INSTANCE;
    }

    public List<IModFileInfo> getModFiles() {
        return new ArrayList<>(VyneMods.files());
    }

    public IModFileInfo getModFileById(String modId) {
        VyneModInfo mod = VyneMods.mod(modId);
        return mod == null ? null : mod.getOwningFile();
    }

    public Optional<? extends ModContainer> getModContainerById(String modId) {
        return Optional.ofNullable(VyneMods.container(modId));
    }

    public Optional<? extends ModContainer> getModContainerByObject(Object instance) {
        for (VyneModContainer container : VyneMods.containers()) {
            if (container.owns(instance)) return Optional.of(container);
        }
        return Optional.empty();
    }

    public List<IModInfo> getMods() {
        return new ArrayList<>(VyneMods.mods());
    }

    public boolean isLoaded(String modId) {
        if (VyneMods.mod(modId) != null) return true;
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }

    public int size() {
        return VyneMods.mods().size();
    }

    public List<ModFileScanData> getAllScanData() {
        List<ModFileScanData> out = new ArrayList<>();
        for (VyneModFileInfo file : VyneMods.files()) out.add(file.getFile().getScanResult());
        return out;
    }

    public void forEachModFile(Consumer<IModFile> action) {
        for (VyneModFileInfo file : VyneMods.files()) action.accept(file.getFile());
    }

    public <T> Stream<T> applyForEachModFile(Function<IModFile, T> function) {
        return VyneMods.files().stream().map(VyneModFileInfo::getFile).map(function);
    }

    public <T> Stream<T> applyForEachModFileAlphabetical(Function<IModFile, T> function) {
        return VyneMods.files().stream().map(VyneModFileInfo::getFile)
                .sorted(Comparator.comparing(VyneModFile::getFileName)).map(function);
    }

    public void forEachModContainer(BiConsumer<String, ModContainer> action) {
        for (VyneModContainer container : VyneMods.containers()) action.accept(container.getModId(), container);
    }

    public List<ModContainer> getSortedMods() {
        return new ArrayList<>(VyneMods.containers());
    }

    public void forEachModInOrder(Consumer<ModContainer> action) {
        for (VyneModContainer container : VyneMods.containers()) action.accept(container);
    }

    public <T> Stream<T> applyForEachModContainer(Function<ModContainer, T> function) {
        return VyneMods.containers().stream().map(function);
    }
}
