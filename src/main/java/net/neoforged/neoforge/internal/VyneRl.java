/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.internal;

import net.minecraft.resources.ResourceLocation;

public final class VyneRl {
    private VyneRl() {}

    public static ResourceLocation fromColonFormRl(String id) {
        if (id == null) {
            throw new NullPointerException("id");
        }
        if (id.contains(":")) {
            String[] parts = id.split(":", 2);
            return new ResourceLocation(parts[0], parts[1]);
        }
        return new ResourceLocation("minecraft", id);
    }
}
