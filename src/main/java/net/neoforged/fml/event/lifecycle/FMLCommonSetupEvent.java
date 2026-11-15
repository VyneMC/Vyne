/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.fml.event.lifecycle;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public class FMLCommonSetupEvent {
    public CompletableFuture<Void> enqueueWork(Runnable work) {
        try {
            work.run();
        } catch (Throwable t) {
            dev.stolym.vyne.util.VyneLogger.warn("[FMLCommonSetupEvent] enqueueWork failed (mod setup task threw)", t);
        }
        return CompletableFuture.completedFuture(null);
    }

    public <T> CompletableFuture<T> enqueueWork(Callable<T> work) {
        try {
            return CompletableFuture.completedFuture(work.call());
        } catch (Throwable t) {
            dev.stolym.vyne.util.VyneLogger.warn("[FMLCommonSetupEvent] enqueueWork (Callable) failed", t);
            return CompletableFuture.completedFuture(null);
        }
    }
}
