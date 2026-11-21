/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stolym.vyne.patchlayer.FabricSleep;
import net.minecraft.class_1299;
import net.minecraft.class_1309;
import net.minecraft.class_1937;
import net.minecraft.class_1941;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2680;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(targets = "net.minecraft.class_1309")
public abstract class PatchLayerLivingSleepMixin {
    @WrapOperation(method = {"method_18403", "/^lambda\\$stopSleeping\\$\\d+$/"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/class_2680;setBedOccupied(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_1309;Z)V"))
    private void vyne$setBedOccupied(class_2680 state, class_1937 level, class_2338 pos, class_1309 entity, boolean occupied, Operation<Void> original) {
        if (!FabricSleep.occupationHandled(entity, pos, state, occupied)) original.call(state, level, pos, entity, occupied);
    }

    @WrapOperation(method = "/^lambda\\$stopSleeping\\$\\d+$/",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/class_2244;method_9484(Lnet/minecraft/class_1299;Lnet/minecraft/class_1941;Lnet/minecraft/class_2338;Lnet/minecraft/class_2350;F)Ljava/util/Optional;"))
    private Optional<?> vyne$wakeUpPosition(class_1299 type, class_1941 level, class_2338 pos, class_2350 direction, float yaw, Operation<Optional<?>> original) {
        Optional<?> vanilla = original.call(type, level, pos, direction, yaw);
        return FabricSleep.wakeUpPosition(this, pos, level, vanilla);
    }

    @Inject(method = "method_18401", at = @At("RETURN"), cancellable = true)
    private void vyne$sleepDirection(CallbackInfoReturnable<class_2350> cir) {
        cir.setReturnValue((class_2350) FabricSleep.sleepDirection(this, cir.getReturnValue()));
    }
}
