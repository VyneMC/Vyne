/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.stolym.vyne.hooks.fabric.FabricSleepHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public abstract class FabricPlayerSleepHook {
    @WrapOperation(method = "lambda:startSleepInBed", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getValue(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;"))
    private Comparable<?> vyne$sleepDirection(BlockState state, Property<?> property, Operation<Comparable<?>> original, @Local(argsOnly = true) BlockPos bedPos) {
        Comparable<?> value = original.call(state, property);
        return value instanceof Direction direction ? FabricSleepHooks.sleepDirection((ServerPlayer) (Object) this, bedPos, direction) : value;
    }
}
