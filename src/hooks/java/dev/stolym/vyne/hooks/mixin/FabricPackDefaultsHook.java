/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin;

import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.function.Predicate;

@Mixin(value = Pack.class, priority = 1100)
public abstract class FabricPackDefaultsHook {
    @Shadow(remap = false) @Final private static Predicate<Set<String>> DEFAULT_PARENT_PREDICATE;
    @Shadow(remap = false) private Predicate<Set<String>> parentsPredicate;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void vyne$defaultParentsPredicate(CallbackInfo ci) {
        if (parentsPredicate == null) parentsPredicate = DEFAULT_PARENT_PREDICATE;
    }
}
