/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.fml;

public class ModLoadingContext {
    private static final ModLoadingContext INSTANCE = new ModLoadingContext();
    private String activeModId = "minecraft";

    public static ModLoadingContext get() {
        return INSTANCE;
    }

    public String getActiveId() {
        String loading = ModList.currentlyLoadingModId();
        return loading != null ? loading : activeModId;
    }

    public String getActiveNamespace() {
        return getActiveId();
    }

    public ModContainer getActiveContainer() {
        String id = getActiveId();
        return ModList.get().getModContainerById(id).orElseGet(() -> new ModContainer(id));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public <T extends IExtensionPoint> void registerExtensionPoint(Class<? extends IExtensionPoint> point,
                                                                    java.util.function.Supplier<T> extension) {
        getActiveContainer().registerExtensionPoint((Class) point, (java.util.function.Supplier) extension);
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
        System.out.println("[VYNE-FML] ModLoadingContext active mod set to: " + activeModId);
    }

    public void registerConfig(Object type, Object spec, String fileName) {
        String id = getActiveId();
        String resolvedFile = fileName != null ? fileName : (id + "-" + type + ".toml");
        dev.stolym.vyne.config.VyneConfigBridge.register(id, type, spec, resolvedFile);
    }

    public void registerConfig(Object type, Object spec) {
        registerConfig(type, spec, null);
    }
}
