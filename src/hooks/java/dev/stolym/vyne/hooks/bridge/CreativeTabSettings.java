/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.bridge;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface CreativeTabSettings {
    void vyne$applySettings(@Nullable ResourceLocation scrollerSprite, boolean hasSearchBar, int searchBarWidth,
                            ResourceLocation tabsImage, int labelColor, int slotColor,
                            List<ResourceLocation> tabsBefore, List<ResourceLocation> tabsAfter);
}
