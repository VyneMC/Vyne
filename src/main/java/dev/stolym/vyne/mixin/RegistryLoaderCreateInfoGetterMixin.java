/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.stolym.vyne.registry.RegistryDatapackLookupFallback;
import dev.stolym.vyne.vcl.VCLManager;
import java.util.List;
import net.minecraft.class_5455;
import net.minecraft.class_6903;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.class_7655")
public abstract class RegistryLoaderCreateInfoGetterMixin {
    @WrapMethod(method = "method_46619")
    private static class_6903.class_7863 vyne$wrapCreateInfoGetter(
            class_5455 baseRegistryManager,
            List<?> additionalRegistries,
            Operation<class_6903.class_7863> original) {
        class_6903.class_7863 delegate = original.call(baseRegistryManager, additionalRegistries);
        ClassLoader knot = VCLManager.getCanonicalKnotLoader();
        if (knot == null) {
            knot = Thread.currentThread().getContextClassLoader();
        }
        return (class_6903.class_7863) RegistryDatapackLookupFallback.wrapRegistryInfoGetter(delegate, knot);
    }
}
