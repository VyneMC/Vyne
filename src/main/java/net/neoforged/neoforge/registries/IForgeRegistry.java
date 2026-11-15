/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Set;

public interface IForgeRegistry<T> {
    T getValue(ResourceLocation id);

    ResourceLocation getKey(T value);

    boolean containsKey(ResourceLocation id);

    default Set<ResourceLocation> getKeys() {
        if (this instanceof Registry<?> reg) {
            return reg.getIds();
        }
        return Collections.emptySet();
    }
}
