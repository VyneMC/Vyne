/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;
import java.util.regex.Pattern;

@Mixin(targets = "net.minecraft.class_2754")
public abstract class EnumPropertyValueNameNormalizeMixin {
    private static final Pattern MC_PROPERTY_SEGMENT = Pattern.compile("^[a-z0-9_]+$");

    @Inject(method = "method_11901", at = @At("RETURN"), cancellable = true)
    private void vyne$normalizeEnumValueName(Comparable<?> comparable, CallbackInfoReturnable<String> cir) {
        String s = cir.getReturnValue();
        if (s == null || s.isEmpty()) {
            return;
        }
        if (MC_PROPERTY_SEGMENT.matcher(s).matches()) {
            return;
        }
        String low = s.toLowerCase(Locale.ROOT);
        if (MC_PROPERTY_SEGMENT.matcher(low).matches()) {
            cir.setReturnValue(low);
        }
    }
}
