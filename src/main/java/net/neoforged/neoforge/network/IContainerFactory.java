/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.network;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.FriendlyByteBuf;

@FunctionalInterface
public interface IContainerFactory<T extends AbstractContainerMenu> {
    T create(int windowId, net.minecraft.world.entity.player.Inventory inv, FriendlyByteBuf data);
}
