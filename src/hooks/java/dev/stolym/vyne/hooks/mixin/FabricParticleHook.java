/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin;

import dev.stolym.vyne.hooks.fabric.FabricParticleProviders;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(value = ParticleEngine.class, priority = 500)
public abstract class FabricParticleHook {
    @Shadow(remap = false) @Final private Map<ResourceLocation, ParticleProvider<?>> providers;

    private final Int2ObjectMap<ParticleProvider<?>> vyne$fabricParticleFactories = FabricParticleProviders.rawIdView(() -> providers);
}