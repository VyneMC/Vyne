/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin.server;

import net.minecraft.server.Main;
import net.neoforged.neoforge.server.loading.ServerModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public abstract class MainHooks {
    @Inject(method = "main", at = @At(value = "NEW", target = "net/minecraft/server/dedicated/DedicatedServerSettings"))
    private static void vyne$loadMods(String[] args, CallbackInfo ci) {
        ServerModLoader.load();
    }
}
