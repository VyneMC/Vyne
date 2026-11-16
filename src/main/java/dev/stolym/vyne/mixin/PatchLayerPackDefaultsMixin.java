/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.function.Predicate;

@Mixin(targets = "net.minecraft.class_3288")
public abstract class PatchLayerPackDefaultsMixin {
    @Inject(method = "<init>*", at = @At("RETURN"))
    private void vyne$defaultParentsPredicate(CallbackInfo ci) {
        try {
            Field field = getClass().getDeclaredField("parentsPredicate");
            field.setAccessible(true);
            if (field.get(this) == null) {
                Predicate<Set<String>> always = parents -> true;
                field.set(this, always);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
