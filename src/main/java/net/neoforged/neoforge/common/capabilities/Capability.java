/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.common.capabilities;

public class Capability<T, C> {
    private final String id;

    public Capability(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
