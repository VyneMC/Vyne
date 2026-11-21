/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class VCLMixinConfigPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    private static final java.util.Map<String, String> PATCH_LAYER_MIXINS = java.util.Map.ofEntries(
            java.util.Map.entry("PatchLayerBlockBreakMixin", "fabric-events-interaction-v0"),
            java.util.Map.entry("PatchLayerPackDefaultsMixin", "fabric-resource-loader-v0"),
            java.util.Map.entry("PatchLayerDynamicRegistriesMixin", "fabric-registry-sync-v0"),
            java.util.Map.entry("PatchLayerLivingSleepMixin", "fabric-entity-events-v1"),
            java.util.Map.entry("PatchLayerPlayerSleepMixin", "fabric-entity-events-v1"),
            java.util.Map.entry("PatchLayerItemMixin", "fabric-item-api-v1"),
            java.util.Map.entry("PatchLayerRegistryLoaderMixin", "fabric-item-api-v1"),
            java.util.Map.entry("PatchLayerIngredientMixin", "fabric-recipe-api-v1"),
            java.util.Map.entry("PatchLayerServerboundPayloadMixin", "fabric-networking-api-v1"),
            java.util.Map.entry("PatchLayerClientboundPayloadMixin", "fabric-networking-api-v1"),
            java.util.Map.entry("PatchLayerMenuMixin", "fabric-screen-handler-api-v1"),
            java.util.Map.entry("PatchLayerKnownPacksMixin", "fabric-resource-loader-v0"),
            java.util.Map.entry("PatchLayerLanguageMixin", "fabric-resource-loader-v0"),
            java.util.Map.entry("PatchLayerTagProviderMixin", "fabric-data-generation-api-v1"),
            java.util.Map.entry("PatchLayerScreenTickMixin", "fabric-screen-api-v1"),
            java.util.Map.entry("PatchLayerScreenRenderMixin", "fabric-screen-api-v1"),
            java.util.Map.entry("PatchLayerBlockBreakingClientMixin", "fabric-item-api-v1"),
            java.util.Map.entry("PatchLayerSoundMixin", "fabric-sound-api-v1"),
            java.util.Map.entry("PatchLayerFluidRenderMixin", "fabric-rendering-fluids-v1"),
            java.util.Map.entry("PatchLayerSectionCompilerMixin", "fabric-renderer-indigo"),
            java.util.Map.entry("PatchLayerRenderRegionMixin", "fabric-block-view-api-v2"),
            java.util.Map.entry("PatchLayerModelBakeMixin", "fabric-model-loading-api-v1"));

    private static final java.util.Set<String> PATCHED_GAME_MIXINS = java.util.Set.of(
            "LoadingOverlayVyneMixin",
            "CrashReportVyneMixin",
            "PackRepositoryVyneMixin",
            "DedicatedServerMixin",
            "ServerMainMixin");

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        String requiredModule = PATCH_LAYER_MIXINS.get(simpleName);
        if (requiredModule != null) {
            return dev.stolym.vyne.core.VyneMode.hooks()
                    && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(requiredModule);
        }
        if (!dev.stolym.vyne.core.VyneMode.patchedGame() || !mixinClassName.startsWith("dev.stolym.vyne.mixin.")) {
            return true;
        }
        return PATCHED_GAME_MIXINS.contains(mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1));
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
