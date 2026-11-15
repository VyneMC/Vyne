/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hud;

import net.fabricmc.api.ClientModInitializer;

public final class VyneHudOverlay implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("[VyneHud] Initialized (Runtime rendering enabled)");
    }
}
