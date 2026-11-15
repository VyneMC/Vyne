/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.client.event;

import net.neoforged.bus.api.Event;

import java.util.function.Function;

public class RegisterClientTooltipComponentFactoriesEvent extends Event {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends net.minecraft.class_5632> void register(
            Class<T> markerType,
            Function<? super T, ? extends net.minecraft.class_5684> factory) {
        dev.stolym.vyne.client.VyneTooltipComponentRegistry.register(
            (Class) markerType, factory);
    }
}
