/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.patchlayer.FabricItems;
import net.minecraft.class_1309;
import net.minecraft.class_1799;
import net.minecraft.class_6880;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Consumer;

@Mixin(targets = "net.minecraft.class_1792")
public abstract class PatchLayerItemMixin {
    public class_1799 getCraftingRemainingItem(class_1799 stack) {
        return (class_1799) FabricItems.remainder(this, stack);
    }

    public boolean hasCraftingRemainingItem(class_1799 stack) {
        return FabricItems.hasRemainder(this, stack);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public int damageItem(class_1799 stack, int amount, class_1309 entity, Consumer onBroken) {
        return FabricItems.damage(this, stack, amount, entity, onBroken);
    }

    public boolean supportsEnchantment(class_1799 stack, class_6880 enchantment) {
        return FabricItems.canEnchant(stack, enchantment, false);
    }

    public boolean isPrimaryItemFor(class_1799 stack, class_6880 enchantment) {
        return FabricItems.canEnchant(stack, enchantment, true);
    }
}
