/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.minecraftforge.fml;

import dev.stolym.vyne.util.VyneLogger;

public final class ModLoadingContext {
    private static final ModLoadingContext INSTANCE = new ModLoadingContext();
    private String activeModId = "unknown";

    public static ModLoadingContext get() {
        return INSTANCE;
    }

    public String getActiveId() {
        return activeModId;
    }

    public void setActiveContainer(Object container) {
        if (container instanceof String s) {
            this.activeModId = s;
        } else {
             try {
                 this.activeModId = container.getClass().getMethod("getModId").invoke(container).toString();
             } catch (Throwable t) {
                 this.activeModId = container.toString();
             }
        }
        VyneLogger.info("[VYNE-FML-Forge] ModLoadingContext active mod set to: " + activeModId);
    }

    public void registerConfig(net.minecraftforge.fml.config.ModConfig.Type type, net.minecraftforge.fml.config.IConfigSpec spec) {
        registerConfig(type, spec, null);
    }

    public void registerConfig(net.minecraftforge.fml.config.ModConfig.Type type, net.minecraftforge.fml.config.IConfigSpec spec, String fileName) {
        String mod = !"unknown".equals(activeModId) ? activeModId : getActiveModIdFromFML();
        String resolvedFile = fileName != null ? fileName : (mod + "-" + type.name().toLowerCase() + ".toml");
        VyneLogger.info("[VYNE-FML-Forge] Registering config for " + mod + " (" + type + ") -> " + resolvedFile);
        dev.stolym.vyne.config.VyneConfigBridge.register(mod, type, spec, resolvedFile);
    }

    private String getActiveModIdFromFML() {
        try {
            Class<?> fml = Class.forName("net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext");
            java.lang.reflect.Field f = fml.getDeclaredField("activeModId");
            f.setAccessible(true);
            Object v = f.get(null);
            if (v != null) return v.toString();
        } catch (Throwable ignored) {}
        return "unknown";
    }
}
