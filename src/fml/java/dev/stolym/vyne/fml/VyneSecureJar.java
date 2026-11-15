/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.fml;

import cpw.mods.jarhandling.SecureJar;

import java.nio.file.Path;
import java.security.CodeSigner;
import java.util.jar.Attributes;

public class VyneSecureJar implements SecureJar {
    private final VyneModFile file;

    public VyneSecureJar(VyneModFile file) {
        this.file = file;
    }

    @Override
    public ModuleDataProvider moduleDataProvider() {
        return null;
    }

    @Override
    public Path getPrimaryPath() {
        return file.getFilePath();
    }

    @Override
    public CodeSigner[] getManifestSigners() {
        return null;
    }

    @Override
    public Status verifyPath(Path path) {
        return Status.NONE;
    }

    @Override
    public Status getFileStatus(String name) {
        return Status.NONE;
    }

    @Override
    public Attributes getTrustedManifestEntries(String name) {
        return null;
    }

    @Override
    public boolean hasSecurityData() {
        return false;
    }

    @Override
    public String name() {
        return file.getFileName();
    }

    @Override
    public Path getPath(String first, String... rest) {
        Path path = getRootPath().resolve(first);
        for (String part : rest) path = path.resolve(part);
        return path;
    }

    @Override
    public Path getRootPath() {
        return file.rootPath();
    }

    @Override
    public void close() {
    }
}
