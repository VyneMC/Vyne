/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.network;

import net.neoforged.neoforge.network.registration.IPayloadRegistrar;

public class PayloadRegistrar {
    public static IPayloadRegistrar of(String modId) {
        System.out.println("[VYNE-SHIM-ALT] PayloadRegistrar.of(" + modId + ") called in parent package!");
        return net.neoforged.neoforge.network.registration.IPayloadRegistrar.of(modId);
    }
}
