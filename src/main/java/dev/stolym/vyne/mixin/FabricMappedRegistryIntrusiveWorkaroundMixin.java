/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.stolym.vyne.util.ContainmentLogThrottle;
import dev.stolym.vyne.util.VyneLogger;
import net.minecraft.class_2378;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.class_2370")
public abstract class FabricMappedRegistryIntrusiveWorkaroundMixin {
    private static final String THROTTLE_SITE_FREEZE = "registry.freeze.intrusive";

    @WrapMethod(method = "method_40276")
    private class_2378<?> vyne$wrapFreeze(Operation<class_2378<?>> original) {
        try {
            return original.call();
        } catch (Throwable ex) {
            String msg = ex.getMessage();
            if (msg != null && (msg.contains("intrusive holders") || msg.contains("Unbound values"))) {
                if (ContainmentLogThrottle.shouldLog(THROTTLE_SITE_FREEZE, msg.contains("Unbound") ? "unbound-values" : "intrusive-holders")) {
                    VyneLogger.warn("[Registry] Allowed registry.freeze() to continue despite "
                        + (msg.contains("Unbound") ? "unbound values" : "intrusive holders") 
                        + " (Mod Interop). Message: " + msg);
                }
                @SuppressWarnings("unchecked")
                class_2378<?> self = (class_2378<?>)(Object)this;
                return self;
            }
            throw ex;
        }
    }
}
