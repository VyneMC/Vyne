/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.stolym.vyne.patchlayer.FabricSleep;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2680;
import net.minecraft.class_2769;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.class_3222")
public abstract class PatchLayerPlayerSleepMixin {
    @WrapOperation(method = "/^lambda\\$startSleepInBed\\$\\d+$/",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/class_2680;method_11654(Lnet/minecraft/class_2769;)Ljava/lang/Comparable;"))
    private Comparable<?> vyne$sleepDirection(class_2680 state, class_2769<?> property, Operation<Comparable<?>> original,
                                              @Local(argsOnly = true) class_2338 bedPos) {
        Comparable<?> value = original.call(state, property);
        if (!(value instanceof class_2350)) return value;
        return (Comparable<?>) FabricSleep.sleepDirection(this, bedPos, value);
    }
}
