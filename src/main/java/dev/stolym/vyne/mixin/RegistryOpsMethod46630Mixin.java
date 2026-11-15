/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import dev.stolym.vyne.registry.RegistryDatapackLookupFallback;
import dev.stolym.vyne.vcl.VCLManager;
import java.util.Optional;
import net.minecraft.class_5321;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.class_6903")
public abstract class RegistryOpsMethod46630Mixin {
    @WrapMethod(method = "method_46630")
    @SuppressWarnings("rawtypes")
    private static DataResult vyne$recoverUnknownRegistryKey(
            class_5321 registryTypeKey,
            class_5321 entryKey,
            DynamicOps ops,
            Operation<DataResult> original) {
        DataResult base = original.call(registryTypeKey, entryKey, ops);
        if (Boolean.getBoolean("vyne.registry.skipDatapackLookupFallback")) {
            return base;
        }
        if (base.result().isPresent()) {
            return base;
        }
        ClassLoader knot = VCLManager.getCanonicalKnotLoader();
        if (knot == null) {
            knot = Thread.currentThread().getContextClassLoader();
        }
        Optional<Object> holder = RegistryDatapackLookupFallback.tryResolveHolderLive(knot, entryKey);
        if (holder.isPresent()) {
            return DataResult.success(holder.get());
        }
        return base;
    }
}
