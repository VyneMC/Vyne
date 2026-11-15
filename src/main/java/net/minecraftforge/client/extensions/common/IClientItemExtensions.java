/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.minecraftforge.client.extensions.common;

public interface IClientItemExtensions {
    default net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return null;
    }

    default Object getHumanoidArmorModel(Object livingEntity, Object itemStack,
            Object equipmentSlot, Object original) {
        return original;
    }

    default Object getArmPose(Object entity, Object itemInHand, Object usedHand) {
        return null;
    }
}
