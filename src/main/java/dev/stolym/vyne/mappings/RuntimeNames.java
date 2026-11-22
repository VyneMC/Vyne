/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mappings;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RuntimeNames {
    private RuntimeNames() {}

    public static String field(Class<?> owner, String name) {
        if (owner == null || name == null || !MappingRegistry.isReady()) return name;
        MappingComposer.ComposedMappings mappings = MappingRegistry.get();
        Map<String, String> fields = mappings.fieldMap().get(mojangName(mappings, owner));
        String mapped = fields != null ? fields.get(name) : null;
        return mapped != null && !declaresField(owner, name) ? mapped : name;
    }

    public static String method(Class<?> owner, String name, Class<?>... parameters) {
        if (owner == null || name == null || parameters == null || !MappingRegistry.isReady()) return name;
        if (declaresMethod(owner, name, parameters)) return name;
        MappingComposer.ComposedMappings mappings = MappingRegistry.get();
        StringBuilder key = new StringBuilder(name).append('(');
        for (Class<?> parameter : parameters) key.append(descriptor(mappings, parameter));
        String prefix = key.append(')').toString();

        Set<Class<?>> seen = new HashSet<>();
        ArrayDeque<Class<?>> types = new ArrayDeque<>(List.of(owner));
        while (!types.isEmpty()) {
            Class<?> type = types.poll();
            if (!seen.add(type)) continue;
            Map<String, String> methods = mappings.methodMap().get(mojangName(mappings, type));
            if (methods != null) {
                for (Map.Entry<String, String> method : methods.entrySet()) {
                    if (method.getKey().startsWith(prefix) && declaresMethod(owner, method.getValue(), parameters)) {
                        return method.getValue();
                    }
                }
            }
            if (type.getSuperclass() != null) types.add(type.getSuperclass());
            types.addAll(List.of(type.getInterfaces()));
        }
        return name;
    }

    private static String mojangName(MappingComposer.ComposedMappings mappings, Class<?> type) {
        return mappings.unmapClass(type.getName().replace('.', '/'));
    }

    private static String descriptor(MappingComposer.ComposedMappings mappings, Class<?> type) {
        if (type.isArray()) return "[" + descriptor(mappings, type.getComponentType());
        if (type.isPrimitive()) return type.descriptorString();
        return "L" + mojangName(mappings, type) + ";";
    }

    private static boolean declaresField(Class<?> owner, String name) {
        try {
            owner.getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private static boolean declaresMethod(Class<?> owner, String name, Class<?>[] parameters) {
        try {
            owner.getDeclaredMethod(name, parameters);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
