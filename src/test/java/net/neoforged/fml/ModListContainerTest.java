/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.fml;

import net.neoforged.bus.api.VyneEventBus;
import net.neoforged.fml.javafmlmod.FMLModContainer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModListContainerTest {
    @Test
    void containerIsAnFmlModContainerWithTheModBusAfterLoading() {
        VyneEventBus modBus = new VyneEventBus();
        ModList.registerMod("vynetestmod");
        ModList.beginModInstanceLoad("vynetestmod", modBus);
        ModList.endModInstanceLoad();

        ModContainer container = ModList.get().getModContainerById("vynetestmod").orElseThrow();
        assertInstanceOf(FMLModContainer.class, container);
        assertSame(modBus, container.getEventBus());
        assertSame(container, ModList.get().getModContainerById("vynetestmod").orElseThrow());
    }

    @Test
    void unknownModHasNoContainer() {
        assertTrue(ModList.get().getModContainerById("vyne-not-a-mod").isEmpty());
    }
}
