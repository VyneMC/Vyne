/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.minecraftforge.fml;

public class ModContainer {
    private final String modId;

    public ModContainer(String modId) {
        this.modId = modId;
    }

    public String getModId() {
        return modId;
    }
}
