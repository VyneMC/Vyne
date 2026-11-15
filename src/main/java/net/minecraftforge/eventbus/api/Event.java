/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.minecraftforge.eventbus.api;

public class Event {
    private boolean canceled = false;
    private Result result = Result.DEFAULT;

    public boolean isCancelable() {
        return this instanceof ICancellableEvent;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    public boolean hasResult() {
        return false;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public enum Result {
        DENY, DEFAULT, ALLOW
    }

    public interface ICancellableEvent {}
}
