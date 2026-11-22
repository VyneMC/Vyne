/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin;

import dev.stolym.vyne.hooks.fabric.FabricItemHooks;
import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.fabricmc.fabric.api.item.v1.FabricItemStack;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Consumer;

@Mixin(Item.class)
public abstract class FabricItemHook {
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return ((FabricItem) this).getRecipeRemainder(stack);
    }

    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return !((FabricItem) this).getRecipeRemainder(stack).isEmpty();
    }

    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return ((FabricItemStack) (Object) stack).canBeEnchantedWith(enchantment, EnchantingContext.ACCEPTABLE);
    }

    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return ((FabricItemStack) (Object) stack).canBeEnchantedWith(enchantment, EnchantingContext.PRIMARY);
    }

    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<Item> onBroken) {
        return FabricItemHooks.damage((Item) (Object) this, stack, amount, entity, onBroken);
    }
}
