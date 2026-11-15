/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.fml.javafmlmod;

import net.neoforged.bus.api.IEventBus;

public final class FMLJavaModLoadingContext {
    private static final FMLJavaModLoadingContext INSTANCE = new FMLJavaModLoadingContext();
    private static IEventBus currentBus;
    private static Object neoForgeCurrentBus;
    private static String activeModId;

    static {
        try {
            Object bus = net.neoforged.bus.api.VyneEventBus.global();
            if (bus instanceof IEventBus legacy) {
                currentBus = legacy;
            }
            neoForgeCurrentBus = bus;
        } catch (Throwable ignored) { }
    }

    public static void setActiveBus(IEventBus bus) {
        currentBus = bus;
    }

    public static void setActiveBus(net.neoforged.neoforge.eventbus.api.IEventBus bus) {
        if (bus instanceof IEventBus) {
            currentBus = (IEventBus) bus;
        }
    }

    public static void setActiveBus(Object bus) {
        neoForgeCurrentBus = bus;
        if (bus instanceof IEventBus) {
            currentBus = (IEventBus) bus;
        }
    }

    public static void setActiveModId(String modId) {
        activeModId = modId;
    }

    public static FMLJavaModLoadingContext get() {
        return INSTANCE;
    }

    public net.neoforged.neoforge.eventbus.api.IEventBus getModEventBus() {
        net.neoforged.neoforge.eventbus.api.IEventBus bus = null;
        if (currentBus instanceof net.neoforged.neoforge.eventbus.api.IEventBus neobus) {
            bus = neobus;
        } else if (neoForgeCurrentBus instanceof net.neoforged.neoforge.eventbus.api.IEventBus neobus) {
            bus = neobus;
        } else {
            try {
                bus = net.neoforged.bus.api.VyneEventBus.global();
            } catch (Throwable t) {}
        }
        dev.stolym.vyne.util.VyneLogger.info("[FMLCtx] getModEventBus for " + (activeModId != null ? activeModId : "unknown") + " returns " + (bus != null ? bus.getClass().getName() : "null"));
        return bus;
    }

    public void registerConfig(net.neoforged.neoforge.fml.config.ModConfig.Type type,
                               net.neoforged.neoforge.fml.config.IConfigSpec spec) {
        dev.stolym.vyne.config.VyneConfigBridge.register(
            activeModId != null ? activeModId : "unknown",
            type, spec,
            (activeModId != null ? activeModId : "unknown")
                + "-" + type.name().toLowerCase() + ".toml");
    }

    public void registerConfig(net.neoforged.neoforge.fml.config.ModConfig.Type type,
                               net.neoforged.neoforge.fml.config.IConfigSpec spec,
                               String fileName) {
        dev.stolym.vyne.config.VyneConfigBridge.register(
            activeModId != null ? activeModId : "unknown", type, spec, fileName);
    }
}
