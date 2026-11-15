/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.tools.hooks;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class NeoForgeChanges {
    record Change(String owner, String member, String kind, boolean synthetic, Set<String> neoForgeAdded,
                  Set<String> otherAdded, Set<String> removed) {}

    record Result(List<Change> changes, int recompiledOnly) {}

    private static final String NEOFORGE = "net/neoforged/";

    static Result collect(Path patchedJar, Path vanillaJar) throws IOException {
        Map<String, ClassNode> patched = read(patchedJar);
        Map<String, ClassNode> vanilla = read(vanillaJar);
        List<Change> changes = new ArrayList<>();
        int recompiledOnly = 0;
        for (Map.Entry<String, ClassNode> entry : patched.entrySet()) {
            ClassNode now = entry.getValue();
            ClassNode before = vanilla.get(entry.getKey());
            if (before == null) {
                changes.add(new Change(now.name, "", "class+", false, Set.of(), Set.of(), Set.of()));
                continue;
            }
            Set<String> interfaces = new TreeSet<>(now.interfaces);
            interfaces.removeAll(before.interfaces);
            for (String type : interfaces) {
                changes.add(new Change(now.name, type, "implements", false, Set.of(), Set.of(), Set.of()));
            }
            Set<String> oldFields = new TreeSet<>();
            for (FieldNode field : before.fields) oldFields.add(field.name + ":" + field.desc);
            for (FieldNode field : now.fields) {
                if (!oldFields.contains(field.name + ":" + field.desc)) {
                    changes.add(new Change(now.name, field.name + ":" + field.desc, "field+",
                            (field.access & Opcodes.ACC_SYNTHETIC) != 0, Set.of(), Set.of(), Set.of()));
                }
            }
            Map<String, MethodNode> oldMethods = new HashMap<>();
            for (MethodNode method : before.methods) {
                if (!isLambda(method)) oldMethods.put(method.name + method.desc, method);
            }
            Map<MethodNode, MethodNode> pairs = new HashMap<>();
            for (MethodNode method : now.methods) {
                if (!isLambda(method)) pairs.put(method, oldMethods.remove(method.name + method.desc));
            }
            pairLambdas(before, now, pairs);
            for (MethodNode method : now.methods) {
                boolean synthetic = (method.access & Opcodes.ACC_SYNTHETIC) != 0;
                MethodNode old = pairs.get(method);
                List<String> nowBehaviour = behaviour(method);
                if (old == null) {
                    changes.add(new Change(now.name, method.name + method.desc, "method+", synthetic,
                            only(nowBehaviour, List.of(), true), only(nowBehaviour, List.of(), false), Set.of()));
                    continue;
                }
                List<String> oldBehaviour = behaviour(old);
                if (nowBehaviour.equals(oldBehaviour)) {
                    if (method.instructions.size() != old.instructions.size()) recompiledOnly++;
                    continue;
                }
                String member = old.name.equals(method.name)
                        ? method.name + method.desc
                        : old.name + method.desc + " (now " + method.name + ")";
                changes.add(new Change(now.name, member, "method~", synthetic,
                        only(nowBehaviour, oldBehaviour, true), only(nowBehaviour, oldBehaviour, false),
                        missing(oldBehaviour, nowBehaviour)));
            }
            Set<MethodNode> paired = new HashSet<>(pairs.values());
            for (MethodNode method : before.methods) {
                if (!paired.contains(method)) {
                    changes.add(new Change(now.name, method.name + method.desc, "method-",
                            (method.access & Opcodes.ACC_SYNTHETIC) != 0, Set.of(), Set.of(), Set.of()));
                }
            }
        }
        changes.sort((a, b) -> a.owner().equals(b.owner()) ? a.member().compareTo(b.member()) : a.owner().compareTo(b.owner()));
        return new Result(changes, recompiledOnly);
    }

    private static boolean isLambda(MethodNode method) {
        return (method.access & Opcodes.ACC_SYNTHETIC) != 0 && method.name.startsWith("lambda$");
    }

    private static String lambdaKey(MethodNode method) {
        String name = method.name;
        int last = name.lastIndexOf('$');
        return name.substring(0, last) + method.desc + ((method.access & Opcodes.ACC_STATIC) != 0 ? " static" : "");
    }

    private static int lambdaIndex(MethodNode method) {
        try {
            return Integer.parseInt(method.name.substring(method.name.lastIndexOf('$') + 1));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static void pairLambdas(ClassNode before, ClassNode now, Map<MethodNode, MethodNode> pairs) {
        Map<String, List<MethodNode>> oldGroups = new TreeMap<>();
        Map<String, List<MethodNode>> newGroups = new TreeMap<>();
        for (MethodNode method : before.methods) {
            if (isLambda(method)) oldGroups.computeIfAbsent(lambdaKey(method), k -> new ArrayList<>()).add(method);
        }
        for (MethodNode method : now.methods) {
            if (isLambda(method)) newGroups.computeIfAbsent(lambdaKey(method), k -> new ArrayList<>()).add(method);
        }
        for (Map.Entry<String, List<MethodNode>> group : newGroups.entrySet()) {
            List<MethodNode> fresh = new ArrayList<>(group.getValue());
            List<MethodNode> old = new ArrayList<>(oldGroups.getOrDefault(group.getKey(), List.of()));
            fresh.sort(java.util.Comparator.comparingInt(NeoForgeChanges::lambdaIndex));
            old.sort(java.util.Comparator.comparingInt(NeoForgeChanges::lambdaIndex));
            if (fresh.size() != old.size()) {
                for (MethodNode candidate : new ArrayList<>(fresh)) {
                    List<String> shape = behaviour(candidate);
                    for (MethodNode match : old) {
                        if (behaviour(match).equals(shape)) {
                            pairs.put(candidate, match);
                            fresh.remove(candidate);
                            old.remove(match);
                            break;
                        }
                    }
                }
            }
            for (int i = 0; i < fresh.size(); i++) pairs.put(fresh.get(i), i < old.size() ? old.get(i) : null);
        }
    }

    private static Set<String> only(List<String> now, List<String> before, boolean neoForge) {
        Set<String> out = new TreeSet<>();
        Map<String, Integer> remaining = counts(before);
        for (String item : now) {
            if (remaining.merge(item, -1, Integer::sum) >= 0) continue;
            String ref = reference(item);
            if (ref == null) continue;
            if (ref.startsWith(NEOFORGE) == neoForge) out.add(ref);
        }
        return out;
    }

    private static Set<String> missing(List<String> before, List<String> now) {
        Set<String> out = new TreeSet<>();
        Map<String, Integer> remaining = counts(now);
        for (String item : before) {
            if (remaining.merge(item, -1, Integer::sum) >= 0) continue;
            String ref = reference(item);
            if (ref != null) out.add(ref);
        }
        return out;
    }

    private static Map<String, Integer> counts(List<String> items) {
        Map<String, Integer> out = new HashMap<>();
        for (String item : items) out.merge(item, 1, Integer::sum);
        return out;
    }

    private static String reference(String item) {
        if (item.startsWith("call ") || item.startsWith("field ")) return item.substring(item.indexOf(' ') + 1);
        if (item.startsWith("type ")) return item.substring(5);
        if (item.startsWith("const ")) return "const " + item.substring(6);
        return null;
    }

    private static List<String> behaviour(MethodNode method) {
        List<String> out = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            int op = insn.getOpcode();
            if (insn instanceof MethodInsnNode call) {
                out.add("call " + call.owner + "." + (call.name.startsWith("lambda$") ? "lambda" : call.name) + call.desc);
            } else if (insn instanceof FieldInsnNode field) {
                out.add("field " + field.owner + "." + field.name + (op == Opcodes.PUTFIELD || op == Opcodes.PUTSTATIC ? " (set)" : ""));
            } else if (insn instanceof TypeInsnNode type) {
                out.add("type " + type.desc + (op == Opcodes.NEW ? " (new)" : op == Opcodes.CHECKCAST ? " (cast)" : op == Opcodes.INSTANCEOF ? " (instanceof)" : ""));
            } else if (insn instanceof LdcInsnNode ldc) {
                out.add("const " + ldc.cst);
            } else if (insn instanceof IntInsnNode number && op != Opcodes.NEWARRAY) {
                out.add("const " + number.operand);
            } else if (op >= Opcodes.ICONST_M1 && op <= Opcodes.DCONST_1) {
                out.add("const op" + op);
            } else if (insn instanceof InvokeDynamicInsnNode indy) {
                StringBuilder key = new StringBuilder("indy ").append(indy.name).append(indy.desc);
                for (Object arg : indy.bsmArgs) {
                    if (arg instanceof Handle handle) {
                        key.append(' ').append(handle.getOwner()).append('.')
                                .append(handle.getName().startsWith("lambda$") ? "lambda" : handle.getName());
                        if (!handle.getName().startsWith("lambda$")) {
                            out.add("call " + handle.getOwner() + "." + handle.getName() + handle.getDesc());
                        }
                    } else {
                        key.append(' ').append(arg);
                    }
                }
                out.add(key.toString());
            } else if (op == Opcodes.ATHROW || op == Opcodes.MONITORENTER) {
                out.add("op " + op);
            }
        }
        Collections.sort(out);
        return out;
    }

    private static Map<String, ClassNode> read(Path jar) throws IOException {
        Map<String, ClassNode> out = new TreeMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                String name = entry.getName();
                if (!name.endsWith(".class") || !(name.startsWith("net/minecraft/") || name.startsWith("com/mojang/"))) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    ClassNode node = new ClassNode();
                    new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES);
                    out.put(node.name, node);
                }
            }
        }
        return out;
    }
}
