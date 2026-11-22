/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.fabric;

import net.fabricmc.fabric.api.item.v1.CustomDamageHandler;
import net.fabricmc.fabric.impl.item.EnchantmentUtil;
import net.fabricmc.fabric.impl.item.ItemExtensions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.function.Consumer;

public final class FabricItemHooks {
    private static final ThreadLocal<Resource> LOADING_FROM = new ThreadLocal<>();

    private FabricItemHooks() {}

    public static int damage(Item item, ItemStack stack, int amount, LivingEntity entity, Consumer<Item> onBroken) {
        if (entity == null) return amount;
        CustomDamageHandler handler = ((ItemExtensions) item).fabric_getCustomDamageHandler();
        if (handler == null || entity.hasInfiniteMaterials()) return amount;
        EquipmentSlot slot = EquipmentSlot.MAINHAND;
        for (EquipmentSlot candidate : EquipmentSlot.values()) {
            if (entity.getItemBySlot(candidate) == stack) {
                slot = candidate;
                break;
            }
        }
        return handler.damage(stack, amount, entity, slot, () -> {
            stack.shrink(1);
            onBroken.accept(item);
        });
    }

    public static void loadingFrom(Resource resource) {
        if (resource == null) LOADING_FROM.remove();
        else LOADING_FROM.set(resource);
    }

    @SuppressWarnings("unchecked")
    public static Object modifyEnchantment(ResourceKey<?> key, Object value) {
        Resource resource = LOADING_FROM.get();
        if (resource == null || !(value instanceof Enchantment enchantment)) return value;
        return EnchantmentUtil.modify((ResourceKey<Enchantment>) key, enchantment, EnchantmentUtil.determineSource(resource));
    }
}
