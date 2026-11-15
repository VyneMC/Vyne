/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.network.event;

import dev.stolym.vyne.util.VyneLogger;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RegisterPayloadHandlersEvent {
    public PayloadRegistrar registrar(String modId) {
        VyneLogger.debug("[Vyne-Net] RegisterPayloadHandlersEvent.registrar(" + modId + ")");
        return new PayloadRegistrar();
    }

    public IPayloadRegistrar registrarLegacy(String modId) {
        VyneLogger.debug("[Vyne-Net] RegisterPayloadHandlersEvent.registrarLegacy(" + modId + ")");
        return IPayloadRegistrar.createProxy(modId);
    }
}
