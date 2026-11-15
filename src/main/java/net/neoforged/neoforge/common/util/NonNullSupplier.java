/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.neoforged.neoforge.common.util;

import java.util.function.Supplier;

@FunctionalInterface
public interface NonNullSupplier<T> extends Supplier<T> {
    @Override
    T get();
}
