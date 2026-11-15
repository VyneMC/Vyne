/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.event.VyneGameplayDispatcher;
import net.minecraft.class_1297;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.class_1937")
public abstract class EntityJoinLevelDispatcherMixin {
    @Inject(method = "method_8649", at = @At("HEAD"), require = 0)
    private void vyne$fireEntityJoin(class_1297 entity, CallbackInfoReturnable<Boolean> cir) {
        VyneGameplayDispatcher.fireEntityJoinLevel(entity, this);
    }
}
