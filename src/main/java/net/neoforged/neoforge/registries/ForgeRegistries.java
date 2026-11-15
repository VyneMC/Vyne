/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.internal.VyneRl;

import java.lang.reflect.Field;

public final class ForgeRegistries {
    public static final IForgeRegistry<?> BLOCKS = wrap("minecraft:block");
    public static final IForgeRegistry<?> ITEMS = wrap("minecraft:item");
    public static final IForgeRegistry<?> FLUIDS = wrap("minecraft:fluid");
    public static final IForgeRegistry<?> ENTITY_TYPES = wrap("minecraft:entity_type");
    public static final IForgeRegistry<?> SOUND_EVENTS = wrap("minecraft:sound_event");
    public static final IForgeRegistry<?> MOB_EFFECTS = wrap("minecraft:mob_effect");
    public static final DeferredRegister.RegistryHolder<?> ENTITY_DATA_SERIALIZERS = holder("neoforge:entity_data_serializers");
    public static final DeferredRegister.RegistryHolder<?> GLOBAL_LOOT_MODIFIER_SERIALIZERS = holder("neoforge:global_loot_modifier_serializers");
    public static final DeferredRegister.RegistryHolder<?> BIOME_MODIFIER_SERIALIZERS = holder("neoforge:biome_modifier_serializers");
    public static final DeferredRegister.RegistryHolder<?> STRUCTURE_MODIFIER_SERIALIZERS = holder("neoforge:structure_modifier_serializers");
    public static final DeferredRegister.RegistryHolder<?> HOLDER_SET_TYPES = holder("neoforge:holder_set_type");
    public static final DeferredRegister.RegistryHolder<?> INGREDIENT_TYPES = holder("neoforge:ingredient_serializer");
    public static final DeferredRegister.RegistryHolder<?> FLUID_INGREDIENT_TYPES = holder("neoforge:fluid_ingredient_type");
    public static final DeferredRegister.RegistryHolder<?> CONDITION_CODECS = holder("neoforge:condition_codecs");
    public static final DeferredRegister.RegistryHolder<?> ATTACHMENT_TYPES = holder("neoforge:attachment_types");
    public static final DeferredRegister.RegistryHolder<?> BIOME_MODIFIERS = holder("neoforge:biome_modifier");
    public static final DeferredRegister.RegistryHolder<?> STRUCTURE_MODIFIERS = holder("neoforge:structure_modifier");

    public static final class Keys {
        public static final ResourceKey<Registry<?>> BLOCKS = key("minecraft:block");
        public static final ResourceKey<Registry<?>> ITEMS = key("minecraft:item");
        public static final ResourceKey<Registry<?>> FLUIDS = key("minecraft:fluid");
        public static final ResourceKey<Registry<?>> ENTITY_TYPES = key("minecraft:entity_type");
        public static final ResourceKey<Registry<?>> SOUND_EVENTS = key("minecraft:sound_event");
        public static final ResourceKey<Registry<?>> MOB_EFFECTS = key("minecraft:mob_effect");
        public static final ResourceKey<Registry<?>> FLUID_TYPES =
            (ResourceKey<Registry<?>>) (Object) NeoForgeRegistries.Keys.FLUID_TYPES;

        private static ResourceKey<Registry<?>> key(String id) {
            return (ResourceKey<Registry<?>>) (Object) ResourceKey.createRegistryKey(VyneRl.fromColonFormRl(id));
        }
    }

    private static Registry<?> lookupBuiltInRegistry(String registryId) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            String builtInName = dev.stolym.vyne.mappings.MappingRegistry.isReady()
                ? dev.stolym.vyne.mappings.MappingRegistry.get()
                    .mapClass("net/minecraft/core/registries/BuiltInRegistries")
                    .replace('/', '.')
                : "net.minecraft.class_7923";
            Class<?> builtIn = Class.forName(builtInName, true, cl);
            for (Field f : builtIn.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object candidate = f.get(null);
                if (!(candidate instanceof Registry<?> registry)) continue;
                if (matchesRegistry(registry, registryId)) return registry;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> IForgeRegistry<T> wrap(String registryId) {
        ResourceKey<? extends Registry<T>> regKey = (ResourceKey<? extends Registry<T>>) (Object)
            ResourceKey.createRegistryKey(VyneRl.fromColonFormRl(registryId));
        return new SimpleForgeRegistry<>(regKey, registryId, lookupBuiltInRegistry(registryId));
    }

    private static DeferredRegister.RegistryHolder<?> holder(String key) {
        return DeferredRegister.createRegistryHolder(VyneRl.fromColonFormRl(key), "neoforge");
    }

    private static boolean matchesRegistry(Registry<?> registry, String id) {
        try {
            Object key = registry.key();
            if (key != null && key.toString().contains(id)) return true;
        } catch (Throwable ignored) {}
        return registry.toString().contains(id);
    }

    private static final class SimpleForgeRegistry<T> extends VyneRegistryProxy<T> implements IForgeRegistry<T> {
        private SimpleForgeRegistry(ResourceKey<? extends Registry<T>> regKey, String registryPath,
                Registry<?> live) {
            super(regKey, registryPath, live);
        }

        @Override
        public T getValue(ResourceLocation id) {
            return getOptional(id).orElse(null);
        }

        @Override
        public ResourceLocation getKey(T value) {
            return getId(value).orElse(null);
        }
    }

    private ForgeRegistries() {}
}
