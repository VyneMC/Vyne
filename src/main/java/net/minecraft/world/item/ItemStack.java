/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 *
 * Temporary stand-in for a Minecraft class, only here so Vyne compiles.
 * It will be removed once Vyne compiles against the real Minecraft jar with Mojang names.
 */

package net.minecraft.world.item;

public class ItemStack {
    public static final ItemStack EMPTY = new ItemStack(null);

    private final Item item;
    private int count = 1;

    public ItemStack(Item item) {
        this.item = item;
    }

    public Item getItem() {
        return item;
    }

    public boolean isEmpty() {
        return this == EMPTY || item == null || count <= 0;
    }

    public ItemStack copy() {
        ItemStack c = new ItemStack(item);
        c.count = this.count;
        return c;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
