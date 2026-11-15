/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the Licenses/LICENSE_VYNE file in the project root.
 */

package dev.stolym.vyne.resources;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class VyneModContainer implements ModContainer {
    private static final Logger LOG = LogManager.getLogger("VynePacks");

    private final Path jarPath;
    private final ModMetadata metadata;
    private volatile List<Path> rootPaths;

    public VyneModContainer(Path jarPath, String modId, String name, String version) {
        this.jarPath = jarPath;
        this.metadata = buildMetadata(modId, name, version);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ModMetadata buildMetadata(String modId, String name, String version) {
        String safeVersion = (version == null || version.isEmpty()) ? "0.0.0" : version;

        for (String attempt : new String[]{safeVersion, sanitizeVersion(safeVersion), "0.0.0"}) {
            try {
                Class<?> b = Class.forName("net.fabricmc.loader.impl.metadata.BuiltinModMetadata$Builder");
                Constructor<?> ctor = b.getConstructor(String.class, String.class);
                Object builder = ctor.newInstance(modId, attempt);
                Method setName = b.getMethod("setName", String.class);
                setName.invoke(builder, name == null ? modId : name);
                Method build = b.getMethod("build");
                return (ModMetadata) build.invoke(builder);
            } catch (Throwable ignored) {
            }
        }
        throw new IllegalStateException(
            "[VyneModContainer] Could not build ModMetadata for " + modId + "@" + safeVersion);
    }

    private static String sanitizeVersion(String v) {
        StringBuilder sb = new StringBuilder();
        for (char c : v.toCharArray()) {
            if (Character.isDigit(c) || c == '.') sb.append(c);
            else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '.') sb.append('.');
        }
        String s = sb.toString().replaceAll("\\.+", ".").replaceAll("^\\.|\\.$", "");
        if (s.isEmpty()) return "0.0.0";

        int dots = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '.') dots++;
        while (dots < 2) { s = s + ".0"; dots++; }
        return s;
    }

    @Override
    public ModMetadata getMetadata() {
        return metadata;
    }

    @Override
    public List<Path> getRootPaths() {
        List<Path> r = this.rootPaths;
        if (r == null) {
            synchronized (this) {
                r = this.rootPaths;
                if (r == null) {
                    r = openRootsOnce();
                    this.rootPaths = r;
                }
            }
        }
        return r;
    }

    private List<Path> openRootsOnce() {
        try {
            if (Files.isDirectory(jarPath)) {
                return List.of(jarPath);
            }
            if (!Files.isRegularFile(jarPath)) {
                LOG.error("[VyneModContainer] Not a file or directory: {}", jarPath);
                return List.of();
            }
            FileSystem fs = tryOpenJarFileSystem(jarPath);
            return List.of(fs.getPath("/"));
        } catch (IOException | RuntimeException t) {
            LOG.error("[VyneModContainer] Could not open jar as a filesystem: {}", jarPath, t);
            return List.of();
        }
    }

    private static FileSystem tryOpenJarFileSystem(Path jarPath) throws IOException {
        try {
            return FileSystems.newFileSystem(jarPath, (ClassLoader) null);
        } catch (FileSystemAlreadyExistsException e) {
            URI jarUri = URI.create("jar:" + jarPath.toUri() + "!/");
            return FileSystems.getFileSystem(jarUri);
        } catch (IOException | RuntimeException first) {
            Map<String, ?> env = Collections.singletonMap("create", "false");
            URI jarUri = URI.create("jar:" + jarPath.toUri() + "!/");
            try {
                return FileSystems.newFileSystem(jarUri, env);
            } catch (FileSystemAlreadyExistsException exists) {
                return FileSystems.getFileSystem(jarUri);
            } catch (IOException | RuntimeException second) {
                LOG.warn("[VyneModContainer] newFileSystem(Path) failed ({}), jar URI retry failed ({})",
                    first.getMessage(), second.getMessage());
                if (first instanceof IOException) {
                    throw (IOException) first;
                }
                throw new IOException(first);
            }
        }
    }

    @Override
    public ModOrigin getOrigin() {
        return null;
    }

    @Override
    public Optional<ModContainer> getContainingMod() {
        return Optional.empty();
    }

    @Override
    public Collection<ModContainer> getContainedMods() {
        return Collections.emptyList();
    }

    @Override
    public Path getRoot() {
        return getRootPath();
    }

    @Override
    public Path getRootPath() {
        List<Path> r = getRootPaths();
        return r.isEmpty() ? jarPath : r.get(0);
    }

    @Override
    public Path getPath(String file) {
        for (Path root : getRootPaths()) {
            Path p = root.resolve(file.replace("/", root.getFileSystem().getSeparator()));
            if (Files.exists(p)) return p;
        }
        return getRootPath().resolve(file);
    }
}
