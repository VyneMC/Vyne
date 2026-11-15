/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.util.VyneKeyPressContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_309")
public abstract class KeyboardHandlerKeyPressContextMixin {
    @Inject(method = "method_1466(JIIII)V", at = @At("HEAD"))
    private void vyne$pushKeyPressContext(long window, int key, int scancode,
                                          int action, int modifiers, CallbackInfo ci) {
        VyneKeyPressContext.push(key, scancode, modifiers);
    }

    @Inject(method = "method_1466(JIIII)V", at = @At("RETURN"))
    private void vyne$popKeyPressContext(long window, int key, int scancode,
                                         int action, int modifiers, CallbackInfo ci) {
        VyneKeyPressContext.pop();
    }
}
