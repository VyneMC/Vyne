/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 *
 * Temporary stand-in for a Minecraft class, only here so Vyne compiles.
 * It will be removed once Vyne compiles against the real Minecraft jar with Mojang names.
 */

package net.minecraft.resources;

public final class ResourceLocation {
    private final String namespace;
    private final String path;

    public ResourceLocation(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    public static ResourceLocation of(String combined) {
        String[] parts = combined.split(":", 2);
        return parts.length == 2
            ? new ResourceLocation(parts[0], parts[1])
            : new ResourceLocation("minecraft", combined);
    }

    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    public static ResourceLocation parse(String combined) {
        return of(combined);
    }

    public static ResourceLocation tryParse(String combined) {
        try { return of(combined); } catch (Exception e) { return null; }
    }

    public static ResourceLocation withDefaultNamespace(String path) {
        return new ResourceLocation("minecraft", path);
    }

    public String getNamespace() { return namespace; }
    public String getPath() { return path; }

    public String method_12836() { return namespace; }
    public String method_12832() { return path; }

    @Override public String toString() { return namespace + ":" + path; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceLocation that)) return false;
        return java.util.Objects.equals(namespace, that.namespace)
            && java.util.Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(namespace, path);
    }
}
