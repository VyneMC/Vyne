/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.containment;

public enum VyneModState {
    RUNNING,

    FROZEN,

    HARD_STOPPED;

    public boolean allowsExecution() {
        return this == RUNNING;
    }

    public boolean isFailed() {
        return this != RUNNING;
    }
}
