/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.stolym.vyne.registry.RegistryDatapackLookupFallback;
import dev.stolym.vyne.vcl.VCLManager;
import java.util.Optional;
import net.minecraft.class_5321;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.class_6903")
public abstract class RegistryOpsMethod46634Mixin {
    private static boolean vyne$forceLiveBuiltinEntryLookup() {
        String p = System.getProperty("vyne.registry.forceLiveBuiltinEntryLookup");
        return p == null || Boolean.parseBoolean(p);
    }

    @WrapMethod(method = "method_46634")
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Optional vyne$wrapEntryLookup(class_5321 registryRef, Operation<Optional> original) {
        if (Boolean.getBoolean("vyne.registry.skipDatapackLookupFallback")) {
            return original.call(registryRef);
        }
        ClassLoader knot = VCLManager.getCanonicalKnotLoader();
        if (knot == null) {
            knot = Thread.currentThread().getContextClassLoader();
        }
        Optional base = original.call(registryRef);
        if (base.isEmpty() && vyne$forceLiveBuiltinEntryLookup()) {
            Optional<?> live = RegistryDatapackLookupFallback.optionalLiveWrappedEntryLookupForRegistryRef(
                registryRef, knot);
            if (live.isPresent()) {
                return live;
            }
        }
        if (base.isEmpty()) {
            Optional<?> syn = RegistryDatapackLookupFallback.synthesizeWrappedRegistryEntryLookup(
                registryRef, knot);
            return syn.isPresent() ? syn : base;
        }
        Object lookup = base.get();
        Object wrapped = RegistryDatapackLookupFallback.wrapRegistryEntryLookup(lookup, knot);
        if (wrapped == lookup) {
            return base;
        }
        return Optional.of(wrapped);
    }
}
