/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class HookedGame {
    public static final String SURFACE_VERSION = "21.1.265";

    private HookedGame() {}

    public static Path vyneNeoForge(Path cacheRoot) throws IOException {
        byte[] bytes;
        try (InputStream in = HookedGame.class.getResourceAsStream("/META-INF/vyne/neoforge.jar")) {
            if (in == null) throw new IOException("Vyne's NeoForge API is missing from the Vyne jar");
            bytes = in.readAllBytes();
        }
        Path out = cacheRoot.resolve("vyne-neoforge").resolve("neoforge-" + SURFACE_VERSION + "-"
                + Integer.toHexString(java.util.Arrays.hashCode(bytes)) + ".jar");
        if (!Files.isRegularFile(out)) {
            Files.createDirectories(out.getParent());
            Path tmp = out.resolveSibling("tmp-" + out.getFileName());
            Files.write(tmp, bytes);
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        }
        return out;
    }
}
