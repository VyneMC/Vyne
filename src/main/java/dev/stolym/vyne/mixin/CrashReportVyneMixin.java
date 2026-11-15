/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.util.VyneFabricCrashFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin(targets = "net.minecraft.class_128")
public abstract class CrashReportVyneMixin {
    @ModifyReturnValue(method = "method_60920", at = @At("RETURN"), remap = false)
    private String vyne$asStringV1(String original) {
        return prependVyne(original);
    }

    @ModifyReturnValue(method = "method_60921", at = @At("RETURN"), remap = false)
    private String vyne$asStringV2(String original) {
        return prependVyne(original);
    }

    private String prependVyne(String original) {
        try {
            Object self = this;
            Throwable cause = (Throwable) self.getClass().getMethod("method_564").invoke(self);
            if (cause == null) {
                return "---- Vyne summary (no cause on CrashReport) ----\n\n" + original;
            }
            return VyneFabricCrashFormat.formatClipboard("Vyne crash summary (prepended)", cause)
                + "\n\n---- Full Minecraft report below ----\n\n"
                + original;
        } catch (Throwable t) {
            return original;
        }
    }
}
