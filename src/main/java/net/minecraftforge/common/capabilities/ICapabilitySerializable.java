/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.common.capabilities;

import net.minecraftforge.common.util.INBTSerializable;

public interface ICapabilitySerializable<T> extends ICapabilityProvider, INBTSerializable<T> {
}
