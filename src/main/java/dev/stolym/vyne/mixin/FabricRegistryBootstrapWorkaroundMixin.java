/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.stolym.vyne.util.VyneLogger;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.class_7923")
public abstract class FabricRegistryBootstrapWorkaroundMixin {
    @WrapMethod(method = "method_47476")
    private static void vyne$wrapRegistriesBootstrap(Operation<Void> original) {
        try {
            original.call();
        } catch (Throwable ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("intrusive holder")) {
                VyneLogger.warn("[Registry] Allowed registry bootstrap to continue despite "
                    + "intrusive-holder validation (VCL / NeoForge → Fabric).");
                return;
            }
            throw ex;
        }
    }
}
