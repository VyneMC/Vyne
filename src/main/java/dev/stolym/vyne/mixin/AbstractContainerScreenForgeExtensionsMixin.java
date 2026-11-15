/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */
package dev.stolym.vyne.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.class_465")
public abstract class AbstractContainerScreenForgeExtensionsMixin {
    @Shadow protected int field_2776;
    @Shadow protected int field_2800;
    @Shadow protected int field_2792;
    @Shadow protected int field_2779;

    public int getGuiLeft() { return field_2776; }
    public int getGuiTop()  { return field_2800; }
    public int getXSize()   { return field_2792; }
    public int getYSize()   { return field_2779; }
}
