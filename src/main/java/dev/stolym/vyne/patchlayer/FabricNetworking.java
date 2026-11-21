/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class FabricNetworking {
    private FabricNetworking() {}

    public static <T> T withFabricPayloads(T codec, String registry) {
        try {
            ClassLoader loader = FabricNetworking.class.getClassLoader();
            Class<?> providerType = Class.forName("net.fabricmc.fabric.impl.networking.CustomPayloadTypeProvider", true, loader);
            Object provider = Proxy.newProxyInstance(loader, new Class<?>[] {providerType}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> "Vyne Fabric payload provider (" + registry + ")";
                    };
                }
                Object types = Class.forName("net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl", true, loader).getField(registry).get(null);
                return lookup(types).invoke(types, args[1]);
            });
            Class.forName("net.fabricmc.fabric.impl.networking.FabricCustomPayloadPacketCodec", true, loader)
                    .getMethod("fabric_setPacketCodecProvider", providerType).invoke(codec, provider);
            return codec;
        } catch (ReflectiveOperationException | RuntimeException e) {
            dev.stolym.vyne.util.VyneLogger.warn("[PatchLayer] Fabric payloads (" + registry + ") could not be added to NeoForge's codec: " + e);
            return codec;
        }
    }

    private static Method lookup(Object types) throws NoSuchMethodException {
        for (Method m : types.getClass().getMethods()) {
            if (m.getName().equals("get") && m.getParameterCount() == 1 && m.getParameterTypes()[0].getName().equals("net.minecraft.class_2960")) return m;
        }
        throw new NoSuchMethodException("PayloadTypeRegistryImpl.get");
    }
}
