/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */
package dev.stolym.vyne.mixin;

import dev.stolym.vyne.game.VyneLoadingOverlay;
import net.minecraft.class_2960;
import net.minecraft.class_332;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.IntSupplier;

@Mixin(targets = "net.minecraft.class_425")
public abstract class LoadingOverlayVyneMixin {
    @Unique
    private long vyne$animationStart = -1L;

    @Unique
    private float[] vyne$handoffLogo;

    @Redirect(method = "method_25394",
            at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
    private int vyne$background(IntSupplier original) {
        return VyneLoadingOverlay.background();
    }

    @Redirect(method = "method_25394",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/class_332;method_25293(Lnet/minecraft/class_2960;IIIIFFIIII)V"))
    private void vyne$logo(class_332 graphics, class_2960 texture, int x, int y, int width, int height,
                           float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        if (v > 1f) {
            if (!VyneLoadingOverlay.isActive()) {
                graphics.method_25293(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (vyne$animationStart < 0L) {
            vyne$animationStart = now;
            vyne$handoffLogo = dev.stolym.vyne.ui.VyneLoadingScreen.consumeHandoffLogo();
        }
        if (!VyneLoadingOverlay.drawLogo(graphics, x, y, width, height, now - vyne$animationStart, vyne$handoffLogo)) {
            graphics.method_25293(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        }
    }
}
