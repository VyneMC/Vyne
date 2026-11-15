/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.common.loot;

import net.minecraft.resources.ResourceLocation;

public abstract class GlobalLootModifierSerializer<T extends LootModifier> {
    public abstract T read(ResourceLocation id, com.google.gson.JsonObject json,
                           net.minecraft.world.level.storage.loot.predicates.LootItemCondition[] conditions);

    public abstract com.google.gson.JsonObject write(T instance);
}
