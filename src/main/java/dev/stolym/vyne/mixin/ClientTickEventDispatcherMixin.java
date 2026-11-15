/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */
package dev.stolym.vyne.mixin;

import dev.stolym.vyne.event.VyneTickDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_310")
public abstract class ClientTickEventDispatcherMixin {
    @Inject(method = "method_1574", at = @At("HEAD"))
    private void vyne$clientTickPre(CallbackInfo ci) {
        VyneTickDispatcher.fireClientTickPre();
    }

    @Inject(method = "method_1574", at = @At("RETURN"))
    private void vyne$clientTickPost(CallbackInfo ci) {
        VyneTickDispatcher.fireClientTickPost();
    }
}
