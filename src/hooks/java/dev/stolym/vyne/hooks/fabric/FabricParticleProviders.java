/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.fabric;

import it.unimi.dsi.fastutil.ints.AbstractInt2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.function.Supplier;

public final class FabricParticleProviders {
    private FabricParticleProviders() {}

    public static <V> Int2ObjectMap<V> rawIdView(Supplier<Map<ResourceLocation, V>> providers) {
        return new AbstractInt2ObjectMap<>() {
            private ResourceLocation key(int rawId) {
                ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.byId(rawId);
                return type == null ? null : BuiltInRegistries.PARTICLE_TYPE.getKey(type);
            }

            @Override
            public V get(int rawId) {
                ResourceLocation key = key(rawId);
                return key == null ? null : providers.get().get(key);
            }

            @Override
            public V put(int rawId, V value) {
                ResourceLocation key = key(rawId);
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
                for (Map.Entry<ResourceLocation, V> entry : providers.get().entrySet()) {
                    ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(entry.getKey());
                    if (type != null) entries.add(new BasicEntry<>(BuiltInRegistries.PARTICLE_TYPE.getId(type), entry.getValue()));
                }
                return entries;
            }
        };
    }
}