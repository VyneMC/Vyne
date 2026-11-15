/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.fml;

import cpw.mods.jarhandling.SecureJar;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.ModFileScanData;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class VyneModFile implements IModFile {
    private final Path path;
    private final ModFileScanData scanData;
    private VyneModFileInfo info;
    private FileSystem fileSystem;

    public VyneModFile(Path path, ModFileScanData scanData) {
        this.path = path;
        this.scanData = scanData;
    }

    void setInfo(VyneModFileInfo info) {
        this.info = info;
        scanData.addModFileInfo(info);
    }

    private VyneSecureJar secureJar;

    private synchronized FileSystem fileSystem() throws IOException {
        if (fileSystem == null) fileSystem = FileSystems.newFileSystem(path);
        return fileSystem;
    }

    public Path rootPath() {
        try {
            return fileSystem().getPath("/");
        } catch (IOException e) {
            return path;
        }
    }

    @Override
    public Path findResource(String... pathName) {
        try {
            return fileSystem().getPath(String.join("/", pathName));
        } catch (IOException e) {
            return path.resolve(String.join("/", pathName));
        }
    }

    @Override
    public Supplier<Map<String, Object>> getSubstitutionMap() {
        return Map::of;
    }

    @Override
    public Type getType() {
        return Type.MOD;
    }

    @Override
    public Path getFilePath() {
        return path;
    }

    @Override
    public synchronized SecureJar getSecureJar() {
        if (secureJar == null) secureJar = new VyneSecureJar(this);
        return secureJar;
    }

    @Override
    public void setSecurityStatus(SecureJar.Status status) {
    }

    @Override
    public List<IModInfo> getModInfos() {
        return info == null ? List.of() : info.getMods();
    }

    @Override
    public ModFileScanData getScanResult() {
        return scanData;
    }

    @Override
    public String getFileName() {
        return path.getFileName().toString();
    }

    @Override
    public ModFileDiscoveryAttributes getDiscoveryAttributes() {
        return null;
    }

    @Override
    public IModFileInfo getModFileInfo() {
        return info;
    }

    public VyneModFileInfo getVyneModFileInfo() {
        return info;
    }

    @Override
    public String toString() {
        return "VyneModFile[" + getFileName() + "]";
    }
}
