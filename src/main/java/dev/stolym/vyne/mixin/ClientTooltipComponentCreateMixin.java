/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.client.VyneTooltipComponentRegistry;
import net.minecraft.class_5632;
import net.minecraft.class_5684;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_5684.class)
public interface ClientTooltipComponentCreateMixin {
    @Inject(
        method = "method_32663(Lnet/minecraft/class_5632;)Lnet/minecraft/class_5684;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void vyne$consultBridgeRegistry(
        class_5632 visual,
        CallbackInfoReturnable<class_5684> cir
    ) {
        try {
            Object raw = VyneTooltipComponentRegistry.tryCreateOrFallback(visual);
            if (raw instanceof class_5684) {
                cir.setReturnValue((class_5684) raw);
            }
        } catch (Throwable t) {
            VyneTooltipComponentRegistry.reportConsultFailure(t);
        }
    }
}
