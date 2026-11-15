/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.common.extensions;

import dev.stolym.vyne.util.VyneLogger;
import net.neoforged.neoforge.network.IContainerFactory;

public interface IForgeMenuType {
    @SuppressWarnings("unchecked")
    static <T extends net.minecraft.world.inventory.AbstractContainerMenu> net.minecraft.class_3917<T> create(
        IContainerFactory<T> factory
    ) {
        if (factory == null) {
            VyneLogger.warn("[IForgeMenuType] create(null) — menu registration will stay empty.");
            return null;
        }
        try {
            ClassLoader cl = factory.getClass().getClassLoader();
            Class<?> bridge = Class.forName("dev.stolym.vyne.compat.NeoForgeMenuTypeBridge", false, cl);
            java.lang.reflect.Method m = bridge.getMethod("create", IContainerFactory.class);
            return (net.minecraft.class_3917<T>) m.invoke(null, factory);
        } catch (Throwable t) {
            VyneLogger.warn("[IForgeMenuType] create() delegation failed: " + t);
            return null;
        }
    }
}
