/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.registries;

import dev.stolym.vyne.registry.VyneGenericRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class VyneRegistryProxy<T> implements Registry<T> {
    private final ResourceKey<? extends Registry<T>> registryKey;
    private final String registryPath;
    private final Registry<?> live;

    public VyneRegistryProxy(ResourceKey<? extends Registry<T>> key, String registryPath) {
        this(key, registryPath, null);
    }

    protected VyneRegistryProxy(ResourceKey<? extends Registry<T>> key, String registryPath,
            Registry<?> live) {
        this.registryKey = key;
        this.registryPath = Objects.requireNonNull(registryPath, "registryPath");
        this.live = live;
    }

    @Override
    public ResourceKey<? extends Registry<T>> key() {
        return registryKey;
    }

    public String getRegistryPath() {
        return registryPath;
    }

    protected Registry<?> liveRegistry() {
        return live;
    }

    @Override
    public void register(ResourceKey<T> key, T value) {
        if (key != null) {
            VyneGenericRegistry.register(registryPath, key.location().toString(), value);
        }
    }

    @Override
    public void register(ResourceLocation location, T value) {
        VyneGenericRegistry.register(registryPath, location.toString(), value);
    }

    @Override
    public Optional<T> getOptional(ResourceLocation location) {
        if (location == null) {
            return Optional.empty();
        }
        try {
            if (live != null) {
                Object val = invokeRegistryGetById(live, location);
                if (val != null) {
                    return Optional.of((T) val);
                }
            }
        } catch (Throwable ignored) {
        }
        return Optional.ofNullable((T) VyneGenericRegistry.get(registryPath, location.toString()));
    }

    @Override
    public T get(ResourceKey<T> resourceKey) {
        if (resourceKey == null) {
            return null;
        }
        return getOptional(resourceKey.location()).orElse(null);
    }

    @Override
    public Set<ResourceLocation> keySet() {
        LinkedHashSet<ResourceLocation> keys = new LinkedHashSet<>();
        try {
            if (live != null) {
                keys.addAll(live.keySet());
            }
        } catch (Throwable ignored) {
        }
        for (String id : VyneGenericRegistry.getKeys(registryPath)) {
            try {
                keys.add(parseColonId(id));
            } catch (Throwable ignored) {
            }
        }
        return keys;
    }

    @Override
    public java.util.Collection<T> values() {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        try {
            if (live != null) {
                for (T v : (Iterable<T>) live) {
                    if (v != null) {
                        out.add(v);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        for (Object v : VyneGenericRegistry.getValues(registryPath)) {
            if (v != null) {
                out.add((T) v);
            }
        }
        return new ArrayList<>(out);
    }

    @Override
    public Set<Map.Entry<ResourceKey<T>, T>> method_29722() {
        Set<Map.Entry<ResourceKey<T>, T>> out = new LinkedHashSet<>();
        ResourceKey<? extends Registry<T>> reg = key();
        for (ResourceLocation rl : keySet()) {
            Optional<T> opt = getOptional(rl);
            if (opt.isEmpty()) {
                continue;
            }
            ResourceKey<T> ek = ResourceKey.create(reg, rl);
            out.add(new java.util.AbstractMap.SimpleImmutableEntry<>(ek, opt.get()));
        }
        return out;
    }

    @Override
    public Set<ResourceKey<T>> registryKeySet() {
        Set<ResourceKey<T>> out = new LinkedHashSet<>();
        ResourceKey<? extends Registry<T>> reg = key();
        for (ResourceLocation rl : keySet()) {
            out.add(ResourceKey.create(reg, rl));
        }
        return out;
    }

    @Override
    public boolean containsKey(ResourceLocation location) {
        if (location == null) {
            return false;
        }
        try {
            if (live != null && live.containsKey(location)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return VyneGenericRegistry.containsKey(registryPath, location.toString());
    }

    @Override
    public boolean containsKey(ResourceKey<T> resourceKey) {
        return resourceKey != null && containsKey(resourceKey.location());
    }

    @Override
    public int size() {
        return keySet().size();
    }

    @Override
    public Iterator<T> iterator() {
        return values().iterator();
    }

    @Override
    public Optional<ResourceKey<T>> getResourceKey(T value) {
        Optional<ResourceLocation> id = getId(value);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ResourceKey.create(key(), id.get()));
    }

    @Override
    public Optional<T> getOptional(ResourceKey<T> resourceKey) {
        return getOptional(resourceKey == null ? null : resourceKey.location());
    }

    @Override
    public T getOrThrow(ResourceKey<T> resourceKey) {
        return getOptional(resourceKey).orElseThrow(
            () -> new IllegalStateException("Missing key: " + resourceKey));
    }

    @Override
    public Stream<T> stream() {
        return values().stream();
    }

    @Override
    public Optional<ResourceLocation> getId(T value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            if (live != null) {
                ResourceLocation k = invokeRegistryKeyForValue(live, value);
                if (k != null) {
                    return Optional.of(k);
                }
            }
        } catch (Throwable ignored) {
        }
        for (String idStr : VyneGenericRegistry.getKeys(registryPath)) {
            Object v = VyneGenericRegistry.get(registryPath, idStr);
            if (v == value) {
                try {
                    return Optional.of(parseColonId(idStr));
                } catch (Throwable ignored) {
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public int getRawId(T value) {
        return -1;
    }

    @Override
    public T byId(int id) {
        return null;
    }

    @Override
    public Optional<T> getAny() {
        return stream().findFirst();
    }

    @Override
    public Stream<T> holders() {
        return stream();
    }

    @Override
    public Optional<?> getHolder(ResourceKey<T> resourceKey) {
        return Optional.ofNullable(get(resourceKey));
    }

    @Override
    public Optional<?> getHolder(ResourceLocation id) {
        return Optional.ofNullable(getOptional(id).orElse(null));
    }

    @Override
    public boolean containsId(ResourceLocation id) {
        return containsKey(id);
    }

    @Override
    public boolean contains(ResourceKey<T> resourceKey) {
        return containsKey(resourceKey);
    }

    @Override
    public Set<ResourceLocation> getIds() {
        return keySet();
    }

    @Override
    public Spliterator<T> spliterator() {
        return values().spliterator();
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        values().forEach(action);
    }

    @Override
    public Registry<T> freeze() {
        return this;
    }

    @Override
    public Stream<Entry<ResourceKey<T>, T>> getTags() {
        return Stream.empty();
    }

    @Override
    public Stream<ResourceKey<T>> getTagNames() {
        return Stream.empty();
    }

    @Override
    public void resetTags() {}

    @Override
    public void bindTags(Map<?, ?> tagEntries) {}

    @Override
    public Optional<?> getTag(Object tagKey) {
        return Optional.empty();
    }

    @Override
    public Object getOrCreateTag(Object tagKey) {
        return null;
    }

    @Override
    public Optional<?> getHolder(int rawId) {
        return Optional.empty();
    }

    @Override
    public Object wrapAsHolder(T value) {
        return value;
    }

    @Override
    public Object createIntrusiveHolder(T value) {
        return value;
    }

    @Override
    public Optional<?> registrationInfo(ResourceKey<T> key) {
        return Optional.empty();
    }

    @Override
    public Object registryLifecycle() {
        return null;
    }

    @Override
    public Object holderOwner() {
        return this;
    }

    @Override
    public Object asLookup() {
        return this;
    }

    @Override
    public Object asHolderIdMap() {
        return this;
    }

    @Override
    public void addAlias(ResourceLocation from, ResourceLocation to) {}

    @Override
    public Optional<?> getRandom(Object random) {
        return Optional.empty();
    }

    @Override
    public Optional<?> registerForHolder(Registry<?> registry, ResourceKey<?> key, Object entry) {
        return Optional.empty();
    }

    @Override
    public Optional<?> registerForHolder(Registry<?> registry, ResourceLocation id, Object entry) {
        return Optional.empty();
    }

    private static ResourceLocation parseColonId(String id) {
        int c = id.indexOf(':');
        if (c <= 0) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", id);
        }
        return ResourceLocation.fromNamespaceAndPath(id.substring(0, c), id.substring(c + 1));
    }

    private static Object invokeRegistryGetById(Registry<?> registry, ResourceLocation id) {
        for (java.lang.reflect.Method m : registry.getClass().getMethods()) {
            if (m.getParameterCount() != 1) {
                continue;
            }
            if (m.getName().equals("get") || m.getName().equals("getValue")
                    || m.getName().contains("method_")) {
                Class<?> pt = m.getParameterTypes()[0];
                if (pt.isInstance(id) || pt.getName().contains("class_2960")
                        || pt.getName().contains("ResourceLocation")) {
                    try {
                        return m.invoke(registry, id);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return null;
    }

    private static ResourceLocation invokeRegistryKeyForValue(Registry<?> registry, Object value) {
        for (java.lang.reflect.Method m : registry.getClass().getMethods()) {
            if (m.getParameterCount() != 1) {
                continue;
            }
            if (m.getReturnType() == null) {
                continue;
            }
            String ret = m.getReturnType().getName();
            if (!(ret.contains("ResourceLocation") || ret.contains("class_2960"))) {
                continue;
            }
            if (m.getName().equals("getKey") || m.getName().contains("method_")) {
                try {
                    Object out = m.invoke(registry, value);
                    if (out instanceof ResourceLocation rl) {
                        return rl;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }
}
