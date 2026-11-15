/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.stolym.vyne.util.VyneClientContainment;
import dev.stolym.vyne.util.VyneContainmentDiagnostics;
import dev.stolym.vyne.util.VyneFabricCrashFormat;
import dev.stolym.vyne.util.VyneLogger;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.class_310")
public abstract class ClientStartUseItemCrashContainmentMixin {
    @WrapMethod(method = "method_1583")
    private void vyne$containStartUseItem(Operation<Void> original) {
        try {
            original.call();
        } catch (Throwable t) {
            if (!VyneClientContainment.shouldAbsorb(t)) {
                throw t;
            }
            java.nio.file.Path detail = VyneContainmentDiagnostics.record(t, "class_310.method_1583");
            VyneLogger.warn("[Containment] Absorbed client item-use (air/keybind):\n"
                + VyneFabricCrashFormat.formatDeveloperSummary(t)
                + (detail != null ? "\nFull trace: " + detail : "")
                + "\n(set -D" + VyneClientContainment.PROPERTY + "=strict|off to change policy)");
        }
    }
}
