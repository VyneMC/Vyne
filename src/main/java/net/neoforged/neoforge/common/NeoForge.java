/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.common;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.VyneEventBus;

public class NeoForge {
    public static final net.neoforged.bus.api.IEventBus EVENT_BUS = VyneEventBus.global();

    static {
        System.out.println("[VYNE-SHIM] NeoForge.EVENT_BUS initialized!");
    }
}
