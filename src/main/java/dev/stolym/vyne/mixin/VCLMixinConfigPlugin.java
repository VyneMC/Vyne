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

    private static final java.util.Set<String> PATCHED_GAME_MIXINS = java.util.Set.of(
            "LoadingOverlayVyneMixin",
            "CrashReportVyneMixin",
            "PackRepositoryVyneMixin",
            "DedicatedServerMixin");

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
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
