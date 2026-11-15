/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DyeColor.class)
public abstract class DyeColorTagHooks {
    @Shadow @Final private String name;
    @Shadow @Final @Mutable private TagKey<Item> dyesTag;
    @Shadow @Final @Mutable private TagKey<Item> dyedTag;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void vyne$createTags(CallbackInfo ci) {
        this.dyesTag = ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "dyes/" + this.name));
        this.dyedTag = ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "dyed/" + this.name));
    }
}
