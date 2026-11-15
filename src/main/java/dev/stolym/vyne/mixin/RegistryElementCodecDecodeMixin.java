/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import dev.stolym.vyne.registry.RegistryDatapackLookupFallback;
import dev.stolym.vyne.vcl.VCLManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.class_5381")
public abstract class RegistryElementCodecDecodeMixin {
    @WrapMethod(method = "decode")
    @SuppressWarnings("rawtypes")
    private DataResult vyne$decode(DynamicOps ops, Object input, Operation<DataResult> original) {
        ClassLoader knot = VCLManager.getCanonicalKnotLoader();
        if (knot == null) {
            knot = Thread.currentThread().getContextClassLoader();
        }
        RegistryDatapackLookupFallback.enterRegistryElementCodecDecodeContext(this, knot);
        try {
            DataResult r;
            try {
                r = original.call(ops, input);
            } catch (Throwable decodeEx) {
                if (Boolean.getBoolean("vyne.registry.skipRegistryElementCodecDecodeRecover")) {
                    throw decodeEx;
                }
                DataResult<?> recovered = RegistryDatapackLookupFallback.recoverAfterRegistryElementDecodeFailure(
                    this, ops, input,
                    DataResult.error(() -> "vyne-registry-element-decode", decodeEx),
                    knot,
                    decodeEx);
                if (recovered != null && recovered.result().isPresent()) {
                    return recovered;
                }
                throw decodeEx;
            }
            if (Boolean.getBoolean("vyne.registry.skipRegistryElementCodecDecodeRecover")) {
                return r;
            }
            if (r != null && r.result().isPresent()) {
                return r;
            }
            return RegistryDatapackLookupFallback.recoverAfterRegistryElementDecodeFailure(
                this, ops, input, r, knot, null);
        } finally {
            RegistryDatapackLookupFallback.leaveRegistryElementCodecDecodeContext();
        }
    }
}
