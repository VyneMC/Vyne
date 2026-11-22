/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stolym.vyne.hooks.fabric.FabricSleepHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(LivingEntity.class)
public abstract class FabricLivingSleepHook {
    @WrapOperation(method = {"startSleeping", "lambda:stopSleeping"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;setBedOccupied(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;Z)V"))
    private void vyne$setBedOccupied(BlockState state, Level level, BlockPos pos, LivingEntity entity, boolean occupied, Operation<Void> original) {
        if (!FabricSleepHooks.occupationHandled(entity, pos, state, occupied)) original.call(state, level, pos, entity, occupied);
    }

    @WrapOperation(method = "lambda:stopSleeping", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/BedBlock;findStandUpPosition(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;F)Ljava/util/Optional;"))
    private Optional<Vec3> vyne$wakeUpPosition(EntityType<?> type, CollisionGetter level, BlockPos pos, Direction direction, float yaw,
                                              Operation<Optional<Vec3>> original) {
        return FabricSleepHooks.wakeUpPosition((LivingEntity) (Object) this, pos, level, original.call(type, level, pos, direction, yaw));
    }

    @Inject(method = "getBedOrientation", at = @At("RETURN"), cancellable = true)
    private void vyne$sleepDirection(CallbackInfoReturnable<Direction> cir) {
        cir.setReturnValue(FabricSleepHooks.sleepDirection((LivingEntity) (Object) this, cir.getReturnValue()));
    }
}
