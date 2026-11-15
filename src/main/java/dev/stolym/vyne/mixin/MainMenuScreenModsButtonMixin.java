/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */
package dev.stolym.vyne.mixin;

import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_4185;
import net.minecraft.class_437;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_442")
public abstract class MainMenuScreenModsButtonMixin {
    @Inject(method = "method_25426", at = @At("TAIL"))
    private void vyne$addModsButton(CallbackInfo ci) {
        class_437 self = (class_437) (Object) this;
        self.method_37063(class_4185.method_46430(class_2561.method_43470("Mods..."),
                        b -> class_310.method_1551().method_1507(new dev.stolym.vyne.game.VyneModListScreen(self)))
                .method_46434(self.field_22789 - 86, 6, 80, 20).method_46431());
    }
}