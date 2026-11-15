/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */
package dev.stolym.vyne.mixin;

import dev.stolym.vyne.client.ClientModEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_315")
public abstract class OptionsKeyMappingsMixin {
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_315;method_1636()V"))
    private void vyne$registerModKeyMappings(CallbackInfo ci) {
        ClientModEvents.fireRegisterKeyMappings(this);
    }
}
