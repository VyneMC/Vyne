/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.registry;

import dev.stolym.vyne.vcl.VCLManager;

public final class CropBlockDatapackBootstrap {
    private CropBlockDatapackBootstrap() {}

    public static void runBeforeRegistryLoader(ClassLoader knot) {
        if (knot == null) {
            knot = VCLManager.getCanonicalKnotLoader();
        }
        if (knot == null) {
            knot = Thread.currentThread().getContextClassLoader();
        }
        ModBlockTableReflect.preflightDiscoverBlockHolderClasses(knot);
    }
}
