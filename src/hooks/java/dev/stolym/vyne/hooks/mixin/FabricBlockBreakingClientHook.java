/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MultiPlayerGameMode.class)
public abstract class FabricBlockBreakingClientHook {
    @Shadow @Final private Minecraft minecraft;

    @WrapOperation(method = "sameDestroyTarget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;shouldCauseBlockBreakReset(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean vyne$blockBreakReset(ItemStack oldStack, ItemStack newStack, Operation<Boolean> original) {
        boolean neoForgeReset = original.call(oldStack, newStack);
        Item item = oldStack.getItem();
        if (!vyne$overrides(item)) return neoForgeReset;
        if (ItemStack.isSameItemSameComponents(newStack, oldStack)) return false;
        if (!oldStack.is(newStack.getItem())) return true;
        Player player = minecraft.player;
        return !((FabricItem) item).allowContinuingBlockBreaking(player, oldStack, newStack);
    }

    private static boolean vyne$overrides(Item item) {
        try {
            return item.getClass().getMethod("allowContinuingBlockBreaking", Player.class, ItemStack.class, ItemStack.class).getDeclaringClass() != FabricItem.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}