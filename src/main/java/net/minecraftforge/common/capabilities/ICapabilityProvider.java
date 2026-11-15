/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.common.capabilities;

import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

public interface ICapabilityProvider {
    <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable net.minecraft.class_2350 side);

    default <T> LazyOptional<T> getCapability(Capability<T> cap) {
        return getCapability(cap, null);
    }
}
