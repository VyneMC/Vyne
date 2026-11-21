/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.vyne.fixture;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("vyne-mixin-fixture")
public class VyneMixinFixtureMod {
    private static final Logger LOGGER = LogManager.getLogger("VyneMixinFixture");

    public VyneMixinFixtureMod() {
        LOGGER.info("[VyneFixture] mod instance created");
        System.out.println("[VyneFixture] mod instance created");

        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) serverInit();
        if (FMLEnvironment.dist == Dist.CLIENT) ClientOnlyThing.init();
    }

    private static void serverInit() {
        System.out.println("[VyneFixture] dist-aware init: dedicated server path");
        BlockBreakProbe.register();
    }
}
