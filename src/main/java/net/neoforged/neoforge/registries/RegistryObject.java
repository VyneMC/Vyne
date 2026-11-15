/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.registries;

import dev.stolym.vyne.registry.VyneGenericRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class RegistryObject<T> extends DeferredHolder<T> {
    public RegistryObject(Object key, String namespace, String name, Supplier<T> supplier) {
        super(key, namespace, name, supplier);
    }

    public static <T> RegistryObject<T> create(ResourceLocation id, Object key, String modId) {
        String ns = id != null ? id.getNamespace() : modId;
        String path = id != null ? id.getPath() : "unknown";
        return new RegistryObject<>(key, ns, path, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get() {
        T value = super.get();
        if (value != null) return value;

        ResourceLocation id = getId();
        if (id == null) return null;

        String registryPath = inferRegistryPath(getKey());
        if (registryPath == null) return null;

        Object resolved = VyneGenericRegistry.get(registryPath, id.toString());
        return (T) resolved;
    }

    public boolean isPresent() {
        return get() != null;
    }

    public void ifPresent(Consumer<? super T> consumer) {
        T value = get();
        if (value != null) consumer.accept(value);
    }

    private static String inferRegistryPath(net.minecraft.resources.ResourceKey<?> elementKey) {
        if (elementKey == null) return null;
        try {
            net.minecraft.resources.ResourceLocation reg = elementKey.registry();
            if (reg != null) {
                return reg.getNamespace() + ":" + reg.getPath();
            }
        } catch (Throwable ignored) {
        }
        String s = elementKey.toString().toLowerCase();
        if (s.contains("minecraft:item")) return "minecraft:item";
        if (s.contains("minecraft:block")) return "minecraft:block";
        if (s.contains("minecraft:fluid")) return "minecraft:fluid";
        if (s.contains("entity_type")) return "minecraft:entity_type";
        if (s.contains("sound_event")) return "minecraft:sound_event";
        if (s.contains("mob_effect")) return "minecraft:mob_effect";
        return null;
    }
}
