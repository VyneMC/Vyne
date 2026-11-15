/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ModConfigSpec {
    public static final class Entry {
        public final List<String> path;
        public final Object defaultValue;
        public final String comment;
        public final String type;
        final Integer min;
        final Integer max;
        final Double dMin;
        final Double dMax;
        final Predicate<Object> validator;
        final Class<?> enumClass;

        Entry(List<String> path, Object defaultValue, String comment, String type,
              Integer min, Integer max, Double dMin, Double dMax,
              Predicate<Object> validator, Class<?> enumClass) {
            this.path = path;
            this.defaultValue = defaultValue;
            this.comment = comment;
            this.type = type;
            this.min = min;
            this.max = max;
            this.dMin = dMin;
            this.dMax = dMax;
            this.validator = validator;
            this.enumClass = enumClass;
        }

        public String key() {
            return String.join(".", path);
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, Object> loadedValues = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;
    private volatile Runnable saveCallback = this::noop;

    ModConfigSpec() {}

    void addEntry(Entry e) {
        entries.put(e.key(), e);
    }

    public void acceptLoadedValues(Map<String, Object> values, Runnable saveCallback) {
        this.loadedValues.putAll(values);
        this.saveCallback = saveCallback != null ? saveCallback : this::noop;
        this.loaded = true;
    }

    public Object valueFor(Entry entry) {
        Object v = loadedValues.get(entry.key());
        return v != null ? coerce(v, entry) : entry.defaultValue;
    }

    private Object coerce(Object raw, Entry entry) {
        try {
            switch (entry.type) {
                case "BOOLEAN" -> { if (raw instanceof Boolean b) return b; }
                case "INT" -> { if (raw instanceof Number n) return n.intValue(); }
                case "LONG" -> { if (raw instanceof Number n) return n.longValue(); }
                case "DOUBLE" -> { if (raw instanceof Number n) return n.doubleValue(); }
                case "STRING" -> { return raw.toString(); }
                case "ENUM" -> {
                    if (entry.enumClass != null && entry.enumClass.isInstance(raw)) return raw;
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Object e = Enum.valueOf((Class<? extends Enum>) entry.enumClass, raw.toString());
                    return e;
                }
                default -> { return raw; }
            }
        } catch (Throwable ignored) {
        }
        return entry.defaultValue;
    }

    public void setValue(Entry entry, Object newValue) {
        loadedValues.put(entry.key(), newValue);
        saveCallback.run();
    }

    public boolean isLoaded() {
        return loaded;
    }

    public Map<String, Entry> getEntries() {
        return entries;
    }

    private void noop() {}

    public static class ConfigValue<T> {
        protected final List<String> path;
        protected final T defaultValue;
        protected ModConfigSpec spec;
        protected Entry entry;

        ConfigValue(List<String> path, T defaultValue) {
            this.path = path;
            this.defaultValue = defaultValue;
        }

        void attach(ModConfigSpec spec, Entry entry) {
            this.spec = spec;
            this.entry = entry;
        }

        public T get() {
            if (spec != null && entry != null) {
                @SuppressWarnings("unchecked")
                T v = (T) spec.valueFor(entry);
                return v;
            }
            return defaultValue;
        }

        public T getDefault() {
            return defaultValue;
        }

        public void set(T value) {
            if (spec != null && entry != null) spec.setValue(entry, value);
        }

        public List<String> getPath() {
            return path;
        }
    }

    public static class BooleanValue extends ConfigValue<Boolean> {
        BooleanValue(List<String> path, boolean def) { super(path, def); }
    }

    public static class IntValue extends ConfigValue<Integer> {
        IntValue(List<String> path, int def) { super(path, def); }
    }

    public static class LongValue extends ConfigValue<Long> {
        LongValue(List<String> path, long def) { super(path, def); }
    }

    public static class DoubleValue extends ConfigValue<Double> {
        DoubleValue(List<String> path, double def) { super(path, def); }
    }

    public static class EnumValue<T extends Enum<T>> extends ConfigValue<T> {
        EnumValue(List<String> path, T def) { super(path, def); }
    }

    public static class Builder {
        private final List<String> currentPath = new ArrayList<>();
        private String currentComment;
        private final List<Entry> pending = new ArrayList<>();
        private final List<ConfigValue<?>> values = new ArrayList<>();

        public Builder push(String path) {
            currentPath.add(path);
            return this;
        }

        public Builder push(List<String> path) {
            currentPath.addAll(path);
            return this;
        }

        public Builder pop() {
            if (!currentPath.isEmpty()) currentPath.remove(currentPath.size() - 1);
            return this;
        }

        public Builder pop(int count) {
            for (int i = 0; i < count; i++) pop();
            return this;
        }

        public Builder comment(String comment) {
            this.currentComment = comment;
            return this;
        }

        public Builder comment(String... comment) {
            this.currentComment = String.join("\n", comment);
            return this;
        }

        public Builder translation(String key) {
            return this;
        }

        public Builder worldRestart() {
            return this;
        }

        public Builder gameRestart() {
            return this;
        }

        private List<String> fullPath(String path) {
            List<String> full = new ArrayList<>(currentPath);
            for (String p : path.split("\\.")) full.add(p);
            return full;
        }

        private <V extends ConfigValue<?>> V register(V value, Entry entry) {
            pending.add(entry);
            values.add(value);
            return value;
        }

        public <T> ConfigValue<T> define(String path, T defaultValue) {
            List<String> p = fullPath(path);
            ConfigValue<T> v = new ConfigValue<>(p, defaultValue);
            return register(v, new Entry(p, defaultValue, currentComment, "STRING", null, null, null, null, null, null));
        }

        public <T> ConfigValue<T> define(List<String> path, T defaultValue) {
            List<String> p = new ArrayList<>(currentPath);
            p.addAll(path);
            ConfigValue<T> v = new ConfigValue<>(p, defaultValue);
            return register(v, new Entry(p, defaultValue, currentComment, "STRING", null, null, null, null, null, null));
        }

        public <T> ConfigValue<T> define(String path, T defaultValue, Predicate<Object> validator) {
            List<String> p = fullPath(path);
            ConfigValue<T> v = new ConfigValue<>(p, defaultValue);
            return register(v, new Entry(p, defaultValue, currentComment, "STRING", null, null, null, null, validator, null));
        }

        public <T> ConfigValue<T> define(String path, Supplier<T> defaultSupplier, Predicate<Object> validator) {
            return define(path, defaultSupplier.get(), validator);
        }

        public BooleanValue define(String path, boolean defaultValue) {
            List<String> p = fullPath(path);
            BooleanValue v = new BooleanValue(p, defaultValue);
            return register(v, new Entry(p, defaultValue, currentComment, "BOOLEAN", null, null, null, null, null, null));
        }

        public IntValue defineInRange(String path, int defaultValue, int min, int max) {
            List<String> p = fullPath(path);
            IntValue v = new IntValue(p, defaultValue);
            return register(v, new Entry(p, defaultValue, currentComment, "INT", min, max, null, null, null, null));
        }

        public LongValue defineInRange(String path, long defaultValue, long min, long max) {
            List<String> p = fullPath(path);
            LongValue v = new LongValue(p, defaultValue);
            return register(v, new Entry(p, defaultValue, currentComment, "LONG", (int) min, (int) max, null, null, null, null));
        }

        public DoubleValue defineInRange(String path, double defaultValue, double min, double max) {
            List<String> p = fullPath(path);
            DoubleValue v = new DoubleValue(p, defaultValue);
            return register(v, new Entry(p, defaultValue, currentComment, "DOUBLE", null, null, min, max, null, null));
        }

        public <T extends Enum<T>> EnumValue<T> defineEnum(String path, T defaultValue) {
            List<String> p = fullPath(path);
            EnumValue<T> v = new EnumValue<>(p, defaultValue);
            return register(v, new Entry(p, defaultValue, currentComment, "ENUM", null, null, null, null, null, defaultValue.getClass()));
        }

        public <T extends Enum<T>> EnumValue<T> defineEnum(String path, T defaultValue, Predicate<Object> validator) {
            return defineEnum(path, defaultValue);
        }

        public <T extends Enum<T>> EnumValue<T> defineEnum(List<String> path, T defaultValue) {
            List<String> p = new ArrayList<>(currentPath);
            p.addAll(path);
            EnumValue<T> v = new EnumValue<>(p, defaultValue);
            return register(v, new Entry(p, defaultValue, currentComment, "ENUM", null, null, null, null, null, defaultValue.getClass()));
        }

        public <T> ConfigValue<List<? extends T>> defineList(String path, List<? extends T> defaultValue, Predicate<Object> elementValidator) {
            List<String> p = fullPath(path);
            ConfigValue<List<? extends T>> v = new ConfigValue<>(p, defaultValue);
            return register(v, new Entry(p, defaultValue, currentComment, "LIST", null, null, null, null, elementValidator, null));
        }

        public <T> ConfigValue<List<? extends T>> defineList(String path, Supplier<? extends List<? extends T>> defaultValue, Predicate<Object> elementValidator) {
            return defineList(path, defaultValue.get(), elementValidator);
        }

        public <T> ConfigValue<List<? extends T>> defineListAllowEmpty(String path, List<? extends T> defaultValue, Supplier<?> newElementSupplier, Predicate<Object> elementValidator) {
            return defineList(path, defaultValue, elementValidator);
        }

        public <T> ConfigValue<List<? extends T>> defineListAllowEmpty(String path, Supplier<? extends List<? extends T>> defaultValue, Supplier<?> newElementSupplier, Predicate<Object> elementValidator) {
            return defineList(path, defaultValue.get(), elementValidator);
        }

        public <T> org.apache.commons.lang3.tuple.Pair<T, ModConfigSpec> configure(java.util.function.Function<Builder, T> factory) {
            return org.apache.commons.lang3.tuple.Pair.of(factory.apply(this), build());
        }

        public ModConfigSpec build() {
            ModConfigSpec spec = new ModConfigSpec();
            for (int i = 0; i < pending.size(); i++) {
                Entry e = pending.get(i);
                spec.addEntry(e);
                values.get(i).attach(spec, e);
            }
            return spec;
        }
    }
}
