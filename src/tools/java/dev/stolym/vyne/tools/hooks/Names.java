/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.tools.hooks;

import net.fabricmc.mappingio.tree.MappingTree;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;

import java.util.HashMap;
import java.util.Map;

final class Names {
    record Method(String name, String desc) {}

    private final Map<String, String> classes = new HashMap<>();
    private final Map<String, Map<String, Method>> methodsBySignature = new HashMap<>();
    private final Map<String, Map<String, Method>> methodsByName = new HashMap<>();
    private final Map<String, String> methodNamesGlobal = new HashMap<>();
    private final Remapper descriptors = new Remapper() {
        @Override
        public String map(String internalName) {
            return classes.getOrDefault(internalName, internalName);
        }
    };

    Names(MappingTree tree) {
        int intermediary = tree.getNamespaceId("intermediary");
        int named = tree.getNamespaceId("named");
        for (MappingTree.ClassMapping type : tree.getClasses()) {
            String from = type.getName(intermediary);
            String to = type.getName(named);
            if (from != null && to != null) classes.put(from, to);
        }
        for (MappingTree.ClassMapping type : tree.getClasses()) {
            String owner = type.getName(intermediary);
            if (owner == null) continue;
            Map<String, Method> bySignature = new HashMap<>();
            Map<String, Method> byName = new HashMap<>();
            for (MappingTree.MethodMapping method : type.getMethods()) {
                String name = method.getName(intermediary);
                String desc = method.getDesc(intermediary);
                String target = method.getName(named);
                if (name == null || desc == null || target == null) continue;
                Method mapped = new Method(target, method.getDesc(named));
                bySignature.put(name + desc, mapped);
                if (name.startsWith("method_")) methodNamesGlobal.putIfAbsent(name, target);
                byName.putIfAbsent(name, mapped);
            }
            methodsBySignature.put(owner, bySignature);
            methodsByName.put(owner, byName);
        }
    }

    String type(String intermediary) {
        return classes.getOrDefault(intermediary, intermediary);
    }

    String desc(String desc) {
        if (desc == null || desc.isEmpty()) return desc;
        return desc.startsWith("(") ? descriptors.mapMethodDesc(desc) : descriptors.mapDesc(desc);
    }

    Method method(String owner, String name, String desc) {
        if (desc != null && !desc.isEmpty()) {
            Method found = methodsBySignature.getOrDefault(owner, Map.of()).get(name + desc);
            if (found != null) return found;
        } else {
            Method found = methodsByName.getOrDefault(owner, Map.of()).get(name);
            if (found != null) return found;
        }
        String global = methodNamesGlobal.get(name);
        return new Method(global != null ? global : name, desc(desc));
    }

    static String internal(String descriptorOrName) {
        if (descriptorOrName.startsWith("L") && descriptorOrName.endsWith(";")) {
            return Type.getType(descriptorOrName).getInternalName();
        }
        return descriptorOrName.replace('.', '/');
    }
}
