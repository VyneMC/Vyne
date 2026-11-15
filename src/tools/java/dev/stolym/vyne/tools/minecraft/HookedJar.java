/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.tools.minecraft;

import dev.stolym.vyne.hooks.HookSurface;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class HookedJar {
    private HookedJar() {}

    public static void main(String[] args) throws Exception {
        Path vanilla = Path.of(args[0]);
        Path surfaceFile = Path.of(args[1]);
        Path accessFile = Path.of(args[2]);
        Path out = Path.of(args[3]);
        HookSurface surface;
        try (InputStream s = Files.newInputStream(surfaceFile); InputStream a = Files.newInputStream(accessFile)) {
            surface = HookSurface.load(s, a);
        }
        int changed = 0;
        Path tmp = out.resolveSibling("tmp-" + out.getFileName());
        try (ZipFile zip = new ZipFile(vanilla.toFile());
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmp))) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (entry.isDirectory()) continue;
                byte[] bytes;
                try (InputStream in = zip.getInputStream(entry)) {
                    bytes = in.readAllBytes();
                }
                String name = entry.getName();
                if (name.endsWith(".class") && surface.touches(name.substring(0, name.length() - 6))) {
                    ClassNode node = new ClassNode();
                    new ClassReader(bytes).accept(node, 0);
                    if (surface.apply(node, true)) {
                        ClassWriter writer = new ClassWriter(0);
                        node.accept(writer);
                        bytes = writer.toByteArray();
                        changed++;
                    }
                }
                zos.putNextEntry(new ZipEntry(name));
                zos.write(bytes);
                zos.closeEntry();
            }
        }
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Hooked Minecraft (" + changed + " classes changed by the hook surface): " + out);
    }
}
