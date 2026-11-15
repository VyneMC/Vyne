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
import dev.stolym.vyne.util.VyneLinkageContainment;
import dev.stolym.vyne.util.VyneLogger;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_3965;
import net.minecraft.class_746;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.class_636")
public abstract class ClientInteractionCrashContainmentMixin {
    @WrapMethod(method = "method_2896")
    private class_1269 vyne$containItemUse(
        class_746 player,
        class_1268 hand,
        class_3965 hitResult,
        Operation<class_1269> original
    ) {
        try {
            return original.call(player, hand, hitResult);
        } catch (Throwable t) {
            if (!VyneClientContainment.shouldAbsorb(t)) {
                throw t;
            }
            java.nio.file.Path detail = VyneContainmentDiagnostics.record(t, "class_636.method_2896");
            VyneLogger.warn("[Containment] Absorbed client interaction (item/block use):\n"
                + VyneFabricCrashFormat.formatDeveloperSummary(t)
                + (detail != null ? "\nFull trace: " + detail : "")
                + "\n(set -D" + VyneClientContainment.PROPERTY + "=strict|off to change policy)");
            Object pass = VyneLinkageContainment.interactionResultPass(Thread.currentThread().getContextClassLoader());
            return (class_1269) pass;
        }
    }
}
