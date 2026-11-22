/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class FmlMount {
    private static final String REFLECTION_HELPER = "net/neoforged/fml/util/ObfuscationReflectionHelper";
    private static final String RUNTIME_NAMES = "dev/stolym/vyne/mappings/RuntimeNames";
    private static final int SCHEMA = 2;

    private FmlMount() {}

    public static Path prepare(Path fmlJar, Path cacheDir) throws IOException {
        String key = SCHEMA + ":" + Files.size(fmlJar) + ":" + Files.getLastModifiedTime(fmlJar).toMillis() + ":" + Routing.fingerprint();
        Path out = cacheDir.resolve("fml-" + Integer.toHexString(key.hashCode()) + ".jar");
        if (Files.isRegularFile(out)) return out;
        Files.createDirectories(cacheDir);
        Path tmp = out.resolveSibling(out.getFileName() + "." + ProcessHandle.current().pid() + ".tmp");
        try (ZipFile in = new ZipFile(fmlJar.toFile());
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmp))) {
            for (ZipEntry entry : Collections.list(in.entries())) {
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".class")) continue;
                if (Routing.isRouted(name.substring(0, name.length() - ".class".length()))) continue;
                zos.putNextEntry(new ZipEntry(name));
                try (InputStream data = in.getInputStream(entry)) {
                    byte[] bytes = Routing.apply(data.readAllBytes());
                    zos.write(name.equals(REFLECTION_HELPER + ".class") ? translateNames(bytes) : bytes);
                }
                zos.closeEntry();
            }
        }
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        return out;
    }

    private static byte[] translateNames(byte[] bytes) throws IOException {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int patched = 0;
        for (MethodNode method : node.methods) {
            boolean field = method.name.equals("findField") && method.desc.startsWith("(Ljava/lang/Class;Ljava/lang/String;)");
            boolean call = method.name.equals("findMethod") && method.desc.startsWith("(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)");
            if (!field && !call) continue;
            InsnList translate = new InsnList();
            translate.add(new VarInsnNode(Opcodes.ALOAD, 0));
            translate.add(new VarInsnNode(Opcodes.ALOAD, 1));
            if (call) translate.add(new VarInsnNode(Opcodes.ALOAD, 2));
            translate.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RUNTIME_NAMES, field ? "field" : "method", field
                    ? "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/String;"
                    : "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/String;", false));
            translate.add(new VarInsnNode(Opcodes.ASTORE, 1));
            method.instructions.insert(translate);
            patched++;
        }
        if (patched != 2) throw new IOException("NeoForge's ObfuscationReflectionHelper changed: expected findField and findMethod");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
