/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import dev.stolym.vyne.util.VyneLogger;
import net.fabricmc.mappingio.tree.MappingTree;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class SharedMethodSplit {
    static final String PREFIX = "vyne$split$";

    private record Info(String superName, List<String> interfaces, Set<String> methods) {}

    private record Split(String owner, String name, String desc) {}

    private final Map<String, Map<String, String>> methodNames = new HashMap<>();
    private final Map<String, Info> hierarchy = new HashMap<>();
    private final Map<String, List<String>> subtypes = new HashMap<>();
    private final Map<String, Set<String>> supertypeCache = new HashMap<>();
    private final List<Split> splits = new ArrayList<>();

    private SharedMethodSplit(MappingTree mappings) {
        int named = mappings.getNamespaceId("named");
        int intermediary = mappings.getNamespaceId("intermediary");
        for (MappingTree.ClassMapping type : mappings.getClasses()) {
            String className = type.getName(named);
            if (className == null) continue;
            Map<String, String> methods = new HashMap<>();
            for (MappingTree.MethodMapping method : type.getMethods()) {
                String name = method.getName(named);
                String desc = method.getDesc(named);
                String target = method.getName(intermediary);
                if (name != null && desc != null && target != null && !target.equals(name)) methods.put(name + desc, target);
            }
            methodNames.put(className, methods);
        }
    }

    static List<Path> prepare(List<Path> inputs, Collection<Path> classpath, MappingTree mappings, Path workDir)
            throws IOException {
        long start = System.nanoTime();
        SharedMethodSplit split = new SharedMethodSplit(mappings);
        for (Path entry : classpath) {
            if (entry != null && Files.isRegularFile(entry) && !inputs.contains(entry)) split.index(entry);
        }
        List<Map<String, byte[]>> contents = new ArrayList<>();
        for (Path input : inputs) {
            Map<String, byte[]> entries = read(input);
            contents.add(entries);
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                if (entry.getKey().endsWith(".class")) split.index(entry.getValue());
            }
        }
        split.findConflicts(contents);
        VyneLogger.info("[PatchedGame] Shared-method check: " + split.splits.size() + " split(s) in "
                + (System.nanoTime() - start) / 1_000_000 + " ms");
        if (split.splits.isEmpty()) return inputs;
        List<Path> prepared = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            Path out = workDir.resolve("split-" + i + "-" + ProcessHandle.current().pid() + ".jar");
            split.rewrite(contents.get(i), out);
            prepared.add(out);
        }
        return prepared;
    }

    static byte[] restoreNames(byte[] bytes) {
        if (!containsPrefix(bytes)) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            method.name = restore(method.name);
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call) call.name = restore(call.name);
                else if (insn instanceof InvokeDynamicInsnNode indy) {
                    for (int i = 0; i < indy.bsmArgs.length; i++) indy.bsmArgs[i] = restoreHandle(indy.bsmArgs[i]);
                } else if (insn instanceof LdcInsnNode ldc) ldc.cst = restoreHandle(ldc.cst);
            }
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean containsPrefix(byte[] bytes) {
        byte[] marker = PREFIX.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i <= bytes.length - marker.length; i++) {
            for (int j = 0; j < marker.length; j++) {
                if (bytes[i + j] != marker[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    private static String restore(String name) {
        return name.startsWith(PREFIX) ? name.substring(PREFIX.length()) : name;
    }

    private static Object restoreHandle(Object value) {
        if (value instanceof Handle h && h.getName().startsWith(PREFIX)) {
            return new Handle(h.getTag(), h.getOwner(), restore(h.getName()), h.getDesc(), h.isInterface());
        }
        return value;
    }

    private void index(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (!entry.getName().endsWith(".class") || entry.getName().startsWith("META-INF/")) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    index(in.readAllBytes());
                }
            }
        }
    }

    private void index(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        Set<String> methods = new HashSet<>();
        for (MethodNode method : node.methods) {
            if ((method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0 && !method.name.startsWith("<")) {
                methods.add(method.name + method.desc);
            }
        }
        Info previous = hierarchy.put(node.name, new Info(node.superName, node.interfaces, methods));
        if (previous != null) {
            if (previous.superName() != null) subtypes.getOrDefault(previous.superName(), new ArrayList<>()).remove(node.name);
            for (String parent : previous.interfaces()) subtypes.getOrDefault(parent, new ArrayList<>()).remove(node.name);
        }
        if (node.superName != null) subtypes.computeIfAbsent(node.superName, k -> new ArrayList<>()).add(node.name);
        for (String parent : node.interfaces) subtypes.computeIfAbsent(parent, k -> new ArrayList<>()).add(node.name);
    }

    private boolean isMinecraft(String type) {
        return methodNames.containsKey(type);
    }

    private final Map<String, String> mappedCache = new HashMap<>();

    private String mappedName(String type, String name, String desc) {
        String key = type + '.' + name + desc;
        if (mappedCache.containsKey(key)) return mappedCache.get(key);
        String result = lookupMappedName(type, name, desc);
        mappedCache.put(key, result);
        return result;
    }

    private String lookupMappedName(String type, String name, String desc) {
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(type);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!seen.add(current)) continue;
            Map<String, String> mapped = methodNames.get(current);
            if (mapped != null) {
                String target = mapped.get(name + desc);
                if (target != null) return target;
            }
            Info info = hierarchy.get(current);
            if (info == null) continue;
            if (info.superName() != null) queue.add(info.superName());
            queue.addAll(info.interfaces());
        }
        return null;
    }

    private Set<String> supertypes(String type) {
        Set<String> cached = supertypeCache.get(type);
        if (cached != null) return cached;
        Set<String> out = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(type);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!out.add(current)) continue;
            Info info = hierarchy.get(current);
            if (info == null) continue;
            if (info.superName() != null) queue.add(info.superName());
            queue.addAll(info.interfaces());
        }
        supertypeCache.put(type, out);
        return out;
    }

    private void findConflicts(List<Map<String, byte[]>> contents) {
        Set<String> candidates = new TreeSet<>();
        for (Map<String, byte[]> entries : contents) {
            for (String file : entries.keySet()) {
                if (file.endsWith(".class") && !file.startsWith("META-INF/")) {
                    String type = file.substring(0, file.length() - ".class".length());
                    if (!isMinecraft(type)) candidates.add(type);
                }
            }
        }
        for (String type : candidates) {
            Info info = hierarchy.get(type);
            if (info == null) continue;
            for (String method : new TreeSet<>(info.methods())) {
                int paren = method.indexOf('(');
                String name = method.substring(0, paren);
                String desc = method.substring(paren);
                if (mappedName(type, name, desc) != null) continue;
                Set<String> targets = new TreeSet<>();
                ArrayDeque<String> queue = new ArrayDeque<>(subtypes.getOrDefault(type, List.of()));
                Set<String> seen = new HashSet<>();
                while (!queue.isEmpty()) {
                    String sub = queue.poll();
                    if (!seen.add(sub)) continue;
                    String target = mappedName(sub, name, desc);
                    if (target != null) targets.add(target);
                    else queue.addAll(subtypes.getOrDefault(sub, List.of()));
                }
                if (targets.size() > 1) {
                    splits.add(new Split(type, name, desc));
                    VyneLogger.info("[PatchedGame] Splitting " + type + "." + name + desc + " (Minecraft names it "
                            + String.join(" and ", targets) + ")");
                }
            }
        }
    }

    private Split splitFor(String owner, String name, String desc) {
        for (Split split : splits) {
            if (split.name().equals(name) && split.desc().equals(desc) && supertypes(owner).contains(split.owner())
                    && mappedName(owner, name, desc) == null) {
                return split;
            }
        }
        return null;
    }

    private void rewrite(Map<String, byte[]> entries, Path out) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(out))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                byte[] bytes = entry.getValue();
                if (entry.getKey().endsWith(".class") && !entry.getKey().startsWith("META-INF/")) bytes = rewrite(bytes);
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(bytes);
                zos.closeEntry();
            }
        }
    }

    private byte[] rewrite(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if ((method.access & Opcodes.ACC_STATIC) == 0 && splitFor(node.name, method.name, method.desc) != null) {
                method.name = PREFIX + method.name;
                changed = true;
            }
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && insn.getOpcode() != Opcodes.INVOKESTATIC
                        && splitFor(call.owner, call.name, call.desc) != null) {
                    call.name = PREFIX + call.name;
                    changed = true;
                } else if (insn instanceof InvokeDynamicInsnNode indy) {
                    for (int i = 0; i < indy.bsmArgs.length; i++) {
                        Object renamed = renameHandle(indy.bsmArgs[i]);
                        if (renamed != indy.bsmArgs[i]) {
                            indy.bsmArgs[i] = renamed;
                            changed = true;
                        }
                    }
                } else if (insn instanceof LdcInsnNode ldc) {
                    Object renamed = renameHandle(ldc.cst);
                    if (renamed != ldc.cst) {
                        ldc.cst = renamed;
                        changed = true;
                    }
                }
            }
        }
        for (Split split : splits) {
            if (needsBridge(node, split)) {
                node.methods.add(bridge(node, split));
                changed = true;
            }
        }
        if (!changed) return bytes;
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private Object renameHandle(Object value) {
        if (value instanceof Handle h && h.getTag() != Opcodes.H_INVOKESTATIC && h.getTag() >= Opcodes.H_INVOKEVIRTUAL
                && splitFor(h.getOwner(), h.getName(), h.getDesc()) != null) {
            return new Handle(h.getTag(), h.getOwner(), PREFIX + h.getName(), h.getDesc(), h.isInterface());
        }
        return value;
    }

    private boolean needsBridge(ClassNode node, Split split) {
        if (node.name.equals(split.owner()) || !supertypes(node.name).contains(split.owner())) return false;
        if (mappedName(node.name, split.name(), split.desc()) == null) return false;
        List<String> parents = new ArrayList<>(node.interfaces);
        if (node.superName != null) parents.add(node.superName);
        for (String parent : parents) {
            if (supertypes(parent).contains(split.owner()) && mappedName(parent, split.name(), split.desc()) != null) {
                return false;
            }
        }
        return true;
    }

    private static MethodNode bridge(ClassNode node, Split split) {
        boolean isInterface = (node.access & Opcodes.ACC_INTERFACE) != 0;
        MethodNode bridge = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, PREFIX + split.name(),
                split.desc(), null, null);
        bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        int slot = 1;
        for (Type argument : Type.getArgumentTypes(split.desc())) {
            bridge.instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), slot));
            slot += argument.getSize();
        }
        bridge.instructions.add(new MethodInsnNode(isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL,
                node.name, split.name(), split.desc(), isInterface));
        bridge.instructions.add(new InsnNode(Type.getReturnType(split.desc()).getOpcode(Opcodes.IRETURN)));
        bridge.maxStack = slot;
        bridge.maxLocals = slot;
        return bridge;
    }

    private static Map<String, byte[]> read(Path jar) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (entry.isDirectory()) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    out.put(entry.getName(), in.readAllBytes());
                }
            }
        }
        return out;
    }
}
