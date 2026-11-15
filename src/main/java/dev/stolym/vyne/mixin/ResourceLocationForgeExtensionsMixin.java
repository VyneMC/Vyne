/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */
package dev.stolym.vyne.mixin;

import net.minecraft.class_2960;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.class_2960")
public abstract class ResourceLocationForgeExtensionsMixin {
    @Shadow public abstract String method_12836(); 
    @Shadow public abstract String method_12832(); 

    public int compareNamespaced(class_2960 other) {
        int ns = method_12836().compareTo(other.method_12836());
        if (ns != 0) return ns;
        return method_12832().compareTo(other.method_12832());
    }
}
