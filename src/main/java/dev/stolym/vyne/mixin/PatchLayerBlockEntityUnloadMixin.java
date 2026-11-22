/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stolym.vyne.patchlayer.FabricInteraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

@Mixin(targets = "net.minecraft.class_2818")
public abstract class PatchLayerBlockEntityUnloadMixin {
    @WrapOperation(method = "method_12201", at = @At(value = "INVOKE", ordinal = 0, target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object vyne$unloadRemoved(Map<?, ?> blockEntities, Object pos, Operation<Object> original) {
        Object removed = original.call(blockEntities, pos);
        FabricInteraction.blockEntityUnloaded(this, removed);
        return removed;
    }
}
