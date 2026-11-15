/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.tools.hooks;

import dev.stolym.vyne.convert.NeoForgeInstall;
import dev.stolym.vyne.engine.dynamic.NeoForgeReferenceDownloader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class SurfaceExtract {
    private SurfaceExtract() {}

    public static void main(String[] args) throws Exception {
        String version = args[0];
        Path out = Path.of(args[1]);
        Path work = Path.of(System.getProperty("vyne.gameDir"));
        NeoForgeInstall install = NeoForgeInstall.ensure(version, false, work);
        Path universal = NeoForgeReferenceDownloader.resolve(version, work.resolve(".vyne").resolve("references"));
        if (universal == null) throw new IOException("Could not download NeoForge " + version);

        Map<String, ClassNode> patched = read(install.patchedGame(), true);
        Map<String, ClassNode> vanilla = read(install.vanillaGame(), true);
        Map<String, ClassNode> api = read(universal, false);

        int interfaces = 0;
        int supers = 0;
        int accessChanges = 0;
        int fields = 0;
        int methods = 0;
        Files.createDirectories(out.getParent());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            w.println("class\tkind\tname\tdesc\taccess\tsignature\texceptions\tflags");
            for (Map.Entry<String, ClassNode> entry : patched.entrySet()) {
                ClassNode now = entry.getValue();
                ClassNode before = vanilla.get(entry.getKey());
                if (before == null || anonymous(now.name)) continue;
                if (now.superName != null && !now.superName.equals(before.superName)) {
                    w.println(now.name + "\textends\t" + now.superName + "\t" + before.superName + "\t0\t" + nz(now.signature) + "\t\t");
                    supers++;
                }
                if (widened(before.access, now.access)) {
                    w.println(now.name + "\taccess\t\t\t" + now.access + "\t\t\t");
                    accessChanges++;
                }
                for (org.objectweb.asm.tree.InnerClassNode inner : now.innerClasses) {
                    for (org.objectweb.asm.tree.InnerClassNode old : before.innerClasses) {
                        if (old.name.equals(inner.name) && !anonymous(inner.name) && widened(old.access, inner.access)) {
                            w.println(now.name + "\tinner-access\t" + inner.name + "\t\t" + inner.access + "\t\t\t");
                            accessChanges++;
                        }
                    }
                }
                Map<String, FieldNode> oldFieldNodes = new HashMap<>();
                for (FieldNode f : before.fields) oldFieldNodes.put(f.name, f);
                for (FieldNode f : now.fields) {
                    FieldNode old = oldFieldNodes.get(f.name);
                    if (old != null && widened(old.access, f.access)) {
                        w.println(now.name + "\tfield-access\t" + f.name + "\t" + f.desc + "\t" + f.access + "\t\t\t");
                        accessChanges++;
                    }
                }
                Map<String, MethodNode> oldMethodNodes = new HashMap<>();
                for (MethodNode m : before.methods) oldMethodNodes.put(m.name + m.desc, m);
                for (MethodNode m : now.methods) {
                    MethodNode old = oldMethodNodes.get(m.name + m.desc);
                    if (old != null && (old.access & Opcodes.ACC_ABSTRACT) != 0 && (m.access & Opcodes.ACC_ABSTRACT) == 0) {
                        w.println(now.name + "\tmethod-concrete\t" + m.name + "\t" + m.desc + "\t" + m.access + "\t\t\t");
                        accessChanges++;
                    } else if (old != null && (m.access & Opcodes.ACC_SYNTHETIC) == 0 && widened(old.access, m.access)) {
                        w.println(now.name + "\tmethod-access\t" + m.name + "\t" + m.desc + "\t" + m.access + "\t\t\t");
                        accessChanges++;
                    }
                }
                Set<String> added = new HashSet<>(now.interfaces);
                added.removeAll(before.interfaces);
                for (String type : now.interfaces) {
                    if (!added.contains(type)) continue;
                    w.println(now.name + "\timplements\t" + type + "\t\t0\t" + nz(now.signature) + "\t\t");
                    interfaces++;
                }
                Set<String> oldFields = new HashSet<>();
                for (FieldNode f : before.fields) oldFields.add(f.name);
                for (FieldNode f : now.fields) {
                    if (oldFields.contains(f.name) || (f.access & Opcodes.ACC_SYNTHETIC) != 0) continue;
                    w.println(now.name + "\tfield\t" + f.name + "\t" + f.desc + "\t" + f.access + "\t" + nz(f.signature) + "\t\t");
                    fields++;
                }
                Set<String> oldMethods = new HashSet<>();
                for (MethodNode m : before.methods) oldMethods.add(m.name + m.desc);
                Set<String> defaults = defaults(now, api, patched);
                for (MethodNode m : now.methods) {
                    if (oldMethods.contains(m.name + m.desc)) continue;
                    if ((m.access & Opcodes.ACC_BRIDGE) != 0) {
                        String target = bridgeTarget(m);
                        if (target != null) {
                            w.println(now.name + "\tbridge\t" + m.name + "\t" + m.desc + "\t" + m.access + "\t" + target + "\t\t");
                            methods++;
                        }
                        continue;
                    }
                    if ((m.access & Opcodes.ACC_SYNTHETIC) != 0) continue;
                    String exceptions = m.exceptions == null ? "" : String.join(" ", m.exceptions);
                    String flags = defaults.contains(m.name + m.desc) ? "override" : "";
                    w.println(now.name + "\tmethod\t" + m.name + "\t" + m.desc + "\t" + m.access + "\t" + nz(m.signature)
                            + "\t" + exceptions + "\t" + flags);
                    methods++;
                }
            }
        }
        System.out.println("Hook surface for NeoForge " + version + ": " + supers + " superclasses, " + interfaces
                + " interfaces, " + fields + " fields, " + methods + " methods, " + accessChanges + " access changes -> " + out);
    }

    private static Set<String> defaults(ClassNode type, Map<String, ClassNode> api, Map<String, ClassNode> game) {
        Set<String> out = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(type.interfaces);
        if (type.superName != null) queue.add(type.superName);
        Set<String> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            String name = queue.poll();
            if (!seen.add(name)) continue;
            ClassNode node = api.containsKey(name) ? api.get(name) : game.get(name);
            if (node == null) {
                inheritedFromJdk(name, out);
                continue;
            }
            for (MethodNode m : node.methods) {
                if ((m.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0 && !m.name.startsWith("<")) out.add(m.name + m.desc);
            }
            queue.addAll(node.interfaces);
            if (node.superName != null) queue.add(node.superName);
        }
        return out;
    }

    private static void inheritedFromJdk(String internalName, Set<String> out) {
        try {
            Class<?> type = Class.forName(internalName.replace('/', '.'), false, ClassLoader.getPlatformClassLoader());
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (!java.lang.reflect.Modifier.isStatic(m.getModifiers()) && !java.lang.reflect.Modifier.isPrivate(m.getModifiers())) {
                        out.add(m.getName() + org.objectweb.asm.Type.getMethodDescriptor(m));
                    }
                }
            }
            for (Class<?> i : type.getInterfaces()) inheritedFromJdk(i.getName().replace('.', '/'), out);
        } catch (ClassNotFoundException | LinkageError ignored) {
        }
    }

    private static String bridgeTarget(MethodNode bridge) {
        for (org.objectweb.asm.tree.AbstractInsnNode insn : bridge.instructions) {
            if (insn instanceof org.objectweb.asm.tree.MethodInsnNode call
                    && (call.getOpcode() == Opcodes.INVOKEVIRTUAL || call.getOpcode() == Opcodes.INVOKEINTERFACE)) {
                return call.name + call.desc;
            }
        }
        return null;
    }

    private static boolean anonymous(String name) {
        int dollar = name.lastIndexOf('$');
        return dollar >= 0 && dollar + 1 < name.length() && Character.isDigit(name.charAt(dollar + 1));
    }

    private static int rank(int access) {
        if ((access & Opcodes.ACC_PUBLIC) != 0) return 3;
        if ((access & Opcodes.ACC_PROTECTED) != 0) return 2;
        if ((access & Opcodes.ACC_PRIVATE) != 0) return 0;
        return 1;
    }

    private static boolean widened(int before, int now) {
        return rank(now) > rank(before) || ((before & Opcodes.ACC_FINAL) != 0 && (now & Opcodes.ACC_FINAL) == 0);
    }

    private static int visibility(int access) {
        return access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
    }

    private static int final_(int access) {
        return access & Opcodes.ACC_FINAL;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static Map<String, ClassNode> read(Path jar, boolean minecraftOnly) throws IOException {
        Map<String, ClassNode> out = new TreeMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                String name = entry.getName();
                if (!name.endsWith(".class")) continue;
                if (minecraftOnly && !(name.startsWith("net/minecraft/") || name.startsWith("com/mojang/"))) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    ClassNode node = new ClassNode();
                    new ClassReader(in).accept(node, minecraftOnly ? 0 : ClassReader.SKIP_CODE);
                    out.put(node.name, node);
                }
            }
        }
        return out;
    }
}
