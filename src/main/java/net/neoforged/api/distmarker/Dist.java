/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.api.distmarker;

public enum Dist {
    CLIENT,
    DEDICATED_SERVER;

    public boolean isClient() { return this == CLIENT; }
    public boolean isDedicatedServer() { return this == DEDICATED_SERVER; }
}
