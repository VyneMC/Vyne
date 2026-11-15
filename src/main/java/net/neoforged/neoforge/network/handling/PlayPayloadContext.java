/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.network.handling;

import dev.stolym.vyne.util.VyneLogger;

public final class PlayPayloadContext {
    public void reply(Object payload) {
        VyneLogger.debug("[VCL-Shim] PlayPayloadContext.reply() — no-op stub");
    }

    public void disconnect(Object reason) {
        VyneLogger.debug("[VCL-Shim] PlayPayloadContext.disconnect() — no-op stub");
    }

    public boolean isClientSide() {
        return true;
    }
}
