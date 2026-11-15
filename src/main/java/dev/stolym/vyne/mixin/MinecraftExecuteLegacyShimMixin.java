/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the Licenses/LICENSE_VYNE file in the project root.
 */
package dev.stolym.vyne.mixin;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.class_310")
public abstract class MinecraftExecuteLegacyShimMixin {
    public void method_22812(Runnable task) {
        ((net.minecraft.class_1255) (Object) this).method_18859(task);
    }
}
