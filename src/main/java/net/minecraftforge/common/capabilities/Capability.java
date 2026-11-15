/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.common.capabilities;

import net.minecraftforge.common.util.LazyOptional;

public class Capability<T> {
    private final String name;

    public Capability() {
        this("vyne:anonymous");
    }

    public Capability(String name) {
        this.name = name == null ? "vyne:anonymous" : name;
    }

    public String getName() { return name; }

    public <R> LazyOptional<R> orEmpty(Capability<?> cap, LazyOptional<R> other) {
        if (cap == this && other != null) {
            return other;
        }
        return LazyOptional.empty();
    }

    @SuppressWarnings("unchecked")
    public <R> LazyOptional<R> cast(LazyOptional<?> opt) {
        return (LazyOptional<R>) (opt == null ? LazyOptional.empty() : opt);
    }

    @Override
    public String toString() { return "Capability(" + name + ")"; }

    public interface IStorage<T> {
    }
}
