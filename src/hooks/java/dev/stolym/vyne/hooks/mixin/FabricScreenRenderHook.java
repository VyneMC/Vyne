/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class FabricScreenRenderHook {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;drawScreen(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void vyne$renderScreen(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float delta, Operation<Void> original) {
        ScreenEvents.beforeRender(screen).invoker().beforeRender(screen, graphics, mouseX, mouseY, delta);
        original.call(screen, graphics, mouseX, mouseY, delta);
        ScreenEvents.afterRender(screen).invoker().afterRender(screen, graphics, mouseX, mouseY, delta);
    }
}