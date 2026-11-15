/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 *
 * Temporary stand-in for a Minecraft class, only here so Vyne compiles.
 * It will be removed once Vyne compiles against the real Minecraft jar with Mojang names.
 */

package net.minecraft.client.renderer;

public class BlockEntityWithoutLevelRenderer {
    public BlockEntityWithoutLevelRenderer() {}

    public BlockEntityWithoutLevelRenderer(Object blockEntityRenderDispatcher, Object entityModelSet) {}

    public void renderByItem(Object itemStack, Object displayContext, Object poseStack,
            Object multiBufferSource, int packedLight, int packedOverlay) {
    }

    public void render(Object itemStack, Object displayContext, Object poseStack,
            Object multiBufferSource, int packedLight, int packedOverlay) {
        renderByItem(itemStack, displayContext, poseStack, multiBufferSource, packedLight, packedOverlay);
    }
}
