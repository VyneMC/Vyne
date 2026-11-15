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
import net.minecraft.class_2586;
import net.minecraft.class_4587;
import net.minecraft.class_4597;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.class_824")
public abstract class BlockEntityRenderContainmentMixin {
    private static final String SITE = "class_824.method_3555";

    @WrapMethod(method = "method_3555")
    private <E extends class_2586> void vyne$containBlockEntityRender(
        E blockEntity,
        float partialTick,
        class_4587 poseStack,
        class_4597 bufferSource,
        Operation<Void> original
    ) {
        try {
            original.call(blockEntity, partialTick, poseStack, bufferSource);
        } catch (Throwable t) {
            if (!VyneClientContainment.shouldAbsorb(t)) {
                throw t;
            }
            String beClass = blockEntity != null ? blockEntity.getClass().getName() : "null";
            String signature = ContainmentLogThrottle.signatureOf(t, beClass);
            if (ContainmentLogThrottle.shouldLog(SITE, signature)) {
                java.nio.file.Path detail = VyneContainmentDiagnostics.record(t, SITE + ":" + beClass);
                String skipped = ContainmentLogThrottle.drainSkippedSummary(SITE);
                VyneLogger.warn("[Containment] Absorbed block-entity render (be=" + beClass + "):\n"
                    + VyneFabricCrashFormat.formatDeveloperSummary(t)
                    + (detail != null ? "\nFull trace: " + detail : "")
                    + (skipped != null ? "\n" + skipped : "")
                    + "\n(set -D" + VyneClientContainment.PROPERTY + "=strict|off to change policy)");
            }
        }
    }
}
