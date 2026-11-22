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

    private static final java.util.Map<String, String> FABRIC_API_HOOKS = java.util.Map.of(
            "FabricBlockBreakHook", "fabric-events-interaction-v0");

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
