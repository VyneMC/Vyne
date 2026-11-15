/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 *
 * Temporary stand-in for a Minecraft class, only here so Vyne compiles.
 * It will be removed once Vyne compiles against the real Minecraft jar with Mojang names.
 */

package net.minecraft.core;

import net.minecraft.resources.ResourceKey;
import net.minecraft.class_6880;
import java.util.function.Predicate;

public interface Holder<T> extends class_6880<T> {
    T value();

    boolean is(Predicate<T> predicate);

    boolean is(ResourceKey<T> key);

    static <T> Holder<T> direct(T value) {
        return new Direct<>(value);
    }

    static class Direct<T> implements Holder<T> {
        private final T value;

        public Direct(T value) {
            this.value = value;
        }

        @Override
        public T value() {
            return value;
        }

        @Override
        public boolean is(Predicate<T> predicate) {
            return predicate.test(value);
        }

        @Override
        public boolean is(ResourceKey<T> key) {
            return false;
        }
    }

    interface Reference<T> extends Holder<T>, class_6880.class_6883<T> {
        ResourceKey<T> key();

        boolean isBound();
    }
}
