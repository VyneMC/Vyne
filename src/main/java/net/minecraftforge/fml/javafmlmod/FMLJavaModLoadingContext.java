/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.minecraftforge.fml.javafmlmod;

import net.minecraftforge.eventbus.api.IEventBus;

public final class FMLJavaModLoadingContext {
    private static final FMLJavaModLoadingContext INSTANCE = new FMLJavaModLoadingContext();
    private static IEventBus currentBus;
    private static Object neoForgeCurrentBus;
    private static String activeModId;

    static {
        try {
            currentBus = net.neoforged.bus.api.VyneEventBus.global();
        } catch (Throwable ignored) {
        }
    }

    public static void setActiveModId(String modId) {
        activeModId = modId;
    }

    public static void setActiveBus(IEventBus bus) {
        currentBus = bus;
    }

    public static void setActiveBus(Object bus) {
        neoForgeCurrentBus = bus;
        if (bus instanceof IEventBus) {
            currentBus = (IEventBus) bus;
        }
    }

    public static FMLJavaModLoadingContext get() {
        return INSTANCE;
    }

    public net.neoforged.neoforge.eventbus.api.IEventBus getModEventBus() {
        if (currentBus instanceof net.neoforged.neoforge.eventbus.api.IEventBus neo) {
            return neo;
        }

        try {
            return net.neoforged.bus.api.VyneEventBus.global();
        } catch (Throwable t) {
            return null;
        }
    }

    public void registerConfig(net.minecraftforge.fml.config.ModConfig.Type type,
                               net.minecraftforge.fml.config.IConfigSpec spec) {
        String mod = activeModId != null ? activeModId : "unknown";
        dev.stolym.vyne.config.VyneConfigBridge.register(mod, type, spec,
            mod + "-" + type.name().toLowerCase() + ".toml");
    }

    public void registerConfig(net.minecraftforge.fml.config.ModConfig.Type type,
                               net.minecraftforge.fml.config.IConfigSpec spec,
                               String fileName) {
        String mod = activeModId != null ? activeModId : "unknown";
        dev.stolym.vyne.config.VyneConfigBridge.register(mod, type, spec, fileName);
    }
}
