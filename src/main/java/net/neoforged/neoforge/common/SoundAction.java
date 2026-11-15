/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.common;

public final class SoundAction {
    private final String name;
    private SoundAction(String name) { this.name = name; }
    public static SoundAction get(String name) { return new SoundAction(name); }
    public String name() { return name; }
}
