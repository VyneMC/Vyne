/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.registries;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.fluids.FluidType;
import dev.stolym.vyne.fluid.VyneFluidTypeRegistry;
import net.neoforged.neoforge.internal.VyneRl;

import java.util.*;
import java.util.stream.Stream;
import java.lang.reflect.Field;

public class NeoForgeRegistries {
    public static final class Keys {
        public static final net.minecraft.class_5321 FLUID_TYPES = resolveFluidTypesKey();
        public static final ResourceKey<?> ENTITY_DATA_SERIALIZERS =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("neoforge", "entity_data_serializers"));
        public static final ResourceKey<?> GLOBAL_LOOT_MODIFIER_SERIALIZERS =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("neoforge", "global_loot_modifier_serializers"));
        public static final ResourceKey<?> BIOME_MODIFIER_SERIALIZERS =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("neoforge", "biome_modifier_serializers"));
        public static final ResourceKey<?> STRUCTURE_MODIFIER_SERIALIZERS =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("neoforge", "structure_modifier_serializers"));
        public static final ResourceKey<?> HOLDER_SET_TYPES =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("neoforge", "holder_set_type"));
        public static final ResourceKey<?> INGREDIENT_TYPES =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("neoforge", "ingredient_serializer"));
        public static final ResourceKey<?> FLUID_INGREDIENT_TYPES =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("neoforge", "fluid_ingredient_type"));
        public static final ResourceKey<?> CONDITION_CODECS =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("neoforge", "condition_codecs"));
        public static final ResourceKey<?> ATTACHMENT_TYPES =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("neoforge", "attachment_types"));
        public static final ResourceKey<?> BIOME_MODIFIERS =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("neoforge", "biome_modifier"));
        public static final ResourceKey<?> STRUCTURE_MODIFIERS =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("neoforge", "structure_modifier"));

        private static net.minecraft.class_5321 resolveFluidTypesKey() {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = NeoForgeRegistries.class.getClassLoader();
            }
            for (String candidate : new String[]{
                "net.neoforged.neoforge.registries.NeoForgeRegistries$Keys",
                "net.neoforged.neoforge.registries.NeoForgeRegistries",
                "net.minecraftforge.registries.ForgeRegistries$Keys"
            }) {
                try {
                    Class<?> cls = Class.forName(candidate, false, cl);
                    for (Field f : cls.getDeclaredFields()) {
                        if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        f.setAccessible(true);
                        Object val = f.get(null);
                        if (val == null) continue;
                        String s = val.toString().toLowerCase();
                        if (s.contains("fluid_type") || s.contains("fluidtype")) {
                            if (val instanceof net.minecraft.class_5321 rk) return rk;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            return net.minecraft.resources.ResourceKey.createRegistryKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("neoforge", "fluid_type"));
        }

        static {
            String prefix = Keys.class.getName() + "#";
            dev.stolym.vyne.vcl.VyneFieldRegistry.register(prefix + "FLUID_TYPES", FLUID_TYPES);
            dev.stolym.vyne.vcl.VyneFieldRegistry.register(prefix + "ENTITY_DATA_SERIALIZERS", ENTITY_DATA_SERIALIZERS);
            dev.stolym.vyne.vcl.VyneFieldRegistry.register(prefix + "GLOBAL_LOOT_MODIFIER_SERIALIZERS", GLOBAL_LOOT_MODIFIER_SERIALIZERS);
            dev.stolym.vyne.vcl.VyneFieldRegistry.register(prefix + "BIOME_MODIFIER_SERIALIZERS", BIOME_MODIFIER_SERIALIZERS);
            dev.stolym.vyne.vcl.VyneFieldRegistry.register(prefix + "STRUCTURE_MODIFIER_SERIALIZERS", STRUCTURE_MODIFIER_SERIALIZERS);
            dev.stolym.vyne.vcl.VyneFieldRegistry.register(prefix + "HOLDER_SET_TYPES", HOLDER_SET_TYPES);
            dev.stolym.vyne.vcl.VyneFieldRegistry.register(prefix + "INGREDIENT_TYPES", INGREDIENT_TYPES);
            dev.stolym.vyne.vcl.VyneFieldRegistry.register(prefix + "FLUID_INGREDIENT_TYPES", FLUID_INGREDIENT_TYPES);
            dev.stolym.vyne.vcl.VyneFieldRegistry.register(prefix + "CONDITION_CODECS", CONDITION_CODECS);
            dev.stolym.vyne.vcl.VyneFieldRegistry.register(prefix + "ATTACHMENT_TYPES", ATTACHMENT_TYPES);
            dev.stolym.vyne.vcl.VyneFieldRegistry.register(prefix + "BIOME_MODIFIERS", BIOME_MODIFIERS);
            dev.stolym.vyne.vcl.VyneFieldRegistry.register(prefix + "STRUCTURE_MODIFIERS", STRUCTURE_MODIFIERS);
        }
    }

    private static final VyneFluidTypeRegistryShim FLUID_TYPES_SHIM = new VyneFluidTypeRegistryShim();
    public static final net.minecraft.core.Registry<FluidType> FLUID_TYPES = FLUID_TYPES_SHIM;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static net.minecraft.core.Registry<?> neoReg(net.minecraft.resources.ResourceKey<?> key,
                                                          String vynePath) {
        return new VyneRegistryProxy<>(
            (net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<Object>>)
                (Object) key,
            vynePath);
    }

    public static final net.minecraft.core.Registry<?> ENTITY_DATA_SERIALIZERS =
        neoReg(Keys.ENTITY_DATA_SERIALIZERS, "neoforge/entity_data_serializers");
    public static final net.minecraft.core.Registry<?> GLOBAL_LOOT_MODIFIER_SERIALIZERS =
        neoReg(Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "neoforge/global_loot_modifier_serializers");
    public static final net.minecraft.core.Registry<?> BIOME_MODIFIER_SERIALIZERS =
        neoReg(Keys.BIOME_MODIFIER_SERIALIZERS, "neoforge/biome_modifier_serializers");
    public static final net.minecraft.core.Registry<?> STRUCTURE_MODIFIER_SERIALIZERS =
        neoReg(Keys.STRUCTURE_MODIFIER_SERIALIZERS, "neoforge/structure_modifier_serializers");
    public static final net.minecraft.core.Registry<?> HOLDER_SET_TYPES =
        neoReg(Keys.HOLDER_SET_TYPES, "neoforge/holder_set_type");
    public static final net.minecraft.core.Registry<?> INGREDIENT_TYPES =
        neoReg(Keys.INGREDIENT_TYPES, "neoforge/ingredient_serializer");
    public static final net.minecraft.core.Registry<?> FLUID_INGREDIENT_TYPES =
        neoReg(Keys.FLUID_INGREDIENT_TYPES, "neoforge/fluid_ingredient_type");
    public static final net.minecraft.core.Registry<?> CONDITION_CODECS =
        neoReg(Keys.CONDITION_CODECS, "neoforge/condition_codecs");
    public static final net.minecraft.core.Registry<?> ATTACHMENT_TYPES =
        neoReg(Keys.ATTACHMENT_TYPES, "neoforge/attachment_types");
    public static final net.minecraft.core.Registry<?> BIOME_MODIFIERS =
        neoReg(Keys.BIOME_MODIFIERS, "neoforge/biome_modifier");
    public static final net.minecraft.core.Registry<?> STRUCTURE_MODIFIERS =
        neoReg(Keys.STRUCTURE_MODIFIERS, "neoforge/structure_modifier");

    public static VyneFluidTypeRegistryShim getFluidTypeRegistry() {
        return FLUID_TYPES_SHIM;
    }

    public static class VyneFluidTypeRegistryShim implements net.minecraft.core.Registry<FluidType> {
        public VyneFluidTypeRegistryShim() {}

        @Override
        public void register(ResourceKey<FluidType> key, FluidType value) {
            if (key == null || value == null) {
                return;
            }
            fluidEntries.put(key, value);
            fluidLocationEntries.put(key.location(), value);
        }

        @Override
        public void register(ResourceLocation id, FluidType value) {
            if (id == null || value == null) {
                return;
            }
            ResourceKey<FluidType> fk = ResourceKey.create(
                (ResourceKey<net.minecraft.core.Registry<FluidType>>) (Object) Keys.FLUID_TYPES, id);
            fluidEntries.put(fk, value);
            fluidLocationEntries.put(id, value);
        }

        private final Map<ResourceKey<FluidType>, FluidType> fluidEntries = new HashMap<>();
        private final Map<ResourceLocation, FluidType> fluidLocationEntries = new HashMap<>();

        public ResourceLocation getKey(FluidType value) {
            String keyStr = VyneFluidTypeRegistry.getKey(value);
            return keyStr != null ? VyneRl.fromColonFormRl(keyStr) : null;
        }

        public FluidType get(ResourceLocation name) {
            return (FluidType) VyneFluidTypeRegistry.get(name);
        }

        public ResourceLocation getKey(Object value) {
            if (value instanceof FluidType) return getKey((FluidType) value);
            return null;
        }

        public FluidType get(ResourceKey<FluidType> key) {
            return fluidEntries.get(key);
        }

        public Optional<ResourceKey<FluidType>> getResourceKey(FluidType value) {
            for (Map.Entry<ResourceKey<FluidType>, FluidType> e : fluidEntries.entrySet()) {
                if (e.getValue().equals(value)) return Optional.of(e.getKey());
            }
            return Optional.empty();
        }

        public ResourceKey<net.minecraft.core.Registry<FluidType>> key() {
            return (ResourceKey<net.minecraft.core.Registry<FluidType>>) (Object) Keys.FLUID_TYPES;
        }

        public Codec<FluidType> byNameCodec() {
            return null;
        }

        public Codec<Object> holderByNameCodec() {
            return null;
        }

        public Codec<Holder<FluidType>> holderByNameCodecTyped() {
            return null;
        }

        public Object referenceHolderWithLifecycle() {
            return null;
        }

        public Object safeCastToReference(Object holder) {
            return holder;
        }

        public int getRawId(FluidType value) {
            return method_56158(value);
        }

        @Override
        public Optional<ResourceLocation> getId(FluidType value) {
            for (Map.Entry<ResourceLocation, FluidType> e : fluidLocationEntries.entrySet()) {
                if (e.getValue() == value) return Optional.of(e.getKey());
            }
            return Optional.empty();
        }

        public int method_10206(Object value) {
            if (value instanceof FluidType) return getRawId((FluidType) value);
            return -1;
        }

        @Override
        public FluidType byId(int id) {
            int cur = 0;
            for (FluidType v : fluidEntries.values()) {
                if (cur == id) {
                    return v;
                }
                cur++;
            }
            return null;
        }

        public Object method_39974(int id) {
            return byId(id);
        }

        public int method_56158(Object value) {
            int index = 0;
            for (Map.Entry<ResourceKey<FluidType>, FluidType> e : fluidEntries.entrySet()) {
                if (e.getValue().equals(value)) return index;
                index++;
            }
            return -1;
        }

        @Override
        public int size() {
            return fluidEntries.size();
        }

        @Override
        public boolean containsKey(ResourceLocation location) {
            return fluidLocationEntries.containsKey(location);
        }

        @Override
        public boolean containsKey(ResourceKey<FluidType> key) {
            return fluidEntries.containsKey(key);
        }

        public boolean containsId(ResourceLocation id) {
            return fluidLocationEntries.containsKey(id);
        }

        public boolean contains(ResourceKey<FluidType> key) {
            return fluidEntries.containsKey(key);
        }

        @Override
        public Set<ResourceKey<FluidType>> registryKeySet() {
            return fluidEntries.keySet();
        }

        @Override
        public Set<ResourceLocation> keySet() {
            return fluidLocationEntries.keySet();
        }

        public Set<ResourceKey<FluidType>> getKeys() {
            return fluidEntries.keySet();
        }

        public Set<ResourceLocation> getIds() {
            return fluidLocationEntries.keySet();
        }

        public Set<Map.Entry<ResourceKey<FluidType>, FluidType>> entrySet() {
            return fluidEntries.entrySet();
        }

        @Override
        public Collection<FluidType> values() {
            return fluidEntries.values();
        }

        @Override
        public Iterator<FluidType> iterator() {
            return fluidEntries.values().iterator();
        }

        @Override
        public Spliterator<FluidType> spliterator() {
            return fluidEntries.values().spliterator();
        }

        @Override
        public void forEach(java.util.function.Consumer<? super FluidType> action) {
            fluidEntries.values().forEach(action);
        }

        @Override
        public Stream<FluidType> stream() {
            return fluidEntries.values().stream();
        }

        public Stream<ResourceLocation> keys() {
            return fluidLocationEntries.keySet().stream();
        }

        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return fluidLocationEntries.keySet().stream()
                .map(rl -> ops.createString(rl.toString()));
        }

        @Override
        public Stream<FluidType> holders() {
            return fluidEntries.values().stream();
        }

        public Stream<Object> holderStream() {
            return fluidEntries.values().stream().map(v -> (Object) v);
        }

        @Override
        public Optional<FluidType> getOptional(ResourceLocation location) {
            return Optional.ofNullable(fluidLocationEntries.get(location));
        }

        public Optional<FluidType> getOptional(ResourceKey<FluidType> key) {
            return Optional.ofNullable(fluidEntries.get(key));
        }

        @Override
        public Optional<FluidType> getAny() {
            return fluidEntries.values().stream().findFirst();
        }

        public java.util.Optional<Object> getAnyHolder() {
            return fluidEntries.values().stream().findFirst().map(v -> v);
        }

        @Override
        public java.util.Optional<?> getRandom(Object random) {
            List<FluidType> list = new ArrayList<>(fluidEntries.values());
            if (list.isEmpty()) return java.util.Optional.empty();
            return java.util.Optional.of(list.get(((java.util.Random) random).nextInt(list.size())));
        }

        @Override
        public FluidType getOrThrow(ResourceKey<FluidType> key) {
            FluidType value = fluidEntries.get(key);
            if (value == null) throw new IllegalArgumentException("Missing key: " + key);
            return value;
        }

        @Override
        public java.util.Optional<?> getHolder(int id) {
            return java.util.Optional.ofNullable(byId(id));
        }

        @Override
        public java.util.Optional<?> getHolder(ResourceKey<FluidType> key) {
            return java.util.Optional.ofNullable(fluidEntries.get(key));
        }

        @Override
        public java.util.Optional<?> getHolder(ResourceLocation location) {
            return java.util.Optional.ofNullable(fluidLocationEntries.get(location));
        }

        public Object getHolderOrThrow(ResourceKey<FluidType> key) {
            FluidType value = fluidEntries.get(key);
            if (value == null) throw new IllegalArgumentException("Missing key: " + key);
            return value;
        }

        public Holder.Reference<FluidType> getHolderOrThrowHolder(ResourceKey<FluidType> key) {
            FluidType value = fluidEntries.get(key);
            if (value == null) throw new IllegalArgumentException("Missing key: " + key);
            Holder.Reference<FluidType> holder = new Holder.Direct<>(value) instanceof Holder.Reference ? (Holder.Reference<FluidType>) new Holder.Direct<>(value) : null;
            return holder;
        }

        public Holder.Reference<FluidType> method_40290(ResourceKey<FluidType> key) {
            FluidType value = fluidEntries.get(key);
            if (value == null) throw new IllegalArgumentException("Missing key: " + key);
            return new Holder.Direct<>(value) instanceof Holder.Reference ? (Holder.Reference<FluidType>) new Holder.Direct<>(value) : null;
        }

        public FluidType getOrThrowHolder(ResourceKey<FluidType> key) {
            FluidType value = fluidEntries.get(key);
            if (value == null) throw new IllegalArgumentException("Missing key: " + key);
            return value;
        }

        @Override
        public Object wrapAsHolder(FluidType value) {
            return value;
        }

        @Override
        public Object createIntrusiveHolder(FluidType value) {
            return value;
        }

        public java.util.Optional<?> getHolder(Object tag) {
            return java.util.Optional.empty();
        }

        public static <T> T register(net.minecraft.core.Registry<T> registry, String id, T entry) {
            return register(registry, VyneRl.fromColonFormRl(id), entry);
        }

        public static <T> T register(net.minecraft.core.Registry<T> registry, ResourceLocation id, T entry) {
            registry.register(id, entry);
            return entry;
        }

        public static <T> T register(net.minecraft.core.Registry<T> registry, ResourceKey<T> key, T entry) {
            registry.register(key, entry);
            return entry;
        }

        public Holder.Reference<FluidType> method_47984(net.minecraft.core.Registry registry, ResourceKey key, Object entry) {
            System.out.println("[NeoForgeRegistries] Entered method_47984: key=" + key + ", entry=" + entry);
            FluidType fluidType = (FluidType) entry;
            ResourceLocation loc = key.location();
            fluidEntries.put((ResourceKey<FluidType>) key, fluidType);
            fluidLocationEntries.put(loc, fluidType);
            VyneFluidTypeRegistry.register(loc, fluidType);
            System.out.println("[VyneFluidTypeRegistryShim] method_47984 called: " + loc + " -> " + fluidType);
            return new Holder.Direct<>(fluidType) instanceof Holder.Reference ? (Holder.Reference<FluidType>) new Holder.Direct<>(fluidType) : null;
        }

        public Holder.Reference<FluidType> method_47985(net.minecraft.core.Registry registry, ResourceLocation id, Object entry) {
            System.out.println("[NeoForgeRegistries] Entered method_47985: id=" + id + ", entry=" + entry);
            FluidType fluidType = (FluidType) entry;
            ResourceKey<FluidType> fluidKey = ResourceKey.create((ResourceKey<net.minecraft.core.Registry<FluidType>>) (Object) Keys.FLUID_TYPES, id);
            fluidEntries.put(fluidKey, fluidType);
            fluidLocationEntries.put(id, fluidType);
            VyneFluidTypeRegistry.register(id, fluidType);
            System.out.println("[VyneFluidTypeRegistryShim] method_47985 called: " + id + " -> " + fluidType);
            return null;
        }

        @Override
        public Stream<java.util.Map.Entry<ResourceKey<FluidType>, FluidType>> getTags() {
            return java.util.stream.Stream.empty();
        }

        public Stream<java.util.Map.Entry<Object, Object>> getTagEntries() {
            return java.util.stream.Stream.empty();
        }

        @Override
        public Stream<ResourceKey<FluidType>> getTagNames() {
            return java.util.stream.Stream.empty();
        }

        public Stream<Object> tagKeyStream() {
            return java.util.stream.Stream.empty();
        }

        @Override
        public java.util.Optional<?> getTag(Object tagKey) {
            return java.util.Optional.empty();
        }

        @Override
        public Object getOrCreateTag(Object tagKey) {
            return null;
        }

        public Iterable<Object> getTagOrEmpty(Object tagKey) {
            return Collections.emptyList();
        }

        public Iterable<Holder<FluidType>> getTagOrEmpty(TagKey<FluidType> tagKey) {
            return Collections.emptyList();
        }

        public java.util.Optional<?> getRandomElementOf(Object tagKey, Object random) {
            return java.util.Optional.empty();
        }

        public java.util.Optional<Holder<FluidType>> getRandomElementOf(TagKey<FluidType> tagKey, RandomSource random) {
            return java.util.Optional.empty();
        }

        public Iterable<Object> getTagOrEmpty(Object tagKey, Object random) {
            return Collections.emptyList();
        }

        public HolderLookup.RegistryLookup<FluidType> asTagAddingLookup() {
            return new HolderLookup.RegistryLookup<FluidType>() {
                @Override
                public Optional<Holder.Reference<FluidType>> getHolder(ResourceKey<FluidType> key) {
                    FluidType value = fluidEntries.get(key);
                    if (value != null) {
                        return Optional.of(new Holder.Direct<>(value) instanceof Holder.Reference ? (Holder.Reference<FluidType>) new Holder.Direct<>(value) : null);
                    }
                    return Optional.empty();
                }

                @Override
                public HolderLookup.RegistryLookup<?> lookup(ResourceKey<? extends net.minecraft.core.Registry<?>> registryKey) {
                    return this;
                }
            };
        }

        public HolderLookup.RegistryLookup<FluidType> asLookupHolder() {
            return new HolderLookup.RegistryLookup<FluidType>() {
                @Override
                public Optional<Holder.Reference<FluidType>> getHolder(ResourceKey<FluidType> key) {
                    FluidType value = fluidEntries.get(key);
                    if (value != null) {
                        return Optional.of(new Holder.Direct<>(value) instanceof Holder.Reference ? (Holder.Reference<FluidType>) new Holder.Direct<>(value) : null);
                    }
                    return Optional.empty();
                }

                @Override
                public HolderLookup.RegistryLookup<?> lookup(ResourceKey<? extends net.minecraft.core.Registry<?>> registryKey) {
                    return this;
                }
            };
        }

        @Override
        public void bindTags(java.util.Map<?, ?> tags) {
        }

        public void bindTagMap(java.util.Map<Object, List<Object>> tags) {
        }

        @Override
        public void resetTags() {
        }

        @Override
        public Object registryLifecycle() {
            return Lifecycle.stable();
        }

        @Override
        public Object holderOwner() {
            return this;
        }

        @Override
        public Object asLookup() {
            return this;
        }

        public Object method_46772() {
            return asTagAddingLookup();
        }

        public Object method_40295() {
            return this;
        }

        @Override
        public Object asHolderIdMap() {
            return this;
        }

        public Object holderIdMap() {
            return this;
        }

        @Override
        public net.minecraft.core.Registry<FluidType> freeze() {
            return this;
        }

        @Override
        public java.util.Optional<?> registrationInfo(ResourceKey<FluidType> key) {
            return java.util.Optional.empty();
        }

        @Override
        public void addAlias(ResourceLocation from, ResourceLocation to) {
            FluidType target = fluidLocationEntries.get(to);
            if (target != null) {
                fluidLocationEntries.put(from, target);
            }
        }

        @Override
        public java.util.Optional<?> registerForHolder(net.minecraft.core.Registry<?> registry,
                ResourceKey<?> key, Object entry) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<?> registerForHolder(net.minecraft.core.Registry<?> registry,
                ResourceLocation id, Object entry) {
            return java.util.Optional.empty();
        }

        public void registerAlias(ResourceLocation from, ResourceLocation to) {
            addAlias(from, to);
        }

        public int count() {
            return fluidEntries.size();
        }

        public boolean isEmpty() {
            return fluidEntries.isEmpty();
        }

        public void clear() {
            fluidEntries.clear();
            fluidLocationEntries.clear();
        }

        public boolean isValidKey(ResourceKey<FluidType> key) {
            return fluidEntries.containsKey(key);
        }

        public boolean isValidLocation(ResourceLocation location) {
            return fluidLocationEntries.containsKey(location);
        }

        public ResourceLocation[] getIdsArray() {
            return fluidLocationEntries.keySet().toArray(new ResourceLocation[0]);
        }

        public FluidType[] getValuesArray() {
            return fluidEntries.values().toArray(new FluidType[0]);
        }

        public Map<ResourceKey<FluidType>, FluidType> getEntryMap() {
            return new HashMap<>(fluidEntries);
        }

        public Map<ResourceLocation, FluidType> getLocationMap() {
            return new HashMap<>(fluidLocationEntries);
        }

        public boolean isRegistered(ResourceLocation location) {
            return fluidLocationEntries.containsKey(location);
        }

        public boolean isRegistered(ResourceKey<FluidType> key) {
            return fluidEntries.containsKey(key);
        }

        public Set<ResourceLocation> allLocations() {
            return new HashSet<>(fluidLocationEntries.keySet());
        }

        public Set<ResourceKey<FluidType>> allKeys() {
            return new HashSet<>(fluidEntries.keySet());
        }

        public Collection<FluidType> allValues() {
            return new ArrayList<>(fluidEntries.values());
        }

        public int getEntryCount() {
            return fluidEntries.size();
        }

        public List<FluidType> getEntriesAsList() {
            return new ArrayList<>(fluidEntries.values());
        }

        public Optional<FluidType> findFirst() {
            return fluidEntries.values().stream().findFirst();
        }

        public Optional<FluidType> findLast() {
            List<FluidType> list = new ArrayList<>(fluidEntries.values());
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(list.size() - 1));
        }

        public Optional<FluidType> findAt(int index) {
            List<FluidType> list = new ArrayList<>(fluidEntries.values());
            if (index < 0 || index >= list.size()) return Optional.empty();
            return Optional.of(list.get(index));
        }

        public boolean containsValue(FluidType value) {
            return fluidEntries.containsValue(value);
        }

        public boolean containsLocation(ResourceLocation location) {
            return fluidLocationEntries.containsKey(location);
        }

        public ResourceLocation getLocationForKey(ResourceKey<FluidType> key) {
            if (fluidEntries.containsKey(key)) {
                return key.location();
            }
            return null;
        }

        public ResourceKey<FluidType> getKeyForLocation(ResourceLocation location) {
            for (Map.Entry<ResourceKey<FluidType>, FluidType> entry : fluidEntries.entrySet()) {
                if (entry.getKey().location().equals(location)) {
                    return entry.getKey();
                }
            }
            return null;
        }

        public List<FluidType> getAll() {
            return new ArrayList<>(fluidEntries.values());
        }

        public int indexOf(FluidType value) {
            int index = 0;
            for (FluidType v : fluidEntries.values()) {
                if (v == value) return index;
                index++;
            }
            return -1;
        }

        public FluidType getAtIndex(int index) {
            int currentIndex = 0;
            for (FluidType v : fluidEntries.values()) {
                if (currentIndex == index) return v;
                currentIndex++;
            }
            return null;
        }

        public List<ResourceLocation> getAllLocations() {
            return new ArrayList<>(fluidLocationEntries.keySet());
        }

        public List<ResourceKey<FluidType>> getAllKeys() {
            return new ArrayList<>(fluidEntries.keySet());
        }

        public void forEachKey(java.util.function.Consumer<ResourceKey<FluidType>> action) {
            fluidEntries.keySet().forEach(action);
        }

        public void forEachLocation(java.util.function.Consumer<ResourceLocation> action) {
            fluidLocationEntries.keySet().forEach(action);
        }

        public void forEachValue(java.util.function.Consumer<FluidType> action) {
            fluidEntries.values().forEach(action);
        }

        public void forEachEntry(java.util.function.BiConsumer<ResourceKey<FluidType>, FluidType> action) {
            fluidEntries.forEach(action);
        }

        public boolean remove(ResourceKey<FluidType> key) {
            FluidType removed = fluidEntries.remove(key);
            if (removed != null) {
                fluidLocationEntries.remove(key.location());
                return true;
            }
            return false;
        }

        public boolean remove(ResourceLocation location) {
            FluidType removed = fluidLocationEntries.remove(location);
            if (removed != null) {
                fluidEntries.values().remove(removed);
                return true;
            }
            return false;
        }

        public void put(ResourceKey<FluidType> key, FluidType value) {
            fluidEntries.put(key, value);
            fluidLocationEntries.put(key.location(), value);
        }

        public void put(ResourceLocation location, FluidType value) {
            fluidLocationEntries.put(location, value);
        }

        public void putAll(Map<ResourceKey<FluidType>, FluidType> map) {
            for (Map.Entry<ResourceKey<FluidType>, FluidType> entry : map.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }

        public void putAllLocations(Map<ResourceLocation, FluidType> map) {
            fluidLocationEntries.putAll(map);
        }

        public Set<ResourceLocation> locations() {
            return fluidLocationEntries.keySet();
        }

        public Collection<FluidType> elements() {
            return fluidEntries.values();
        }
    }

    private NeoForgeRegistries() {}
}
