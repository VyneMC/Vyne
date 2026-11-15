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

import java.util.ArrayList;

@Mixin(targets = "net.minecraft.class_442")
public abstract class TitleScreenModsButtonMixin {
    @Inject(method = "method_25426", at = @At("TAIL"))
    private void vyne$replaceRealmsButton(CallbackInfo ci) {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu")) return;
        class_437 self = (class_437) (Object) this;
        class_2561 realms = class_2561.method_43471("menu.online");
        for (Object child : new ArrayList<>(self.method_25396())) {
            if (!(child instanceof class_4185 button) || !realms.equals(button.method_25369())) continue;
            self.method_37066(button);
            self.method_37063(class_4185.method_46430(class_2561.method_43470("Mods"),
                            b -> class_310.method_1551().method_1507(new dev.stolym.vyne.game.VyneModListScreen(self)))
                    .method_46434(button.method_46426(), button.method_46427(), button.method_25368(), button.method_25364())
                    .method_46431());
            return;
        }
    }
}
