/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.server;

import dev.stolym.vyne.util.ReflectionHelper;
import dev.stolym.vyne.util.VyneLinkageContainment;
import dev.stolym.vyne.util.VyneLogger;

import java.util.Locale;

@SuppressWarnings("removal")
public final class ServerEntityTickContainment {
    private ServerEntityTickContainment() {}

    public static boolean absorptionDisabled() {
        return Boolean.parseBoolean(System.getProperty("vyne.server.disableEntityTickAbsorption", "false"));
    }

    public static boolean shouldAbsorbEntityTick(Throwable t) {
        if (absorptionDisabled() || t == null) {
            return false;
        }
        if (t instanceof OutOfMemoryError || t instanceof StackOverflowError || t instanceof ThreadDeath) {
            return false;
        }
        if (t instanceof VirtualMachineError) {
            return false;
        }
        if (VyneLinkageContainment.shouldContain(t)) {
            return true;
        }
        if (t instanceof LinkageError) {
            return false;
        }
        if (!(t instanceof Exception)) {
            return false;
        }
        String msg = String.valueOf(t.getMessage()).toLowerCase(Locale.ROOT);
        if (msg.contains("ticking entity") || msg.contains("ticking block entity")) {
            return hasNonVanillaFrame(t);
        }
        return hasNonVanillaFrame(t);
    }

    private static boolean hasNonVanillaFrame(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            for (StackTraceElement ste : cur.getStackTrace()) {
                String name = ste.getClassName();
                if (name.startsWith("net.minecraft.")
                    || name.startsWith("com.mojang.")
                    || name.startsWith("java.")
                    || name.startsWith("jdk.")
                    || name.startsWith("sun.")
                    || name.startsWith("org.spongepowered.asm.")
                    || name.startsWith("dev.stolym.vyne.mixin.")) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    public static void tryDiscardEntity(Object entity) {
        if (entity == null) {
            return;
        }
        try {
            ReflectionHelper.call(entity, "discard", "method_31472");
        } catch (Throwable ignored) {
        }
    }

    public static void logAbsorbed(Throwable t, Object entity) {
        String id = "<unknown>";
        try {
            Object uuid = ReflectionHelper.call(entity, "getUUID", "getUuid", "method_5667");
            if (uuid != null) {
                id = uuid.toString();
            }
        } catch (Throwable ignored) {
        }
        VyneLogger.warn("[ServerResilience] Absorbed ticking-entity failure; discarding entity uuid=" + id
            + " err=" + t.getClass().getSimpleName() + " msg=" + safeMsg(t));
    }

    private static String safeMsg(Throwable t) {
        String m = t.getMessage();
        return m == null ? "<none>" : (m.length() > 200 ? m.substring(0, 200) + "…" : m);
    }
}
