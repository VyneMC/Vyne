/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package com.terraformersmc.modmenu.api;

import net.minecraft.class_437;

@FunctionalInterface
public interface ConfigScreenFactory<S extends class_437> {
    S create(class_437 parent);
}
