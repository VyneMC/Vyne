/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.containment;

public final class VyneModFrozenException extends RuntimeException {
    private final String modId;
    private final VyneModState state;
    private final String context;

    public VyneModFrozenException(String modId, VyneModState state, String context) {
        super("[VyneGate] Blocked execution of frozen mod '" + modId
                + "' (state=" + state + ", context=" + context + ")");
        this.modId = modId;
        this.state = state;
        this.context = context;
    }

    public String getModId() { return modId; }
    public VyneModState getState() { return state; }
    public String getContext() { return context; }
}
