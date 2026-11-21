/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import dev.stolym.vyne.util.VyneLogger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class FabricClient {
    private FabricClient() {}

    public static boolean blockBreakReset(Object gameMode, Object oldStack, Object newStack, boolean neoForgeReset) {
        try {
            Object item = FabricEvents.call(oldStack, "method_7909");
            Method allow = overridden(item, "allowContinuingBlockBreaking", "net.fabricmc.fabric.api.item.v1.FabricItem");
            if (allow == null) return neoForgeReset;
            if ((boolean) FabricEvents.call(newStack, "method_31577", newStack, oldStack)) return false;
            Object newItem = FabricEvents.call(newStack, "method_7909");
            if (!(boolean) FabricEvents.call(oldStack, "method_31574", newItem)) return true;
            Object player = field(field(gameMode, "field_3712"), "field_1724");
            return !(boolean) allow.invoke(item, player, oldStack, newStack);
        } catch (ReflectiveOperationException | RuntimeException e) {
            VyneLogger.warn("[PatchLayer] Fabric block-breaking continuation could not be answered: " + e);
            return neoForgeReset;
        }
    }

    public static CompletableFuture<?> soundStream(Object instance, Object buffers, Object sound, boolean looping, Supplier<CompletableFuture<?>> neoForge) {
        try {
            Method stream = overridden(instance, "getAudioStream", "net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance");
            if (stream == null) return neoForge.get();
            return (CompletableFuture<?>) stream.invoke(instance, buffers, FabricEvents.call(sound, "method_4766"), looping);
        } catch (ReflectiveOperationException | RuntimeException e) {
            VyneLogger.warn("[PatchLayer] Fabric sound stream could not be opened: " + e);
            return neoForge.get();
        }
    }

    public static Object fluidSprites(Object neoForge) {
        try {
            Object info = Class.forName("net.fabricmc.fabric.impl.client.rendering.fluid.FluidRenderingImpl", true, FabricClient.class.getClassLoader())
                    .getMethod("getCurrentInfo").invoke(null);
            if (info == null || info.getClass().getField("handler").get(info) == null) return neoForge;
            return info.getClass().getField("sprites").get(info);
        } catch (ReflectiveOperationException | RuntimeException e) {
            VyneLogger.warn("[PatchLayer] Fabric fluid sprites could not be answered: " + e);
            return neoForge;
        }
    }

    public static boolean fluidOverlay(boolean neoForge, Object neighborState) {
        if (neoForge) return true;
        try {
            Class<?> registry = Class.forName("net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry", true, FabricClient.class.getClassLoader());
            Object instance = registry.getField("INSTANCE").get(null);
            Object block = FabricEvents.call(neighborState, "method_26204");
            for (Method m : registry.getMethods()) {
                if (m.getName().equals("isBlockTransparent") && m.getParameterCount() == 1) return (boolean) m.invoke(instance, block);
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            VyneLogger.warn("[PatchLayer] Fabric fluid overlay could not be answered: " + e);
            return false;
        }
    }

    private static Method overridden(Object owner, String name, String fabricInterface) {
        for (Method m : owner.getClass().getMethods()) {
            if (m.getName().equals(name) && !m.getDeclaringClass().getName().equals(fabricInterface) && !m.getDeclaringClass().isInterface()) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(owner);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(owner.getClass().getName() + "." + name);
    }
}
