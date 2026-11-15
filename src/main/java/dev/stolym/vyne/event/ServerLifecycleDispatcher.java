/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.event;

import dev.stolym.vyne.util.VyneLogger;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class ServerLifecycleDispatcher {
    private static final Set<Object> STARTED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private ServerLifecycleDispatcher() {}

    public static void aboutToStart(Object server) {
        post("ServerAboutToStartEvent", () -> new ServerAboutToStartEvent((MinecraftServer) server));
    }

    public static void starting(Object server) {
        post("ServerStartingEvent", () -> new ServerStartingEvent((MinecraftServer) server));
    }

    public static void firstTick(Object server) {
        if (STARTED.add(server)) {
            post("ServerStartedEvent", () -> new ServerStartedEvent((MinecraftServer) server));
        }
    }

    public static void stopping(Object server) {
        post("ServerStoppingEvent", () -> new ServerStoppingEvent((MinecraftServer) server));
    }

    public static void stopped(Object server) {
        STARTED.remove(server);
        post("ServerStoppedEvent", () -> new ServerStoppedEvent((MinecraftServer) server));
    }

    private static void post(String name, java.util.function.Supplier<Object> event) {
        java.util.List<?> neoForgeMods = dev.stolym.vyne.core.VyneLauncher.getPendingVCLMods();
        if (neoForgeMods == null || neoForgeMods.isEmpty()) return;
        try {
            NeoForge.EVENT_BUS.post(event.get());
            VyneLogger.info("[ServerLifecycle] Fired " + name);
        } catch (Throwable t) {
            VyneLogger.warn("[ServerLifecycle] " + name + " could not be fired: " + t);
        }
    }
}
