/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.fml;

import net.neoforged.api.distmarker.Dist;

public final class DistExecutor {
    private DistExecutor() {}

    public static boolean isClient() {
        return net.neoforged.fml.loading.FMLEnvironment.dist.isClient();
    }

    public static boolean isServer() {
        return net.neoforged.fml.loading.FMLEnvironment.dist.isDedicatedServer();
    }

    public static void safeRunWhenOn(Dist dist, java.util.function.Supplier<Runnable> toRun) {
        if (shouldRun(dist)) {
            toRun.get().run();
        }
    }

    public static void unsafeRunWhenOn(Dist dist, java.util.function.Supplier<Runnable> toRun) {
        safeRunWhenOn(dist, toRun);
    }

    public static <T> T safeCallWhenOn(Dist dist, java.util.function.Supplier<java.util.function.Supplier<T>> toRun) {
        return shouldRun(dist) ? toRun.get().get() : null;
    }

    public static <T> T unsafeCallWhenOn(Dist dist, java.util.function.Supplier<java.util.function.Supplier<T>> toRun) {
        return safeCallWhenOn(dist, toRun);
    }

    private static boolean shouldRun(Dist dist) {
        return dist != null && net.neoforged.fml.loading.FMLEnvironment.dist == dist;
    }
}
