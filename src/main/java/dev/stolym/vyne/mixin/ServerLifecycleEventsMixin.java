/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.event.ServerLifecycleDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.MinecraftServer")
public abstract class ServerLifecycleEventsMixin {
    @Inject(method = "method_3735", at = @At("HEAD"), require = 0)
    private void vyne$aboutToStart(CallbackInfo ci) {
        ServerLifecycleDispatcher.aboutToStart(this);
    }

    @Inject(method = "method_3735", at = @At("RETURN"), require = 0)
    private void vyne$starting(CallbackInfo ci) {
        ServerLifecycleDispatcher.starting(this);
    }

    @Inject(method = "method_3748", at = @At("HEAD"), require = 0)
    private void vyne$firstTick(CallbackInfo ci) {
        ServerLifecycleDispatcher.firstTick(this);
    }

    @Inject(method = "method_3782", at = @At("HEAD"), require = 0)
    private void vyne$stopping(CallbackInfo ci) {
        ServerLifecycleDispatcher.stopping(this);
    }

    @Inject(method = "method_3782", at = @At("RETURN"), require = 0)
    private void vyne$stopped(CallbackInfo ci) {
        ServerLifecycleDispatcher.stopped(this);
    }
}
