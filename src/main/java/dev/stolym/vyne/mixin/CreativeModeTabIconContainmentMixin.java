/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */
package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.stolym.vyne.util.ContainmentLogThrottle;
import dev.stolym.vyne.util.VyneClientContainment;
import dev.stolym.vyne.util.VyneContainmentDiagnostics;
import dev.stolym.vyne.util.VyneFabricCrashFormat;
import dev.stolym.vyne.util.VyneLogger;
import net.minecraft.class_1799;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.class_1761")
public abstract class CreativeModeTabIconContainmentMixin {
    private static final String SITE = "class_1761.method_7747";

    @WrapMethod(method = "method_7747")
    private class_1799 vyne$containIconSupplier(Operation<class_1799> original) {
        try {
            return original.call();
        } catch (Throwable t) {
            if (!VyneClientContainment.shouldAbsorb(t)) {
                throw t;
            }
            String signature = ContainmentLogThrottle.signatureOf(t, this.getClass().getName());
            if (ContainmentLogThrottle.shouldLog(SITE, signature)) {
                java.nio.file.Path detail = VyneContainmentDiagnostics.record(t, SITE);
                String skipped = ContainmentLogThrottle.drainSkippedSummary(SITE);
                VyneLogger.warn("[Containment] Creative tab icon threw — falling back to ItemStack.EMPTY:\n"
                    + VyneFabricCrashFormat.formatDeveloperSummary(t)
                    + (detail != null ? "\nFull trace: " + detail : "")
                    + (skipped != null ? "\n" + skipped : ""));
            }
            return class_1799.field_8037;
        }
    }
}
