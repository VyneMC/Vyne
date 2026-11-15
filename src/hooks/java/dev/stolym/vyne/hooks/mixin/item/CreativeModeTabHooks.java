/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin.item;

import dev.stolym.vyne.hooks.bridge.CreativeTabSettings;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(CreativeModeTab.class)
public abstract class CreativeModeTabHooks implements CreativeTabSettings {
    private static final ResourceLocation VYNE$TABS_IMAGE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/creative_inventory/tabs.png");

    @Shadow @Final private CreativeModeTab.Type type;
    @Shadow @Final @Mutable @Nullable private ResourceLocation scrollerSpriteLocation;
    @Shadow @Final @Mutable private boolean hasSearchBar;
    @Shadow @Final @Mutable private int searchBarWidth;
    @Shadow @Final @Mutable private ResourceLocation tabsImage;
    @Shadow @Final @Mutable private int labelColor;
    @Shadow @Final @Mutable private int slotColor;
    @Shadow @Final @Mutable public List<ResourceLocation> tabsBefore;
    @Shadow @Final @Mutable public List<ResourceLocation> tabsAfter;

    @Inject(method = "<init>(Lnet/minecraft/world/item/CreativeModeTab$Row;ILnet/minecraft/world/item/CreativeModeTab$Type;Lnet/minecraft/network/chat/Component;Ljava/util/function/Supplier;Lnet/minecraft/world/item/CreativeModeTab$DisplayItemsGenerator;)V",
            at = @At("RETURN"))
    private void vyne$defaults(CallbackInfo ci) {
        vyne$applySettings(null, this.type == CreativeModeTab.Type.SEARCH, 89, VYNE$TABS_IMAGE, 4210752, -2130706433,
                List.of(), List.of());
    }

    @Override
    public void vyne$applySettings(@Nullable ResourceLocation scrollerSprite, boolean hasSearchBar, int searchBarWidth,
                                   ResourceLocation tabsImage, int labelColor, int slotColor,
                                   List<ResourceLocation> tabsBefore, List<ResourceLocation> tabsAfter) {
        this.scrollerSpriteLocation = scrollerSprite;
        this.hasSearchBar = hasSearchBar;
        this.searchBarWidth = searchBarWidth;
        this.tabsImage = tabsImage;
        this.labelColor = labelColor;
        this.slotColor = slotColor;
        this.tabsBefore = List.copyOf(tabsBefore);
        this.tabsAfter = List.copyOf(tabsAfter);
    }

    @Redirect(method = "buildContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/CreativeModeTab$DisplayItemsGenerator;accept(Lnet/minecraft/world/item/CreativeModeTab$ItemDisplayParameters;Lnet/minecraft/world/item/CreativeModeTab$Output;)V"))
    private void vyne$buildContentsEvent(CreativeModeTab.DisplayItemsGenerator generator,
                                         CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        CreativeModeTab self = (CreativeModeTab) (Object) this;
        ResourceKey<CreativeModeTab> key = BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(self)
                .orElseThrow(() -> new IllegalStateException("Unregistered creative tab: " + self));
        EventHooks.onCreativeModeTabBuildContents(self, key, generator, parameters, output);
    }
}
