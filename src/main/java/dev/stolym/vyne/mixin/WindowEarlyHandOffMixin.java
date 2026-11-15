/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */
package dev.stolym.vyne.mixin;

import dev.stolym.vyne.ui.VyneEarlyWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.class_1041")
public abstract class WindowEarlyHandOffMixin {
    @Redirect(method = "<init>", remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
    private long vyne$reuseEarlyWindow(int width, int height, CharSequence title, long monitor, long share) {
        return VyneEarlyWindow.handOff(width, height, title, monitor, share);
    }
}
