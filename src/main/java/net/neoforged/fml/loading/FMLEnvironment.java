/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 *
 * Vyne shim: NeoForge mods read {@link FMLEnvironment#dist} via {@code GETSTATIC} at link time.
 * We resolve "am I on a client?" lazily from Fabric's runtime marker so both integrated-server
 * (client) and dedicated-server launches return the correct value without a static init race.
 */

package net.neoforged.fml.loading;

import net.neoforged.api.distmarker.Dist;

public final class FMLEnvironment {
    public static final Dist dist = detectDist();

    public static final String naming = "mojang";

    public static final boolean production = true;

    public static final boolean secureJarsEnabled = false;

    private FMLEnvironment() {}

    private static Dist detectDist() {
        try {
            Class<?> loader = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object inst = loader.getMethod("getInstance").invoke(null);
            Object env = loader.getMethod("getEnvironmentType").invoke(inst);
            if (env != null && "SERVER".equals(env.toString())) {
                return Dist.DEDICATED_SERVER;
            }
        } catch (Throwable ignored) {
        }
        return Dist.CLIENT;
    }
}
