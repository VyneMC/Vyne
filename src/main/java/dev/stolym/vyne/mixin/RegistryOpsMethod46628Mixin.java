/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.stolym.vyne.registry.RegistryDatapackLookupFallback;
import dev.stolym.vyne.registry.VyneRegistryInjector;
import dev.stolym.vyne.util.ReflectionHelper;
import dev.stolym.vyne.vcl.VCLManager;
import java.util.Optional;
import net.minecraft.class_5321;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.class_6903")
public abstract class RegistryOpsMethod46628Mixin {
    @WrapMethod(method = "method_46628")
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Optional vyne$preferLiveOwner(class_5321 registryRef, Operation<Optional> original) {
        if (Boolean.getBoolean("vyne.registry.skipDatapackLookupFallback")) {
            return original.call(registryRef);
        }
        ClassLoader knot = VCLManager.getCanonicalKnotLoader();
        if (knot == null) {
            knot = Thread.currentThread().getContextClassLoader();
        }
        Optional base = original.call(registryRef);
        String raw = ReflectionHelper.extractRegistryKeyLocation(registryRef);
        String norm = raw == null ? null : ReflectionHelper.normalizeRegistryKeyLocationString(raw);
        if (norm == null || !RegistryDatapackLookupFallback.isDatapackAugmentBuiltinPath(norm)) {
            return base;
        }
        Object live = VyneRegistryInjector.resolveRegistryForPath(knot, norm);
        if (live == null) {
            return base;
        }
        return Optional.of(live);
    }
}
