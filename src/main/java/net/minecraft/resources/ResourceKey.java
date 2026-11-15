/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 *
 * Temporary stand-in for a Minecraft class, only here so Vyne compiles.
 * It will be removed once Vyne compiles against the real Minecraft jar with Mojang names.
 */

package net.minecraft.resources;

import java.util.Objects;
import net.minecraft.class_5321;

public final class ResourceKey<T> extends class_5321<T> {
    private ResourceKey(ResourceLocation registryName, ResourceLocation location) {
        super(registryName, location);
    }

    public static <T> ResourceKey<T> createRegistryKey(ResourceLocation registryName) {
        return new ResourceKey<>(new ResourceLocation("minecraft", "root"), registryName);
    }

    public static <T> ResourceKey<T> create(ResourceKey<? extends net.minecraft.core.Registry<T>> registry, ResourceLocation location) {
        return new ResourceKey<>(registry.registry(), location);
    }

    public ResourceLocation location() { return super.location(); }
    public ResourceLocation registry() { return super.registry(); }

    @Override public String toString() { return "ResourceKey[" + registry() + " / " + location() + "]"; }
}
