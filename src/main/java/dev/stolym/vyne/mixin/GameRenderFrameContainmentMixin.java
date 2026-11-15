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
import net.minecraft.class_9779;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.class_757")
public abstract class GameRenderFrameContainmentMixin {
    private static final String SITE = "class_757.method_3192";

    @WrapMethod(method = "method_3192")
    private void vyne$containFrameRender(
        class_9779 deltaTracker,
        boolean tick,
        Operation<Void> original
    ) {
        try {
            original.call(deltaTracker, tick);
        } catch (Throwable t) {
            vyne$resetModelViewStack();
            if (!VyneClientContainment.shouldAbsorb(t)) {
                throw t;
            }
            String signature = ContainmentLogThrottle.signatureOf(t, null);
            if (ContainmentLogThrottle.shouldLog(SITE, signature)) {
                java.nio.file.Path detail = VyneContainmentDiagnostics.record(t, SITE);
                String skipped = ContainmentLogThrottle.drainSkippedSummary(SITE);
                VyneLogger.warn("[Containment] Absorbed whole-frame render throwable (safety net):\n"
                    + VyneFabricCrashFormat.formatDeveloperSummary(t)
                    + (detail != null ? "\nFull trace: " + detail : "")
                    + (skipped != null ? "\n" + skipped : "")
                    + "\nThis is the top-level catch — add a more surgical containment if this repeats.");
            }
        }
    }

    private static void vyne$resetModelViewStack() {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> renderSystem = Class.forName(
                "com.mojang.blaze3d.systems.RenderSystem", false, cl);
            java.lang.reflect.Method getModelViewStack =
                renderSystem.getMethod("getModelViewStack");
            Object stack = getModelViewStack.invoke(null);
            if (stack == null) return;

            java.lang.reflect.Method popMatrix =
                stack.getClass().getMethod("popMatrix");
            int safety = 64;
            while (safety-- > 0) {
                try {
                    popMatrix.invoke(stack);
                } catch (Throwable stop) {
                    break;
                }
            }
            try {
                stack.getClass().getMethod("identity").invoke(stack);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            VyneLogger.warn("[Containment] Failed to reset model-view matrix stack: " + t);
        }
    }
}
