/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.bus.api;

public class Event {
    private boolean canceled = false;

    public boolean isCancelable() {
        return this instanceof ICancelableEvent;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    public enum Result {
        DENY, DEFAULT, ALLOW
    }
}
