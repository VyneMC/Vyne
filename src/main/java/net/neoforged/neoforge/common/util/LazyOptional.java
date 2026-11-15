/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.common.util;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class LazyOptional<T> {
    public static final LazyOptional<?> EMPTY = new LazyOptional<>(() -> null);

    private final Supplier<T> supplier;
    private boolean resolved;
    private T value;

    private LazyOptional(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    @SuppressWarnings("unchecked")
    public static <T> LazyOptional<T> empty() {
        return (LazyOptional<T>) EMPTY;
    }

    public static <T> LazyOptional<T> of(Supplier<T> supplier) {
        return new LazyOptional<>(supplier);
    }

    public static <T> LazyOptional<T> of(NonNullSupplier<T> supplier) {
        return new LazyOptional<>(supplier);
    }

    public boolean isPresent() {
        return resolve() != null;
    }

    public T orElse(T other) {
        T v = resolve();
        return v != null ? v : other;
    }

    public T orElseGet(Supplier<? extends T> other) {
        T v = resolve();
        return v != null ? v : other.get();
    }

    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        T v = resolve();
        if (v != null) return v;
        throw exceptionSupplier.get();
    }

    public T getValue() {
        T v = resolve();
        if (v == null) throw new NoSuchElementException("LazyOptional is empty");
        return v;
    }

    public Optional<T> resolveOptional() {
        return Optional.ofNullable(resolve());
    }

    public void ifPresent(Consumer<? super T> consumer) {
        T v = resolve();
        if (v != null) consumer.accept(v);
    }

    public <R> LazyOptional<R> map(Function<? super T, ? extends R> mapper) {
        return new LazyOptional<>(() -> {
            T v = resolve();
            return v == null ? null : mapper.apply(v);
        });
    }

    public LazyOptional<T> filter(Predicate<? super T> predicate) {
        return new LazyOptional<>(() -> {
            T v = resolve();
            return v != null && predicate.test(v) ? v : null;
        });
    }

    public void invalidate() {
        resolved = true;
        value = null;
    }

    public T cast() {
        return resolve();
    }

    private T resolve() {
        if (!resolved) {
            resolved = true;
            value = supplier != null ? supplier.get() : null;
        }
        return value;
    }

    @Override
    public String toString() {
        return "LazyOptional[" + Objects.toString(resolve()) + "]";
    }
}
