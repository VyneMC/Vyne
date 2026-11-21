/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class FabricResources {
    private FabricResources() {}

    public static List<?> knownPacks(Object listener, List<?> serverPacks) {
        try {
            Object server = server(listener);
            List<?> original = (List<?>) Class.forName("net.fabricmc.fabric.impl.resource.loader.FabricOriginalKnownPacksGetter", true, FabricResources.class.getClassLoader())
                    .getMethod("fabric_getOriginalKnownPacks").invoke(server);
            return original.stream().filter(serverPacks::contains).toList();
        } catch (ReflectiveOperationException | RuntimeException e) {
            dev.stolym.vyne.util.VyneLogger.warn("[PatchLayer] Fabric known packs could not be filtered: " + e);
            return serverPacks;
        }
    }

    public static InputStream vanillaLanguage(String path) throws IOException {
        Optional<Path> file = FabricLoader.getInstance().getModContainer("minecraft").flatMap(container -> container.findPath(path));
        return file.isPresent() ? Files.newInputStream(file.get()) : null;
    }

    public static boolean replacedByFabric(Object tagBuilder) {
        try {
            return (boolean) Class.forName("net.fabricmc.fabric.impl.datagen.TagBuilderHooks", true, FabricResources.class.getClassLoader())
                    .getMethod("fabric_isReplaced").invoke(tagBuilder);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static Object server(Object listener) throws ReflectiveOperationException {
        for (Class<?> c = listener.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType().getName().equals("net.minecraft.server.MinecraftServer")) {
                    f.setAccessible(true);
                    return f.get(listener);
                }
            }
        }
        throw new NoSuchFieldException(listener.getClass().getName() + ".server");
    }
}
