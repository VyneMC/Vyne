/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mod;

import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class ServiceConfigurationTest {
    private static final String RESOURCES_PATH = "src/main/resources/META-INF/services/";

    @Test
    public void testGameProviderService() throws Exception {
        Path serviceFile = Paths.get(RESOURCES_PATH + "net.fabricmc.loader.impl.game.GameProvider");
        if (Files.exists(serviceFile)) {
            String content = Files.readString(serviceFile).trim();
            assertEquals("net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider", content,
                "GameProvider service file should contain the correct implementation class");
        } else {
            fail("GameProvider service file not found at expected location");
        }
    }

    @Test
    public void testMixinServiceBootstrap() throws Exception {
        Path serviceFile = Paths.get(RESOURCES_PATH + "org.spongepowered.asm.service.IMixinServiceBootstrap");
        if (Files.exists(serviceFile)) {
            String content = Files.readString(serviceFile).trim();
            assertEquals("net.fabricmc.loader.impl.launch.knot.MixinServiceKnotBootstrap", content,
                "IMixinServiceBootstrap service file should contain the correct implementation class");
        } else {
            fail("IMixinServiceBootstrap service file not found at expected location");
        }
    }

    @Test
    public void testMixinService() throws Exception {
        Path serviceFile = Paths.get(RESOURCES_PATH + "org.spongepowered.asm.service.IMixinService");
        if (Files.exists(serviceFile)) {
            String content = Files.readString(serviceFile).trim();
            assertEquals("net.fabricmc.loader.impl.launch.knot.MixinServiceKnot", content,
                "IMixinService service file should contain the correct implementation class");
        } else {
            fail("IMixinService service file not found at expected location");
        }
    }

    @Test
    public void testGlobalPropertyService() throws Exception {
        Path serviceFile = Paths.get(RESOURCES_PATH + "org.spongepowered.asm.service.IGlobalPropertyService");
        if (Files.exists(serviceFile)) {
            String content = Files.readString(serviceFile).trim();
            assertEquals("net.fabricmc.loader.impl.launch.knot.FabricGlobalPropertyService", content,
                "IGlobalPropertyService service file should contain the correct implementation class");
        } else {
            fail("IGlobalPropertyService service file not found at expected location");
        }
    }
}
