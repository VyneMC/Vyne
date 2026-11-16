/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.tools.hooks;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class FabricApiOnPatches {
    private FabricApiOnPatches() {}

    public static void main(String[] args) throws Exception {
        Path inventory = Path.of(args[0]);
        Path vanillaJar = Path.of(args[1]);
        Path patchedJar = Path.of(args[2]);
        Path out = Path.of(args[3]);

        List<String> rows = Files.readAllLines(inventory, StandardCharsets.UTF_8);
        List<String> report = new ArrayList<>();
        report.add("status\tclass\tmethod\tinjector\tat\tmodule\tmixin\thandler\tvanilla_points\tpatched_points");
        Map<String, Integer> counts = new TreeMap<>();
        Map<String, Integer> brokenByModule = new TreeMap<>();
        java.util.Set<String> broken = new java.util.TreeSet<>();
        try (ZipFile vanilla = new ZipFile(vanillaJar.toFile()); ZipFile patched = new ZipFile(patchedJar.toFile())) {
            for (String row : rows.subList(1, rows.size())) {
                String[] f = row.split("\t", -1);
                if (f.length < 6) continue;
                String owner = f[0], method = f[1], injector = f[2], at = f[3], module = f[4], mixin = f[5];
                String handler = f.length > 6 ? f[6] : "";
                ClassNode before = read(vanilla, owner);
                ClassNode after = read(patched, owner);
                if (after == null) after = before;
                String status;
                int beforePoints = -1, afterPoints = -1;
                if (before == null) {
                    status = "unknown-class";
                } else {
                    List<MethodNode> vanillaMethods = find(before, method);
                    List<MethodNode> patchedMethods = find(after, method);
                    if (vanillaMethods.isEmpty()) {
                        status = "unknown-method";
                    } else if (patchedMethods.isEmpty()) {
                        status = "method-gone";
                    } else if (!at.startsWith("INVOKE") && !at.startsWith("FIELD") && !at.startsWith("NEW")) {
                        status = after == before ? "ok" : "ok-unchanged-point";
                    } else {
                        beforePoints = points(vanillaMethods, at);
                        afterPoints = points(patchedMethods, at);
                        if (beforePoints <= 0) status = "unchecked-point";
                        else if (afterPoints == 0 && beforePoints > 0) status = "point-gone";
                        else if (afterPoints != beforePoints) status = "point-moved";
                        else status = "ok";
                    }
                }
                counts.merge(status, 1, Integer::sum);
                if (status.equals("method-gone") || status.equals("point-gone") || status.equals("point-moved")) {
                    brokenByModule.merge(module, 1, Integer::sum);
                    if (!handler.isEmpty()) broken.add(mixin + " " + handler);
                }
                report.add(String.join("\t", status, owner, method, injector, at, module, mixin, handler,
                        String.valueOf(beforePoints), String.valueOf(afterPoints)));
            }
        }
        Files.createDirectories(out.getParent());
        Files.write(out, report, StandardCharsets.UTF_8);
        System.out.println("Fabric API injections checked against NeoForge's patched classes: " + (report.size() - 1));
        counts.forEach((k, v) -> System.out.println("  " + k + ": " + v));
        System.out.println("Affected Fabric API modules (method-gone, point-gone, point-moved):");
        brokenByModule.forEach((k, v) -> System.out.println("  " + k + ": " + v));
        System.out.println("Report: " + out);
        if (args.length > 4) writeTable(Path.of(args[4]), broken);
    }

    private static void writeTable(Path table, java.util.Set<String> broken) throws IOException {
        Map<String, String> entries = new TreeMap<>();
        if (Files.isRegularFile(table)) {
            for (String line : Files.readAllLines(table, StandardCharsets.UTF_8)) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2) entries.put(parts[0] + " " + parts[1], parts.length > 2 ? parts[2] : "replaced");
            }
        }
        int added = 0;
        for (String key : broken) {
            if (entries.containsKey(key.substring(0, key.indexOf(' ')) + " *")) continue;
            if (entries.putIfAbsent(key, "disabled") == null) added++;
        }
        List<String> lines = new ArrayList<>();
        entries.forEach((k, v) -> lines.add(k + " " + v));
        Files.write(table, lines, StandardCharsets.UTF_8);
        long replaced = entries.values().stream().filter("replaced"::equals).count();
        System.out.println("Patch-layer table: " + entries.size() + " handlers (" + replaced + " replaced, "
                + (entries.size() - replaced) + " disabled; " + added + " new) -> " + table);
    }

    private static ClassNode read(ZipFile jar, String owner) throws IOException {
        ZipEntry entry = jar.getEntry(owner + ".class");
        if (entry == null) return null;
        try (InputStream in = jar.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static List<MethodNode> find(ClassNode node, String selector) {
        String name = selector;
        String desc = null;
        int paren = selector.indexOf('(');
        if (paren >= 0) {
            name = selector.substring(0, paren);
            desc = selector.substring(paren);
        }
        List<MethodNode> found = new ArrayList<>();
        for (MethodNode m : node.methods) {
            if (!m.name.equals(name)) continue;
            if (desc != null && !m.desc.equals(desc)) continue;
            found.add(m);
        }
        return found;
    }

    private static int points(List<MethodNode> methods, String at) {
        String[] parts = at.split(" ", 2);
        if (parts.length < 2) return -1;
        String kind = parts[0];
        String target = parts[1];
        if (kind.equals("NEW") && target.startsWith("(")) {
            target = target.substring(target.indexOf(')') + 2, target.length() - 1);
        }
        int count = 0;
        for (MethodNode m : methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (kind.startsWith("INVOKE") && insn instanceof MethodInsnNode call) {
                    if (target.equals(call.owner + "." + call.name + call.desc)) count++;
                } else if (kind.equals("FIELD") && insn instanceof FieldInsnNode field) {
                    if (target.equals(field.owner + "." + field.name + ":" + field.desc)
                            || target.equals(field.owner + "." + field.name)) count++;
                } else if (kind.equals("NEW") && insn instanceof TypeInsnNode type) {
                    if (insn.getOpcode() == org.objectweb.asm.Opcodes.NEW && target.equals(type.desc)) count++;
                }
            }
        }
        return count;
    }
}
