/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 *
 * Temporary stand-in for a Minecraft class, only here so Vyne compiles.
 * It will be removed once Vyne compiles against the real Minecraft jar with Mojang names.
 */

package net.minecraft;

import java.util.Objects;

public class class_2960 {
    protected final String namespace;
    protected final String path;

    public class_2960(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    public static class_2960 method_60655(String namespace, String path) { return new class_2960(namespace, path); }

    public String method_12836() { return namespace; } 
    public String method_12832() { return path; }      

    @Override public String toString() { return namespace + ":" + path; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof class_2960 that)) return false;
        return Objects.equals(namespace, that.namespace) && Objects.equals(path, that.path);
    }
    @Override public int hashCode() { return Objects.hash(namespace, path); }
}
