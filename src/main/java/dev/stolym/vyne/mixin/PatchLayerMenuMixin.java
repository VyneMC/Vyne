/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.stolym.vyne.patchlayer.FabricMenus;
import net.minecraft.class_1703;
import net.minecraft.class_2596;
import net.minecraft.class_3244;
import net.minecraft.class_3908;
import net.minecraft.class_8710;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.class_3222")
public abstract class PatchLayerMenuMixin {
    @WrapOperation(method = "openMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_3908;shouldTriggerClientSideContainerClosingOnOpen()Z"))
    private boolean vyne$closeCurrentScreen(class_3908 provider, Operation<Boolean> original) {
        return original.call(provider) && FabricMenus.shouldCloseCurrentScreen(provider);
    }

    @WrapOperation(method = "openMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_3244;method_14364(Lnet/minecraft/class_2596;)V"))
    private void vyne$openPacket(class_3244 connection, class_2596<?> packet, Operation<Void> original,
                                 @Local(argsOnly = true) class_3908 provider, @Local class_1703 menu) {
        if (!FabricMenus.openedByFabric(this, provider, menu)) original.call(connection, packet);
    }

    @WrapOperation(method = "openMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_3244;send(Lnet/minecraft/class_8710;)V"))
    private void vyne$openPayload(class_3244 connection, class_8710 payload, Operation<Void> original,
                                  @Local(argsOnly = true) class_3908 provider, @Local class_1703 menu) {
        if (!FabricMenus.openedByFabric(this, provider, menu)) original.call(connection, payload);
    }
}
