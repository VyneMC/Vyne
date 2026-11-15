/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.compat;

import dev.stolym.vyne.util.VyneLogger;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.IContainerFactory;

public final class NeoForgeMenuTypeBridge {
    private NeoForgeMenuTypeBridge() {}

    @SuppressWarnings("unchecked")
    public static <T extends AbstractContainerMenu> net.minecraft.class_3917<T> create(
        IContainerFactory<T> factory
    ) {
        if (factory == null) {
            VyneLogger.warn("[IForgeMenuType] create(null) — menu registration will stay empty.");
            return null;
        }
        Object bridged = MenuTypeCompat.createForgeLikeMenuType(factory);
        if (bridged != null) {
            return (net.minecraft.class_3917<T>) bridged;
        }
        VyneLogger.warn("[IForgeMenuType] create() returned null for factory=" + factory.getClass().getName()
            + " — Fabric ExtendedScreenHandlerType bridge unavailable or failed; "
            + "minecraft:menu suppliers stay null → no creative tab / screens / models for that mod.");
        return null;
    }
}
