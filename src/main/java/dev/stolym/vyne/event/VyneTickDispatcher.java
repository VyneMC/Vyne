/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.event;

import dev.stolym.vyne.containment.VyneExecutionGate;
import dev.stolym.vyne.containment.VyneModIdentity;
import dev.stolym.vyne.util.VyneLogger;
import net.neoforged.bus.api.VyneEventBus;

public final class VyneTickDispatcher {
    private VyneTickDispatcher() {}

    private static net.minecraftforge.event.TickEvent.ClientTickEvent.Pre PRE_EVENT;
    private static net.minecraftforge.event.TickEvent.ClientTickEvent.Post POST_EVENT;

    public static void fireClientTickPre() {
        String modId = VyneModIdentity.currentModId();
        if (modId != null && !VyneExecutionGate.isAllowed(modId)) {
            return;
        }
        try {
            if (PRE_EVENT == null) {
                PRE_EVENT = new net.minecraftforge.event.TickEvent.ClientTickEvent.Pre();
            }
            VyneEventBus.global().post(PRE_EVENT);
        } catch (Throwable t) {
            VyneLogger.warn("[Vyne-Tick] client-pre dispatch failure: " + t);
        }
    }

    public static void fireClientTickPost() {
        String modId = VyneModIdentity.currentModId();
        if (modId != null && !VyneExecutionGate.isAllowed(modId)) {
            return;
        }
        try {
            if (POST_EVENT == null) {
                POST_EVENT = new net.minecraftforge.event.TickEvent.ClientTickEvent.Post();
            }
            VyneEventBus.global().post(POST_EVENT);
        } catch (Throwable t) {
            VyneLogger.warn("[Vyne-Tick] client-post dispatch failure: " + t);
        }
    }
}
