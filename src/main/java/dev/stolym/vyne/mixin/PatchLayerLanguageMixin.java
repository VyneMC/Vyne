/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stolym.vyne.patchlayer.FabricResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.IOException;
import java.io.InputStream;

@Mixin(targets = "net.minecraft.class_2477")
public abstract class PatchLayerLanguageMixin {
    @WrapOperation(method = "parseTranslations", at = @At(value = "INVOKE", target = "Ljava/lang/Class;getResourceAsStream(Ljava/lang/String;)Ljava/io/InputStream;"))
    private static InputStream vyne$vanillaLanguage(Class<?> owner, String path, Operation<InputStream> original) throws IOException {
        InputStream vanilla = FabricResources.vanillaLanguage(path);
        return vanilla != null ? vanilla : original.call(owner, path);
    }
}
