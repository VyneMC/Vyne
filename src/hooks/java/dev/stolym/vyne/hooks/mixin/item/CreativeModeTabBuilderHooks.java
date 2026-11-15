/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin.item;

import dev.stolym.vyne.hooks.bridge.CreativeTabSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(CreativeModeTab.Builder.class)
public abstract class CreativeModeTabBuilderHooks {
    @Shadow private CreativeModeTab.Type type;
    @Shadow @Nullable private ResourceLocation spriteScrollerLocation;
    @Shadow private boolean hasSearchBar;
    @Shadow private int searchBarWidth;
    @Shadow private ResourceLocation tabsImage;
    @Shadow private int labelColor;
    @Shadow private int slotColor;
    @Shadow @Final private List<ResourceLocation> tabsBefore;
    @Shadow @Final private List<ResourceLocation> tabsAfter;

    @Inject(method = "build", at = @At("RETURN"))
    private void vyne$applyNeoForgeSettings(CallbackInfoReturnable<CreativeModeTab> cir) {
        ((CreativeTabSettings) cir.getReturnValue()).vyne$applySettings(this.spriteScrollerLocation,
                this.hasSearchBar || this.type == CreativeModeTab.Type.SEARCH, this.searchBarWidth, this.tabsImage,
                this.labelColor, this.slotColor, this.tabsBefore, this.tabsAfter);
    }
}
