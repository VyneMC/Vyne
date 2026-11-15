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
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.class_1255")
public abstract class BlockableEventLoopTaskContainmentMixin {
    private static final String SITE = "class_1255.method_18859";

    private static final String[] BRIDGE_FRAME_MARKERS = {
        "net.fabricmc.fabric.impl.screenhandler",
        "net.fabricmc.fabric.impl.networking",
        "net.fabricmc.fabric.api.screenhandler",
        "net.fabricmc.fabric.api.client.networking",
        "net.minecraftforge.network",
        "net.neoforged.neoforge.network",
        "com.mojang.brigadier.CommandDispatcher",
    };

    @WrapMethod(method = "method_18859")
    private void vyne$absorbTaskCrash(Runnable task, Operation<Void> original) {
        try {
            original.call(task);
        } catch (Throwable t) {
            if (!vyne$isBridgedModTaskCrash(t) || !VyneClientContainment.shouldAbsorb(t)) {
                throw t;
            }
            String taskClass = task != null ? task.getClass().getName() : "null";
            String signature = ContainmentLogThrottle.signatureOf(t, taskClass);
            if (ContainmentLogThrottle.shouldLog(SITE, signature)) {
                java.nio.file.Path detail = VyneContainmentDiagnostics.record(
                    t, "BlockableEventLoop.doRunTask:" + taskClass);
                String skipped = ContainmentLogThrottle.drainSkippedSummary(SITE);
                VyneLogger.warn("[Containment] Absorbed scheduled-task crash from mod-bridge path (task="
                    + taskClass + "):\n"
                    + VyneFabricCrashFormat.formatDeveloperSummary(t)
                    + (detail != null ? "\nFull trace: " + detail : "")
                    + (skipped != null ? "\n" + skipped : "")
                    + "\n(task was dropped; set -D" + VyneClientContainment.PROPERTY
                    + "=off to disable containment)");
            }
        }
    }

    private static boolean vyne$isBridgedModTaskCrash(Throwable t) {
        if (t == null) return false;
        Throwable cursor = t;
        int hops = 0;
        while (cursor != null && hops++ < 8) {
            StackTraceElement[] frames = cursor.getStackTrace();
            if (frames != null) {
                for (StackTraceElement f : frames) {
                    String cn = f.getClassName();
                    if (cn == null) continue;
                    for (String marker : BRIDGE_FRAME_MARKERS) {
                        if (cn.startsWith(marker)) {
                            return true;
                        }
                    }
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
