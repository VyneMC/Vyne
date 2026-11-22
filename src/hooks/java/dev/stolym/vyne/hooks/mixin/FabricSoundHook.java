/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;

@Mixin(SoundEngine.class)
public abstract class FabricSoundHook {
    @WrapOperation(method = "play", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getStream(Lnet/minecraft/client/sounds/SoundBufferLibrary;Lnet/minecraft/client/resources/sounds/Sound;Z)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<AudioStream> vyne$stream(SoundInstance instance, SoundBufferLibrary buffers, Sound sound, boolean looping,
                                                      Operation<CompletableFuture<AudioStream>> original) {
        if (!vyne$overrides(instance)) return original.call(instance, buffers, sound, looping);
        return ((FabricSoundInstance) instance).getAudioStream(buffers, sound.getPath(), looping);
    }

    private static boolean vyne$overrides(SoundInstance instance) {
        try {
            return instance.getClass().getMethod("getAudioStream", SoundBufferLibrary.class, ResourceLocation.class, boolean.class)
                    .getDeclaringClass() != FabricSoundInstance.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}