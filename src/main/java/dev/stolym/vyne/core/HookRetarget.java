/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.core;

import dev.stolym.vyne.util.VyneLogger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class HookRetarget {
    private static final Map<String, Integer> CALL_SITE = Map.of(
            "Lorg/spongepowered/asm/mixin/injection/Redirect;", 0,
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;", 1,
            "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;", 0,
            "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;", 0,
            "Lorg/spongepowered/asm/mixin/injection/ModifyArg;", -1,
            "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;", -2);
    private static final Map<String, ClassNode> TARGETS = new ConcurrentHashMap<>();
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();

    private HookRetarget() {}

    private record Call(String owner, String name, String desc) {}

    static void retarget(ClassNode mixin) {
        List<String> targets = mixinTargets(mixin);
        if (targets.isEmpty()) return;
        Object config = configFor(mixin.name);
        if (config == null) return;
        String mod = modId(config);
        for (MethodNode handler : mixin.methods) {
            for (AnnotationNode annotation : annotations(handler)) {
                Integer extra = CALL_SITE.get(annotation.desc);
                if (extra == null) continue;
                Call call = call(annotation, mixin.name, config);
                if (call == null) continue;
                List<String> selectors = stringList(value(annotation, "method"));
                if (selectors.isEmpty() || selectors.stream().anyMatch(s -> s.startsWith("/"))) continue;
                List<String> resolved = new ArrayList<>();
                for (String selector : selectors) resolved.add(remap(config, mixin.name, selector));
                for (String targetName : targets) {
                    ClassNode target = target(remap(config, mixin.name, targetName));
                    if (target == null) continue;
                    List<String> added = moved(target, resolved, call, handler, extra);
                    if (added.isEmpty()) continue;
                    addSelectors(annotation, added);
                    VyneLogger.info("[PatchLayer] " + mod + ": hook " + handler.name + " follows its call into " + String.join(", ", added)
                            + " of " + target.name.replace('/', '.'));
                }
            }
        }
    }

    private static List<String> moved(ClassNode target, List<String> selectors, Call call, MethodNode handler, int extra) {
        List<MethodNode> selected = new ArrayList<>();
        for (MethodNode method : target.methods) {
            for (String selector : selectors) {
                if (matches(method, selector)) selected.add(method);
            }
        }
        for (MethodNode method : selected) {
            if (contains(method, call) != null) return List.of();
        }
        boolean renamedLambda = selected.isEmpty() && selectors.stream().anyMatch(s -> s.startsWith("lambda$") || s.contains(";lambda$"));
        if (selected.isEmpty() && !renamedLambda) return List.of();
        boolean handlerStatic = (handler.access & Opcodes.ACC_STATIC) != 0;
        List<String> found = new ArrayList<>();
        for (MethodNode method : target.methods) {
            if (selected.contains(method) || method.instructions == null || method.instructions.size() == 0) continue;
            if (renamedLambda && selectors.stream().noneMatch(sel -> method.name.startsWith(lambdaPrefix(sel)))) continue;
            MethodInsnNode insn = contains(method, call);
            if (insn == null) continue;
            if (((method.access & Opcodes.ACC_STATIC) != 0) != handlerStatic) continue;
            if (!handlerFits(handler, insn, extra)) continue;
            found.add(method.name + method.desc);
        }
        return found.size() <= 4 ? found : List.of();
    }

    private static String lambdaPrefix(String selector) {
        String s = selector.contains(";") ? selector.substring(selector.indexOf(';') + 1) : selector;
        if (!s.startsWith("lambda$")) return "\u0000";
        int second = s.indexOf('$', "lambda$".length());
        return second > 0 ? s.substring(0, second + 1) : "\u0000";
    }

    private static boolean handlerFits(MethodNode handler, MethodInsnNode insn, int extra) {
        int handlerParams = Type.getArgumentTypes(handler.desc).length;
        int callArgs = Type.getArgumentTypes(insn.desc).length + (insn.getOpcode() == Opcodes.INVOKESTATIC ? 0 : 1);
        return switch (extra) {
            case -2 -> handlerParams == 1;
            case -1 -> handlerParams == 1 || handlerParams == Type.getArgumentTypes(insn.desc).length;
            default -> handlerParams == callArgs + extra;
        };
    }

    private static MethodInsnNode contains(MethodNode method, Call call) {
        if (method.instructions == null) return null;
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode m && m.name.equals(call.name()) && m.desc.equals(call.desc())
                    && (call.owner() == null || m.owner.equals(call.owner()))) {
                return m;
            }
        }
        return null;
    }

    private static boolean matches(MethodNode method, String selector) {
        String s = selector;
        int semicolon = s.indexOf(';');
        int paren = s.indexOf('(');
        if (s.startsWith("L") && semicolon > 0 && (paren < 0 || semicolon < paren)) s = s.substring(semicolon + 1);
        paren = s.indexOf('(');
        String name = paren >= 0 ? s.substring(0, paren) : s;
        String desc = paren >= 0 ? s.substring(paren) : null;
        if (name.endsWith("*")) return method.name.startsWith(name.substring(0, name.length() - 1));
        return method.name.equals(name) && (desc == null || method.desc.equals(desc));
    }

    private static Call call(AnnotationNode annotation, String mixin, Object config) {
        List<AnnotationNode> ats = new ArrayList<>();
        Object at = value(annotation, "at");
        if (at instanceof AnnotationNode node) ats.add(node);
        else if (at instanceof List<?> list) for (Object o : list) if (o instanceof AnnotationNode node) ats.add(node);
        for (AnnotationNode node : ats) {
            if (!"INVOKE".equals(value(node, "value"))) continue;
            Object target = value(node, "target");
            if (!(target instanceof String raw)) continue;
            String t = remap(config, mixin, raw);
            int paren = t.indexOf('(');
            if (paren < 0) return null;
            String head = t.substring(0, paren);
            String desc = t.substring(paren);
            String owner = null;
            String name = head;
            if (head.startsWith("L") && head.contains(";")) {
                owner = head.substring(1, head.indexOf(';'));
                name = head.substring(head.indexOf(';') + 1);
            } else if (head.contains(".")) {
                owner = head.substring(0, head.lastIndexOf('.')).replace('.', '/');
                name = head.substring(head.lastIndexOf('.') + 1);
            }
            return new Call(owner, name, desc);
        }
        return null;
    }

    private static List<String> mixinTargets(ClassNode mixin) {
        List<String> out = new ArrayList<>();
        for (AnnotationNode annotation : classAnnotations(mixin)) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
            Object types = value(annotation, "value");
            if (types instanceof List<?> list) for (Object o : list) if (o instanceof Type type) out.add(type.getInternalName());
            for (String name : stringList(value(annotation, "targets"))) out.add(name.replace('.', '/'));
        }
        out.removeIf(name -> !name.startsWith("net/minecraft/"));
        return out;
    }

    private static ClassNode target(String internalName) {
        String name = internalName.replace('.', '/');
        if (MISSING.contains(name)) return null;
        ClassNode cached = TARGETS.get(name);
        if (cached != null) return cached;
        try {
            ClassNode node = org.spongepowered.asm.service.MixinService.getService().getBytecodeProvider().getClassNode(name.replace('/', '.'));
            TARGETS.put(name, node);
            return node;
        } catch (Exception e) {
            MISSING.add(name);
            return null;
        }
    }

    private static Object configFor(String mixinInternalName) {
        String dotted = mixinInternalName.replace('/', '.');
        Object best = null;
        int bestLength = -1;
        for (Object config : configs()) {
            String pkg = ((org.spongepowered.asm.mixin.extensibility.IMixinConfig) config).getMixinPackage();
            if (pkg != null && !pkg.isEmpty() && dotted.startsWith(pkg) && pkg.length() > bestLength) {
                best = config;
                bestLength = pkg.length();
            }
        }
        return best;
    }

    private static List<Object> configs() {
        List<Object> out = new ArrayList<>();
        try {
            Object transformer = org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            if (transformer == null) return out;
            java.lang.reflect.Field processorField = transformer.getClass().getDeclaredField("processor");
            processorField.setAccessible(true);
            Object processor = processorField.get(transformer);
            for (String name : new String[] {"configs", "pendingConfigs"}) {
                java.lang.reflect.Field field = processor.getClass().getDeclaredField(name);
                field.setAccessible(true);
                out.addAll((List<?>) field.get(processor));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            VyneLogger.debug("[PatchLayer] Mixin configs unavailable for hook retargeting: " + e);
        }
        return out;
    }

    private static String modId(Object config) {
        try {
            Method decoration = config.getClass().getMethod("getDecoration", String.class);
            decoration.setAccessible(true);
            Object id = decoration.invoke(config, "fabric-modId");
            if (id != null) return id.toString();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return ((org.spongepowered.asm.mixin.extensibility.IMixinConfig) config).getName();
    }

    private static String remap(Object config, String mixin, String value) {
        if (config == null || value == null) return value;
        try {
            Method getter = config.getClass().getMethod("getReferenceMapper");
            getter.setAccessible(true);
            Object mapper = getter.invoke(config);
            if (mapper == null) return value;
            Method remap = mapper.getClass().getMethod("remap", String.class, String.class);
            remap.setAccessible(true);
            Object mapped = remap.invoke(mapper, mixin, value);
            return mapped instanceof String s ? s : value;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return value;
        }
    }

    @SuppressWarnings("unchecked")
    private static void addSelectors(AnnotationNode annotation, List<String> added) {
        int index = annotation.values.indexOf("method");
        Object current = annotation.values.get(index + 1);
        List<Object> list = current instanceof List<?> l ? new ArrayList<>((List<Object>) l) : new ArrayList<>(List.of(current));
        list.addAll(added);
        annotation.values.set(index + 1, list);
    }

    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> out = new ArrayList<>();
        if (method.visibleAnnotations != null) out.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) out.addAll(method.invisibleAnnotations);
        return out;
    }

    private static List<AnnotationNode> classAnnotations(ClassNode node) {
        List<AnnotationNode> out = new ArrayList<>();
        if (node.visibleAnnotations != null) out.addAll(node.visibleAnnotations);
        if (node.invisibleAnnotations != null) out.addAll(node.invisibleAnnotations);
        return out;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) return null;
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static List<String> stringList(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof String s) out.add(s);
        else if (value instanceof List<?> list) for (Object o : list) if (o instanceof String s) out.add(s);
        return out;
    }
}
