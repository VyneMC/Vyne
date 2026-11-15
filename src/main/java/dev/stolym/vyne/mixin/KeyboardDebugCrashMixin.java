/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stolym.vyne.client.VyneDebugCrashHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.class_309")
public abstract class KeyboardDebugCrashMixin {
    @WrapOperation(
        method = "method_1474",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/class_310;method_1494(Lnet/minecraft/class_128;)V"
        ),
        remap = false,
        require = 0
    )
    private void vyne$wrap1494(Object client, Object report, Operation<Void> original) {
        vyne$maybeIntercept(client, report, original);
    }

    @WrapOperation(
        method = "method_1474",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/class_310;method_43587(Lnet/minecraft/class_128;)V"
        ),
        remap = false,
        require = 0
    )
    private void vyne$wrap43587(Object client, Object report, Operation<Void> original) {
        vyne$maybeIntercept(client, report, original);
    }

    @WrapOperation(
        method = "method_1474",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/class_310;method_54580(Lnet/minecraft/class_128;)V"
        ),
        remap = false,
        require = 0
    )
    private void vyne$wrap54580(Object client, Object report, Operation<Void> original) {
        vyne$maybeIntercept(client, report, original);
    }

    private void vyne$maybeIntercept(Object client, Object report, Operation<Void> original) {
        if (!Boolean.parseBoolean(System.getProperty("vyne.debugCrash.intercept", "true"))) {
            original.call();
            return;
        }
        try {
            String msg = (String) report.getClass().getMethod("method_561").invoke(report);
            if (msg != null && msg.contains("Manually triggered debug crash")) {
                VyneDebugCrashHandler.handle(client, (Object) this, new Throwable(msg));
                return;
            }
        } catch (Throwable ignored) {
        }
        original.call();
    }
}
