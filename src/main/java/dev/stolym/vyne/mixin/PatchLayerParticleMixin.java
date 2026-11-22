/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.patchlayer.FabricParticles;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.class_2960;
import net.minecraft.class_707;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(targets = "net.minecraft.class_702", priority = 500)
public abstract class PatchLayerParticleMixin {
    @Shadow
    @Final
    private Map<class_2960, class_707<?>> providers;

    private final Int2ObjectMap<class_707<?>> vyne$fabricParticleFactories = FabricParticles.rawIdView(() -> providers);
}
