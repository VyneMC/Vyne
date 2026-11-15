/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import dev.stolym.vyne.util.VyneLogger;
import io.sigpipe.jbsdiff.Diff;
import io.sigpipe.jbsdiff.Patch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class HookPatches {
    public static final String INDEX = "index.properties";
    private static final String NEW = "new";

    private HookPatches() {}

    public static Path apply(Path vanillaMojang, boolean dedicatedServer, Path cacheRoot) throws IOException {
        String side = dedicatedServer ? "server" : "client";
        byte[] patches;
        try (InputStream in = HookPatches.class.getResourceAsStream("/META-INF/vyne/patches/" + side + ".zip")) {
            if (in == null) throw new IOException("Vyne's " + side + " patches are missing from the Vyne jar");
            patches = in.readAllBytes();
        }
        Path out = cacheRoot.resolve("hooked").resolve(side + "-" + sha1(patches).substring(0, 12) + ".jar");
        if (Files.isRegularFile(out)) return out;

        Map<String, byte[]> entries = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(patches))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.put(entry.getName(), zip.readAllBytes());
        }
        Properties index = new Properties();
        index.load(new StringReader(new String(entries.get(INDEX), StandardCharsets.UTF_8)));

        dev.stolym.vyne.hooks.HookSurface access = accessRules();
        Files.createDirectories(out.getParent());
        Path tmp = out.resolveSibling("tmp-" + out.getFileName());
        int count = 0;
        try (ZipFile vanilla = new ZipFile(vanillaMojang.toFile());
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmp))) {
            for (ZipEntry entry : Collections.list(vanilla.entries())) {
                String name = entry.getName();
                if (!name.endsWith(".class") || index.containsKey(name)
                        || !access.touches(name.substring(0, name.length() - 6))) continue;
                try (InputStream in = vanilla.getInputStream(entry)) {
                    zos.putNextEntry(new ZipEntry(name));
                    zos.write(widen(access, in.readAllBytes()));
                    zos.closeEntry();
                }
            }
            for (String name : index.stringPropertyNames()) {
                String expected = index.getProperty(name);
                byte[] base = new byte[0];
                if (!expected.equals(NEW)) {
                    ZipEntry entry = vanilla.getEntry(name);
                    if (entry == null) throw new IOException("Your Minecraft jar has no " + name + ", which Vyne's patches expect");
                    try (InputStream in = vanilla.getInputStream(entry)) {
                        base = in.readAllBytes();
                    }
                    if (!sha1(base).equals(expected)) {
                        throw new IOException("Vyne's patches do not match your Minecraft jar (" + name + " differs)");
                    }
                }
                ByteArrayOutputStream patched = new ByteArrayOutputStream();
                try {
                    Patch.patch(base, entries.get(name + ".patch"), patched);
                } catch (Exception e) {
                    throw new IOException("Could not apply Vyne's patch to " + name, e);
                }
                zos.putNextEntry(new ZipEntry(name));
                zos.write(widen(access, patched.toByteArray()));
                zos.closeEntry();
                count++;
            }
        }
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        VyneLogger.info("[Hooks] Applied Vyne's patches to " + count + " vanilla " + side + " classes");
        return out;
    }

    private static dev.stolym.vyne.hooks.HookSurface accessRules() throws IOException {
        String path = "/vyne/hooks/neoforge-" + HookedGame.SURFACE_VERSION + "/accesstransformer.cfg";
        try (InputStream rules = HookPatches.class.getResourceAsStream(path)) {
            if (rules == null) throw new IOException("NeoForge's access rules are missing from the Vyne jar: " + path);
            return dev.stolym.vyne.hooks.HookSurface.load(new java.io.ByteArrayInputStream(new byte[0]), rules);
        }
    }

    private static byte[] widen(dev.stolym.vyne.hooks.HookSurface access, byte[] bytes) {
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(bytes).accept(node, 0);
        if (!access.touches(node.name) || !access.apply(node, false)) return bytes;
        org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    public static void write(Path vanillaMojang, Path hooked, Path out) throws IOException {
        Properties index = new Properties();
        Files.createDirectories(out.getParent());
        try (ZipFile vanilla = new ZipFile(vanillaMojang.toFile());
             ZipFile target = new ZipFile(hooked.toFile());
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(out))) {
            for (ZipEntry entry : Collections.list(target.entries())) {
                String name = entry.getName();
                if (!name.endsWith(".class")) continue;
                byte[] now;
                try (InputStream in = target.getInputStream(entry)) {
                    now = in.readAllBytes();
                }
                ZipEntry baseEntry = vanilla.getEntry(name);
                byte[] base = new byte[0];
                if (baseEntry != null) {
                    try (InputStream in = vanilla.getInputStream(baseEntry)) {
                        base = in.readAllBytes();
                    }
                    if (java.util.Arrays.equals(base, now)) continue;
                }
                ByteArrayOutputStream patch = new ByteArrayOutputStream();
                try {
                    Diff.diff(base, now, patch);
                } catch (Exception e) {
                    throw new IOException("Could not diff " + name, e);
                }
                zos.putNextEntry(new ZipEntry(name + ".patch"));
                zos.write(patch.toByteArray());
                zos.closeEntry();
                index.setProperty(name, baseEntry == null ? NEW : sha1(base));
            }
            zos.putNextEntry(new ZipEntry(INDEX));
            ByteArrayOutputStream text = new ByteArrayOutputStream();
            index.store(text, null);
            zos.write(text.toByteArray());
            zos.closeEntry();
        }
    }

    static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
