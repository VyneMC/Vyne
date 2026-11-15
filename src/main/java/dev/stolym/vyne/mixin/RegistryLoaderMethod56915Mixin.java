/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.serialization.Decoder;
import dev.stolym.vyne.registry.RegistryDatapackLookupFallback;
import dev.stolym.vyne.util.VyneLogger;
import dev.stolym.vyne.registry.VyneRegistryInjector;
import dev.stolym.vyne.vcl.VCLManager;
import java.io.IOException;
import net.minecraft.class_2385;
import net.minecraft.class_3298;
import net.minecraft.class_5321;
import net.minecraft.class_6903;
import net.minecraft.class_9248;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.class_7655")
public abstract class RegistryLoaderMethod56915Mixin {
    static {
        System.err.println("[Vyne-Recovery] RegistryLoaderMethod56915Mixin loaded!");
    }

    @WrapMethod(method = "method_56915")
    private static <E> void vyne$recoverUnknownKeyOnDecode(
            class_2385<E> registry,
            Decoder<E> decoder,
            class_6903<JsonElement> ops,
            class_5321<E> elementKey,
            class_3298 resource,
            class_9248 registrationInfo,
            Operation<Void> original) throws IOException {
        if (Boolean.getBoolean("vyne.registry.skipRegistryLoader56915ProactiveSync")) {
            original.call(registry, decoder, ops, elementKey, resource, registrationInfo);
            return;
        }
        ClassLoader knot = VCLManager.getCanonicalKnotLoader();
        if (knot == null) {
            knot = Thread.currentThread().getContextClassLoader();
        }
        final ClassLoader cl = knot;

        String path = VyneRegistryInjector.getRegistryPath(registry);
        if (path != null) {
            VyneRegistryInjector.syncAllTrackedGenericEntriesToLiveRegistry(cl);
        }
        class_6903<JsonElement> decodeOps = vyne$augmentDecodeOps(ops, cl, elementKey);

        try {
            ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(cl);
                original.call(registry, decoder, decodeOps, elementKey, resource, registrationInfo);
            } finally {
                Thread.currentThread().setContextClassLoader(originalTccl);
            }
        } catch (IllegalStateException e) {
            String m = e.getMessage();
            if (m == null || (!m.contains("Unknown registry key") 
                            && !m.contains("Failed to get element") 
                            && !m.contains("Trying to access unbound value"))) {
                sneakyThrow(e);
                return;
            }

            VyneLogger.info("[Vyne-Recovery] Caught registry error during decode of " + elementKey + ": " + e.getMessage());

            int injected = VyneRegistryInjector.reactiveInjectMissingKeys(e, registry, cl);
            if (injected == 0 && !m.contains("Trying to access unbound value")) {
                VyneLogger.warn("[Vyne-Recovery] Reactive inject found 0 entr(y/ies) for missing keys. "
                        + "Hard-stopping to preserve world integrity. Error: " + e.getMessage());
                sneakyThrow(e);
                return;
            }

            try {
                decodeOps = vyne$augmentDecodeOps(decodeOps, cl, elementKey);

                ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
                try {
                    Thread.currentThread().setContextClassLoader(cl);
                    original.call(registry, decoder, decodeOps, elementKey, resource, registrationInfo);
                } finally {
                    Thread.currentThread().setContextClassLoader(originalTccl);
                }
            } catch (IllegalStateException e2) {
                String m2 = e2.getMessage();
                if (m2 != null && (m2.contains("Trying to access unbound value") || m2.contains("Unknown registry key") || m2.contains("Failed to parse"))) {
                    VyneLogger.warn("[Vyne-Recovery] Retry encountered error: " + m2
                        + ". Continuing despite this error to allow world load (Mod Interop / Late Registration).");
                    return;
                }
                VyneLogger.warn("[Vyne-Recovery] Retry still failed after injecting " + injected
                        + " key(s). Continuing anyway. Error: " + e2.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static class_6903<JsonElement> vyne$augmentDecodeOps(
            class_6903<JsonElement> ops, ClassLoader cl, class_5321<?> elementKey) {
        Object augmented = RegistryDatapackLookupFallback.augmentRegistryOpsJsonForDecode(ops, cl);
        if (augmented instanceof class_6903<?> augmentedOps) {
            if (augmentedOps != ops) {
                VyneLogger.info("[Vyne-Recovery] Using augmented RegistryOps for decode of " + elementKey);
            }
            return (class_6903<JsonElement>) augmentedOps;
        }
        return ops;
    }

    private static void sneakyThrow(Throwable t) throws IOException {
        if (t instanceof IOException ioe) {
            throw ioe;
        }
        if (t instanceof RuntimeException re) {
            throw re;
        }
        if (t instanceof Error e) {
            throw e;
        }
        throw new RuntimeException(t);
    }
}
