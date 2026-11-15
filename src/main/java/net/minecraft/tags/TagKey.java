/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 *
 * Temporary stand-in for a Minecraft class, only here so Vyne compiles.
 * It will be removed once Vyne compiles against the real Minecraft jar with Mojang names.
 */

package net.minecraft.tags;

import net.minecraft.resources.ResourceLocation;

public final class TagKey<T> {
    private final ResourceLocation location;

    public TagKey(ResourceLocation location) {
        this.location = location;
    }

    public ResourceLocation location() {
        return location;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TagKey<?> tagKey = (TagKey<?>) o;
        return location.equals(tagKey.location);
    }

    @Override
    public int hashCode() {
        return location.hashCode();
    }

    @Override
    public String toString() {
        return "TagKey[" + location + "]";
    }
}
