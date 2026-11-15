/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 *
 * Temporary stand-in for a Minecraft class, only here so Vyne compiles.
 * It will be removed once Vyne compiles against the real Minecraft jar with Mojang names.
 */

package net.minecraft;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public class class_5321<T> {
    private final ResourceLocation registryName;
    private final ResourceLocation location;

    protected class_5321(ResourceLocation registryName, ResourceLocation location) {
        this.registryName = registryName;
        this.location = location;
    }

    public static <T> class_5321<T> method_29177(ResourceLocation registryName) {
        return new class_5321<>(new ResourceLocation("minecraft", "root"), registryName);
    }

    public static <T> class_5321<T> method_29184(class_5321<? extends net.minecraft.core.Registry<T>> registry, ResourceLocation location) {
        return new class_5321<>(registry.location(), location);
    }

    public ResourceLocation location() { return location; }
    public ResourceLocation registry() { return registryName; }

    public ResourceLocation method_29177() { return registryName; }
    public ResourceLocation method_29184() { return location; }

    @Override public String toString() { return "ResourceKey[" + registryName + " / " + location + "]"; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof class_5321<?> that)) return false;
        return Objects.equals(registryName, that.registryName) && Objects.equals(location, that.location);
    }
    @Override public int hashCode() { return Objects.hash(registryName, location); }
}
