/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.vyne.fixture.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.MinecraftServer")
public class MinecraftServerMixin {
    @Inject(method = "runServer", at = @At("HEAD"))
    private void vyne$onRunServer(CallbackInfo ci) {
        System.out.println("[VyneFixture] mixin injected into MinecraftServer.runServer");
    }
}
