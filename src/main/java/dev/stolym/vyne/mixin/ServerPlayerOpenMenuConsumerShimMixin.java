/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.compat.OpenMenuExtendedShim;
import dev.stolym.vyne.util.VyneLogger;
import net.minecraft.class_3908;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Consumer;

@Mixin(targets = "net.minecraft.class_3222")
public abstract class ServerPlayerOpenMenuConsumerShimMixin {
    public void openMenu(class_3908 provider, Consumer<?> extraDataWriter) {
        if (provider == null) {
            return;
        }
        try {
            Object wrappedRaw = OpenMenuExtendedShim.wrapAsExtended(provider, extraDataWriter);
            class_3908 wrapped = wrappedRaw instanceof class_3908 ? (class_3908) wrappedRaw : provider;
            OpenMenuExtendedShim.invokeVanillaOpenMenu(this, wrapped);
        } catch (Throwable t) {
            logOpenMenuFailure("Extended openMenu failed, falling back", t, provider);
            try {
                OpenMenuExtendedShim.invokeVanillaOpenMenu(this, provider);
            } catch (Throwable inner) {
                logOpenMenuFailure("Fallback openMenu also failed", inner, provider);
            }
        }
    }

    private void logOpenMenuFailure(String tag, Throwable t, class_3908 provider) {
        StringBuilder sb = new StringBuilder("[OpenMenuShim] ").append(tag).append(": ")
            .append(t).append('\n');
        sb.append("  provider=").append(provider.getClass().getName())
            .append(" (loader=").append(provider.getClass().getClassLoader()).append(")\n");
        for (StackTraceElement ste : t.getStackTrace()) {
            sb.append("    at ").append(ste).append('\n');
        }
        for (Throwable cause = t.getCause(); cause != null; cause = cause.getCause()) {
            sb.append("  Caused by: ").append(cause).append('\n');
            for (StackTraceElement ste : cause.getStackTrace()) {
                sb.append("    at ").append(ste).append('\n');
            }
        }
        VyneLogger.warn(sb.toString());
    }
}
