/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;

@Mixin(targets = "net.minecraft.class_2591$class_2592")
public abstract class BlockEntityPacketTypeSetMixin {
    @ModifyArg(
        method = "method_20528",
        at = @At(
            value = "INVOKE",
            target = "Lcom/google/common/collect/ImmutableSet;copyOf([Ljava/lang/Object;)Lcom/google/common/collect/ImmutableSet;"
        ),
        index = 0,
        require = 1
    )
    private static Object[] vyne$stripNullBlockTypes(Object[] blocks) {
        if (blocks == null || blocks.length == 0) {
            return blocks == null ? new Object[0] : blocks;
        }
        boolean sawNull = false;
        for (Object o : blocks) {
            if (o == null) {
                sawNull = true;
                break;
            }
        }
        if (!sawNull) {
            return blocks;
        }
        ArrayList<Object> out = new ArrayList<>(blocks.length);
        for (Object o : blocks) {
            if (o != null) {
                out.add(o);
            }
        }
        return out.toArray();
    }
}
