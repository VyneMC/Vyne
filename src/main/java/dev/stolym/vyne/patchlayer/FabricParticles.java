/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import it.unimi.dsi.fastutil.ints.AbstractInt2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.class_2378;
import net.minecraft.class_2396;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

import java.util.Map;
import java.util.function.Supplier;

public final class FabricParticles {
    private FabricParticles() {}

    public static <V> Int2ObjectMap<V> rawIdView(Supplier<Map<class_2960, V>> providers) {
        return new AbstractInt2ObjectMap<>() {
            private final class_2378<class_2396<?>> types = class_7923.field_41180;

            private class_2960 key(int rawId) {
                class_2396<?> type = types.method_10200(rawId);
                return type == null ? null : types.method_10221(type);
            }

            @Override
            public V get(int rawId) {
                class_2960 key = key(rawId);
                return key == null ? null : providers.get().get(key);
            }

            @Override
            public V put(int rawId, V value) {
                class_2960 key = key(rawId);
                if (key == null) throw new IllegalArgumentException("No particle type with raw id " + rawId);
                return providers.get().put(key, value);
            }

            @Override
            public int size() {
                return providers.get().size();
            }

            @Override
            public ObjectSet<Int2ObjectMap.Entry<V>> int2ObjectEntrySet() {
                ObjectSet<Int2ObjectMap.Entry<V>> entries = new ObjectArraySet<>();
                for (Map.Entry<class_2960, V> entry : providers.get().entrySet()) {
                    class_2396<?> type = types.method_10223(entry.getKey());
                    if (type != null) entries.add(new BasicEntry<>(types.method_10206(type), entry.getValue()));
                }
                return entries;
            }
        };
    }
}
