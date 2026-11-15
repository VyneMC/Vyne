/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */
package dev.stolym.vyne.mixin;

import net.minecraft.class_1657;
import net.minecraft.class_1747;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_3414;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.class_1747")
public abstract class BlockItemForgeExtensionsMixin {
    @Shadow protected abstract class_3414 method_19260(class_2680 state);

    public class_3414 getPlaceSound(class_2680 state, class_1937 level, class_2338 pos, class_1657 player) {
        return method_19260(state);
    }
}
