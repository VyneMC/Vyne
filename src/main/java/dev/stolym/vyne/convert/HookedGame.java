/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import dev.stolym.vyne.hooks.HookSurface;
import dev.stolym.vyne.util.VyneLogger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class HookedGame {
    public static final String SURFACE_VERSION = "21.1.265";

    private HookedGame() {}

    public static HookSurface surface() throws IOException {
        String base = "/vyne/hooks/neoforge-" + SURFACE_VERSION + "/";
        try (InputStream surface = HookedGame.class.getResourceAsStream(base + "surface.tsv");
             InputStream access = HookedGame.class.getResourceAsStream(base + "accesstransformer.cfg")) {
            if (surface == null) throw new IOException("Vyne's hook surface is missing from the jar: " + base);
            return HookSurface.load(surface, access);
        }
    }

    public static int fingerprint() throws IOException {
        int hash = 17;
        String base = "/vyne/hooks/neoforge-" + SURFACE_VERSION + "/";
        for (String file : new String[] {"surface.tsv", "accesstransformer.cfg"}) {
            try (InputStream in = HookedGame.class.getResourceAsStream(base + file)) {
                if (in != null) hash = hash * 31 + java.util.Arrays.hashCode(in.readAllBytes());
            }
        }
        return hash;
    }

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

    public static Path build(Path vanillaMojang, HookSurface surface, Path out) throws IOException {
        if (Files.isRegularFile(out)) return out;
        Files.createDirectories(out.getParent());
        Path tmp = out.resolveSibling("tmp-" + out.getFileName());
        int changed = 0;
        try (ZipFile zip = new ZipFile(vanillaMojang.toFile());
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmp))) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                String name = entry.getName();
                if (!name.endsWith(".class") || !surface.touches(name.substring(0, name.length() - 6))) continue;
                ClassNode node = new ClassNode();
                try (InputStream in = zip.getInputStream(entry)) {
                    new ClassReader(in).accept(node, 0);
                }
                if (!surface.apply(node, false)) continue;
                ClassWriter writer = new ClassWriter(0);
                node.accept(writer);
                zos.putNextEntry(new ZipEntry(name));
                zos.write(writer.toByteArray());
                zos.closeEntry();
                changed++;
            }
        }
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        VyneLogger.info("[Hooks] Applied Vyne's hook surface to " + changed + " vanilla classes");
        return out;
    }
}
