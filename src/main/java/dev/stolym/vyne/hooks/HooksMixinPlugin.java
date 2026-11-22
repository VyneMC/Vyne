/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks;

import dev.stolym.vyne.core.VyneMode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class HooksMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    private static final java.util.Map<String, String> FABRIC_API_HOOKS = java.util.Map.ofEntries(
            java.util.Map.entry("FabricBlockBreakHook", "fabric-events-interaction-v0"),
            java.util.Map.entry("FabricLivingSleepHook", "fabric-entity-events-v1"),
            java.util.Map.entry("FabricPlayerSleepHook", "fabric-entity-events-v1"),
            java.util.Map.entry("FabricItemHook", "fabric-item-api-v1"),
            java.util.Map.entry("FabricEnchantmentLoadHook", "fabric-item-api-v1"),
            java.util.Map.entry("FabricIngredientHook", "fabric-recipe-api-v1"),
            java.util.Map.entry("FabricServerboundPayloadHook", "fabric-networking-api-v1"),
            java.util.Map.entry("FabricClientboundPayloadHook", "fabric-networking-api-v1"),
            java.util.Map.entry("FabricMenuHook", "fabric-screen-handler-api-v1"),
            java.util.Map.entry("FabricBlockEntityUnloadHook", "fabric-lifecycle-events-v1"),
            java.util.Map.entry("FabricPackDefaultsHook", "fabric-resource-loader-v0"),
            java.util.Map.entry("FabricDynamicRegistriesHook", "fabric-registry-sync-v0"),
            java.util.Map.entry("FabricTagProviderHook", "fabric-data-generation-api-v1"),
            java.util.Map.entry("FabricScreenTickHook", "fabric-screen-api-v1"),
            java.util.Map.entry("FabricScreenRenderHook", "fabric-screen-api-v1"),
            java.util.Map.entry("FabricBlockBreakingClientHook", "fabric-item-api-v1"),
            java.util.Map.entry("FabricSoundHook", "fabric-sound-api-v1"),
            java.util.Map.entry("FabricFluidRenderHook", "fabric-rendering-fluids-v1"),
            java.util.Map.entry("FabricSectionCompilerHook", "fabric-renderer-indigo"),
            java.util.Map.entry("FabricRenderRegionHook", "fabric-block-view-api-v2"),
            java.util.Map.entry("FabricModelBakeHook", "fabric-model-loading-api-v1"),
            java.util.Map.entry("FabricParticleHook", "fabric-particles-v1"),
            java.util.Map.entry("FabricBlockDustHook", "fabric-particles-v1"));

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String module = FABRIC_API_HOOKS.get(mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1));
        if (module != null) {
            return VyneMode.patchedGame() && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(module);
        }
        return VyneMode.hooks();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
