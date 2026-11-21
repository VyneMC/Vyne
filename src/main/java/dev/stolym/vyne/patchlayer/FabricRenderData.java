/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import dev.stolym.vyne.util.VyneLogger;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Map;

public final class FabricRenderData {
    private static volatile boolean warned;

    private FabricRenderData() {}

    public static void attach(Object region, Object level, Object section) {
        if (region == null) return;
        try {
            int sectionX = (int) FabricEvents.call(section, "method_18674");
            int sectionY = (int) FabricEvents.call(section, "method_18683");
            int sectionZ = (int) FabricEvents.call(section, "method_18687");
            int minX = (sectionX - 1) << 4, minY = (sectionY - 1) << 4, minZ = (sectionZ - 1) << 4;
            int maxX = ((sectionX + 1) << 4) + 15, maxY = ((sectionY + 1) << 4) + 15, maxZ = ((sectionZ + 1) << 4) + 15;
            Long2ObjectOpenHashMap<Object> data = null;
            for (int cx = sectionX - 1; cx <= sectionX + 1; cx++) {
                for (int cz = sectionZ - 1; cz <= sectionZ + 1; cz++) {
                    Object chunk = FabricEvents.call(level, "method_8497", cx, cz);
                    Map<?, ?> blockEntities = (Map<?, ?>) FabricEvents.call(chunk, "method_12214");
                    for (Map.Entry<?, ?> entry : blockEntities.entrySet()) {
                        Object pos = entry.getKey();
                        int x = (int) FabricEvents.call(pos, "method_10263"), y = (int) FabricEvents.call(pos, "method_10264"), z = (int) FabricEvents.call(pos, "method_10260");
                        if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) continue;
                        Object renderData = FabricEvents.call(entry.getValue(), "getRenderData");
                        if (renderData == null) continue;
                        if (data == null) data = new Long2ObjectOpenHashMap<>();
                        data.put((long) FabricEvents.call(pos, "method_10063"), renderData);
                    }
                }
            }
            if (data != null) {
                Class.forName("net.fabricmc.fabric.impl.blockview.client.RenderDataMapConsumer", true, FabricRenderData.class.getClassLoader())
                        .getMethod("fabric_acceptRenderDataMap", it.unimi.dsi.fastutil.longs.Long2ObjectMap.class).invoke(region, data);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warned) {
                warned = true;
                VyneLogger.warn("[PatchLayer] Fabric block entity render data could not be attached: " + e);
            }
        }
    }
}
