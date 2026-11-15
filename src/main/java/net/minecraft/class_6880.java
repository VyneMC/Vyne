/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 *
 * Temporary stand-in for a Minecraft class, only here so Vyne compiles.
 * It will be removed once Vyne compiles against the real Minecraft jar with Mojang names.
 */

package net.minecraft;

import java.util.Objects;
import java.util.Optional;

public interface class_6880<T> {
    default Optional<class_5321<T>> method_40230() {
        return Optional.empty();
    }

    static <T> class_6880<T> direct(T value) {
        return new Direct<>(Objects.requireNonNull(value, "value"));
    }

    static <T> class_6880<T> method_40223(T value) {
        return direct(value);
    }

    final class Direct<T> implements class_6880<T> {
        private final T value;

        Direct(T value) {
            this.value = value;
        }
    }

    public interface class_6883<T> extends class_6880<T> {
    }
}
