/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 *
 * Temporary stand-in for a Minecraft class, only here so Vyne compiles.
 * It will be removed once Vyne compiles against the real Minecraft jar with Mojang names.
 */

package net.minecraft.core;

import net.minecraft.resources.ResourceKey;
import java.util.Optional;

public interface HolderLookup {
    interface RegistryLookup<T> extends HolderLookup.Provider {
        Optional<Holder.Reference<T>> getHolder(ResourceKey<T> key);

        default Holder.Reference<T> getHolderOrThrow(ResourceKey<T> key) {
            return getHolder(key).orElseThrow(() -> new IllegalArgumentException("Missing key: " + key));
        }
    }

    interface Provider {
        HolderLookup.RegistryLookup<?> lookup(ResourceKey<? extends Registry<?>> registryKey);
    }
}
