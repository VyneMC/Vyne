/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */
package dev.stolym.vyne.mixin;

import dev.stolym.vyne.core.VyneLauncher;
import dev.stolym.vyne.world.WorldModGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_3176")
public abstract class DedicatedServerMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void vyne$onServerInit(CallbackInfo ci) {
        WorldModGuard.verifyAndSnapshot(this);
    }
}
