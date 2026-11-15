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
import net.minecraft.class_6880;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.class_2378")
public interface RegistryMethod40290Mixin {
    @WrapMethod(method = "method_40290")
    @SuppressWarnings("rawtypes")
    default class_6880.class_6883 vyne$getHolderOrThrow(class_5321 key,
            Operation<class_6880.class_6883> original) {
        try {
            return original.call(key);
        } catch (IllegalStateException ex) {
            String msg = ex.getMessage();
            if (msg == null || !msg.contains("Unknown registry key")) {
                throw ex;
            }
            ClassLoader knot = VCLManager.getCanonicalKnotLoader();
            if (knot == null) {
                knot = Thread.currentThread().getContextClassLoader();
            }
            Object self = this;
            Optional<Object> live = RegistryDatapackLookupFallback.lenientHolderAfterMappedRegistryMissForKey(
                self, knot, key);
            if (live.isEmpty()) {
                live = RegistryDatapackLookupFallback.tryDeferredRegisterBlockHolderForMappedRegistry(
                    self, knot, key);
            }
            if (live.isPresent()) {
                Object h = live.get();
                if (RegistryDatapackLookupFallback.registryHolderIsReference(h, knot)) {
                    return (class_6880.class_6883) h;
                }
            }
            throw ex;
        }
    }
}
