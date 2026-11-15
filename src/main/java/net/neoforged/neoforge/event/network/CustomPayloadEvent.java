/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Stub for NeoForge networking; Fabric path does not ship this event type.
 */

package net.neoforged.neoforge.event.network;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class CustomPayloadEvent {
    private CustomPayloadEvent() {}

    public static final class Context {
        public CompletableFuture<Void> enqueueWork(Runnable runnable) {
            if (runnable != null) {
                runnable.run();
            }
            return CompletableFuture.completedFuture(null);
        }

        public <T> CompletableFuture<T> enqueueWork(Supplier<T> work) {
            try {
                return CompletableFuture.completedFuture(work == null ? null : work.get());
            } catch (Throwable t) {
                CompletableFuture<T> f = new CompletableFuture<>();
                f.completeExceptionally(t);
                return f;
            }
        }
    }
}
