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
import dev.stolym.vyne.util.VyneKeyPressContext;
import dev.stolym.vyne.util.VyneLogger;
import net.minecraft.class_332;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.class_437")
public abstract class ScreenCrashContainmentMixin {
    private static final String SITE_HOOK = "class_437.method_25412";
    private static final String SITE_RENDER = "class_437.method_25394";

    @WrapMethod(method = "method_25412")
    private static void vyne$containScreenHookCrash(
        Runnable task,
        String title,
        String screenName,
        Operation<Void> original
    ) {
        try {
            original.call(task, title, screenName);
        } catch (Throwable t) {
            if (VyneClientContainment.shouldAbsorb(t)) {
                String signature = ContainmentLogThrottle.signatureOf(t, screenName);
                if (ContainmentLogThrottle.shouldLog(SITE_HOOK, signature)) {
                    java.nio.file.Path detail = VyneContainmentDiagnostics.record(t, "Screen.method_25412:" + screenName);
                    String skipped = ContainmentLogThrottle.drainSkippedSummary(SITE_HOOK);
                    VyneLogger.warn("[Containment] Suppressed screen hook crash (screen=" + screenName + " title=" + title + "):\n"
                        + VyneFabricCrashFormat.formatDeveloperSummary(t)
                        + (detail != null ? "\nFull trace: " + detail : "")
                        + (skipped != null ? "\n" + skipped : ""));
                }

                if (title != null
                        && title.contains("keyPressed")
                        && VyneKeyPressContext.isEscape()) {
                    vyne$forceCloseCurrentScreenIfSafe();
                }
                return;
            }
            throw t;
        }
    }

    @WrapMethod(method = "method_25394")
    private void vyne$containScreenRender(
        class_332 guiGraphics,
        int mouseX,
        int mouseY,
        float partialTick,
        Operation<Void> original
    ) {
        try {
            original.call(guiGraphics, mouseX, mouseY, partialTick);
        } catch (Throwable t) {
            vyne$resetModelViewStack();
            if (!VyneClientContainment.shouldAbsorb(t)) {
                throw t;
            }
            String screenClass = this.getClass().getName();
            String signature = ContainmentLogThrottle.signatureOf(t, screenClass);
            if (ContainmentLogThrottle.shouldLog(SITE_RENDER, signature)) {
                java.nio.file.Path detail = VyneContainmentDiagnostics.record(t, "Screen.method_25394:" + screenClass);
                String skipped = ContainmentLogThrottle.drainSkippedSummary(SITE_RENDER);
                VyneLogger.warn("[Containment] Absorbed screen render frame (class="
                    + screenClass + "):\n"
                    + VyneFabricCrashFormat.formatDeveloperSummary(t)
                    + (detail != null ? "\nFull trace: " + detail : "")
                    + (skipped != null ? "\n" + skipped : "")
                    + "\n(set -D" + VyneClientContainment.PROPERTY + "=strict|off to change policy)");
            }
        }
    }

    private static void vyne$forceCloseCurrentScreenIfSafe() {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = ScreenCrashContainmentMixin.class.getClassLoader();
            Class<?> minecraftCls = Class.forName("net.minecraft.class_310", false, cl);
            Object mc = minecraftCls.getMethod("method_1551").invoke(null);
            if (mc == null) return;
            java.lang.reflect.Field screenField = minecraftCls.getField("field_1755");
            Object screen = screenField.get(mc);
            if (screen == null) return;
            Class<?> screenCls = Class.forName("net.minecraft.class_437", false, cl);
            screenCls.getMethod("method_25419").invoke(screen);
        } catch (Throwable closeErr) {
            VyneLogger.warn("[Containment] ESC escape-hatch failed to close screen: "
                + closeErr);
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
        } catch (Throwable ignored) {
        }
    }
}
