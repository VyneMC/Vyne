/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.fml.javafmlmod;

import net.neoforged.fml.ModContainer;

public class FMLModContainer extends ModContainer {
    public FMLModContainer(String modId) {
        super(modId);
    }

    public FMLModContainer(String modId, Object eventBus, String modVersion) {
        super(modId, eventBus, modVersion);
    }
}
