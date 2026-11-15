/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package com.terraformersmc.modmenu.api;

import net.minecraft.class_2561;
import net.minecraft.class_437;

import java.util.Map;

public interface ModMenuApi {
    static class_437 createModsScreen(class_437 previous) {
        return new dev.stolym.vyne.game.VyneModListScreen(previous);
    }

    static class_2561 createModsButtonText() {
        return class_2561.method_43470("Mods");
    }

    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> null;
    }

    default Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        return Map.of();
    }
}
