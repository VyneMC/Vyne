/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.server;

import dev.stolym.vyne.util.ReflectionHelper;
import dev.stolym.vyne.util.VyneLogger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class ServerLagMitigator {
    private static long tickWallStartNs;
    private static final IdentityHashMap<Object, Long> ENTITY_NANOS = new IdentityHashMap<>();
    private static long lastMitigationWallMs;

    private ServerLagMitigator() {}

    public static void onServerTickStart() {
        tickWallStartNs = System.nanoTime();
        ENTITY_NANOS.clear();
    }

    public static void recordEntityTickNanos(Object entity, long nanos) {
        if (!ServerLagMitigationConfig.shouldSampleEntityTicks() || entity == null || nanos <= 0) {
            return;
        }
        if (nanos < ServerLagMitigationConfig.minEntityTickNanos()) {
            return;
        }
        ENTITY_NANOS.merge(entity, nanos, Long::sum);
    }

    public static void onServerTickEnd(Object server) {
        if (!ServerLagMitigationConfig.enabled()) {
            return;
        }
        long wallNs = System.nanoTime() - tickWallStartNs;
        long triggerNs = ServerLagMitigationConfig.triggerTickTimeMs() * 1_000_000L;
        if (wallNs < triggerNs) {
            return;
        }
        long cooldown = ServerLagMitigationConfig.cooldownMs();
        long now = System.currentTimeMillis();
        if (cooldown > 0 && now - lastMitigationWallMs < cooldown) {
            return;
        }
        if (ENTITY_NANOS.isEmpty()) {
            VyneLogger.warn("[LagMitigation] Server tick took " + (wallNs / 1_000_000L)
                + "ms (threshold " + ServerLagMitigationConfig.triggerTickTimeMs()
                + "ms) but no entity timings were recorded — cause may be chunk generation, "
                + "scheduled tasks, or GC (mitigation skipped).");
            return;
        }

        List<Map.Entry<Object, Long>> ranked = new ArrayList<>(ENTITY_NANOS.entrySet());
        ranked.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        int cap = ServerLagMitigationConfig.maxDiscardsPerSpike();
        int discarded = 0;
        List<String> names = new ArrayList<>();
        for (Map.Entry<Object, Long> e : ranked) {
            if (discarded >= cap) {
                break;
            }
            Object ent = e.getKey();
            if (isProtectedEntity(ent)) {
                continue;
            }
            try {
                String label = shortEntityLabel(ent);
                ReflectionHelper.call(ent, "discard", "method_31472");
                names.add(label + "(" + (e.getValue() / 1_000_000L) + "ms entity-tick)");
                discarded++;
            } catch (Throwable ignored) {
            }
        }
        if (discarded > 0) {
            lastMitigationWallMs = now;
            VyneLogger.warn("[LagMitigation] Tick took " + (wallNs / 1_000_000L) + "ms; removed "
                + discarded + " slow-ticking entit(y/ies): " + String.join(", ", names));
        }
    }

    private static String shortEntityLabel(Object entity) {
        try {
            Object type = ReflectionHelper.call(entity, "getType", "method_5864");
            if (type != null) {
                return type.toString();
            }
        } catch (Throwable ignored) {
        }
        return entity.getClass().getSimpleName();
    }

    private static boolean isProtectedEntity(Object entity) {
        if (entity == null) {
            return true;
        }
        ClassLoader cl = entity.getClass().getClassLoader();
        try {
            Class<?> player = Class.forName("net.minecraft.world.entity.player.Player", false, cl);
            if (player.isInstance(entity)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        String n = entity.getClass().getName();
        return n.contains("ServerPlayer") || n.endsWith(".Player") && !n.contains("Fake");
    }
}
