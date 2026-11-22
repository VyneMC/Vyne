/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

public final class FabricResources {
    private FabricResources() {}

    public static boolean replacedByFabric(Object tagBuilder) {
        try {
            return (boolean) Class.forName("net.fabricmc.fabric.impl.datagen.TagBuilderHooks", true, FabricResources.class.getClassLoader())
                    .getMethod("fabric_isReplaced").invoke(tagBuilder);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
