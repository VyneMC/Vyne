/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.common.util;

import net.minecraft.core.HolderLookup;

public interface INBTSerializable<T> {
    T serializeNBT(HolderLookup.Provider provider);

    void deserializeNBT(HolderLookup.Provider provider, T tag);

    default T serializeNBT() { return serializeNBT((HolderLookup.Provider) null); }

    default void deserializeNBT(T tag) { deserializeNBT((HolderLookup.Provider) null, tag); }
}
