/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.vyne.fixture;

import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

@OnlyIn(Dist.CLIENT)
public final class ClientOnlyThing {
    private ClientOnlyThing() {}

    public static void init() {
        System.out.println("[VyneFixture] client-only class loaded on CLIENT");
    }
}
