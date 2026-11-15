/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.client.ItemStackTitleCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.class_1799")
public abstract class ItemStackHoverTitleMixin {
    @Inject(method = "method_7964", at = @At("RETURN"), cancellable = true)
    private void vyne$maybeAbbreviateHoverTitle(CallbackInfoReturnable<Object> cir) {
        Object cur = cir.getReturnValue();
        Object next = ItemStackTitleCompat.maybeShortenHoverTitle(cur);
        if (next != cur) {
            cir.setReturnValue(next);
        }
    }
}
