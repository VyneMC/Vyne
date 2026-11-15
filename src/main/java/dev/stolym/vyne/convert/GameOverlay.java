/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class GameOverlay {
    private static volatile ZipFile overlay;

    private GameOverlay() {}

    public static synchronized void install(Path jar) throws IOException {
        ZipFile previous = overlay;
        overlay = new ZipFile(jar.toFile());
        if (previous != null) previous.close();
    }

    public static boolean isActive() {
        return overlay != null;
    }

    public static byte[] read(String classFileName) {
        ZipFile zip = overlay;
        if (zip == null || !classFileName.startsWith("net/minecraft/") && !classFileName.startsWith("com/mojang/")) {
            return null;
        }
        ZipEntry entry = zip.getEntry(classFileName);
        if (entry == null) return null;
        try (InputStream in = zip.getInputStream(entry)) {
            return in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    public static int size() {
        ZipFile zip = overlay;
        return zip == null ? 0 : zip.size();
    }
}
