/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 *
 * Temporary stand-in for a Minecraft class, only here so Vyne compiles.
 * It will be removed once Vyne compiles against the real Minecraft jar with Mojang names.
 */

package net.minecraft.core;

import net.minecraft.class_2378;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

public interface Registry<T> extends class_2378<T> {
    ResourceKey<? extends Registry<T>> key();

    default ResourceKey<? extends Registry<T>> method_30517() {
        return key();
    }

    void register(ResourceKey<T> key, T value);

    void register(ResourceLocation location, T value);

    Optional<T> getOptional(ResourceLocation location);

    T get(ResourceKey<T> key);

    Set<ResourceLocation> keySet();

    Collection<T> values();

    default Set<Map.Entry<ResourceKey<T>, T>> method_29722() {
        return Set.of();
    }

    Set<ResourceKey<T>> registryKeySet();

    boolean containsKey(ResourceLocation location);

    boolean containsKey(ResourceKey<T> key);

    int size();

    Iterator<T> iterator();

    Optional<ResourceKey<T>> getResourceKey(T value);

    Optional<T> getOptional(ResourceKey<T> key);

    T getOrThrow(ResourceKey<T> key);

    Stream<T> stream();

    Optional<ResourceLocation> getId(T value);

    int getRawId(T value);

    T byId(int id);

    Registry<T> freeze();

    Optional<T> getAny();

    Stream<T> holders();

    Stream<Map.Entry<ResourceKey<T>, T>> getTags();

    Stream<ResourceKey<T>> getTagNames();

    void resetTags();

    void bindTags(Map<?, ?> tagEntries);

    Optional<?> getTag(Object tagKey);

    Object getOrCreateTag(Object tagKey);

    Optional<?> getHolder(int rawId);

    Optional<?> getHolder(ResourceKey<T> key);

    Optional<?> getHolder(ResourceLocation id);

    Object wrapAsHolder(T value);

    Object createIntrusiveHolder(T value);

    Optional<?> registrationInfo(ResourceKey<T> key);

    Object registryLifecycle();

    Object holderOwner();

    Object asLookup();

    Object asHolderIdMap();

    void addAlias(ResourceLocation from, ResourceLocation to);

    Optional<?> getRandom(Object random);

    Optional<?> registerForHolder(Registry<?> registry, ResourceKey<?> key, Object entry);

    Optional<?> registerForHolder(Registry<?> registry, ResourceLocation id, Object entry);

    boolean containsId(ResourceLocation id);

    boolean contains(ResourceKey<T> key);

    Set<ResourceLocation> getIds();

    Spliterator<T> spliterator();

    void forEach(Consumer<? super T> action);
}
