/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin;

import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.WorldLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(WorldLoader.class)
public abstract class FabricDynamicRegistriesHook {
    @ModifyArg(method = "load", index = 3, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/WorldLoader;loadAndReplaceLayer(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/core/LayeredRegistryAccess;Lnet/minecraft/server/RegistryLayer;Ljava/util/List;)Lnet/minecraft/core/LayeredRegistryAccess;"))
    private static List<RegistryDataLoader.RegistryData<?>> vyne$mergeDynamicRegistries(List<RegistryDataLoader.RegistryData<?>> neoForge) {
        List<RegistryDataLoader.RegistryData<?>> merged = new ArrayList<>(neoForge);
        Set<ResourceKey<?>> keys = new HashSet<>();
        for (RegistryDataLoader.RegistryData<?> entry : neoForge) keys.add(entry.key());
        for (RegistryDataLoader.RegistryData<?> entry : DynamicRegistries.getDynamicRegistries()) {
            if (keys.add(entry.key())) merged.add(entry);
        }
        return merged;
    }
}
