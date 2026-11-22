/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import dev.stolym.vyne.util.VyneLogger;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class ArchitecturyBridge {
    private static final String EVENTS = "dev.architectury.event.events.";
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private ArchitecturyBridge() {}

    static void onBlockBreak(Object event) {
        guard("BlockEvent.BREAK", () -> {
            Object player = FabricEvents.call(event, "getPlayer");
            if (player == null || !isServerPlayer(player)) return;
            Object result = fire("common.BlockEvent", "BREAK", "breakBlock", FabricEvents.call(event, "getLevel"),
                    FabricEvents.call(event, "getPos"), FabricEvents.call(event, "getState"), player, null);
            if (isFalse(result)) FabricEvents.call(event, "setCanceled", true);
        });
    }

    static void onEntityJoin(Object event) {
        guard("EntityEvent.ADD", () -> {
            Object level = FabricEvents.call(event, "getLevel");
            if ((boolean) FabricEvents.call(level, "method_8608")) return;
            Object result = fire("common.EntityEvent", "ADD", "add", FabricEvents.call(event, "getEntity"), level);
            if (isFalse(result)) FabricEvents.call(event, "setCanceled", true);
        });
    }

    static void onSpawnPositionCheck(Object event) {
        guard("EntityEvent.LIVING_CHECK_SPAWN", () -> {
            Object result = fire("common.EntityEvent", "LIVING_CHECK_SPAWN", "canSpawn", FabricEvents.call(event, "getEntity"),
                    FabricEvents.call(event, "getLevel"), FabricEvents.call(event, "getX"), FabricEvents.call(event, "getY"),
                    FabricEvents.call(event, "getZ"), FabricEvents.call(event, "getSpawnType"), FabricEvents.call(event, "getSpawner"));
            Object value = result == null ? null : FabricEvents.call(result, "value");
            if (value == null) return;
            Class<?> results = Class.forName("net.neoforged.neoforge.event.entity.living.MobSpawnEvent$PositionCheck$Result", true, event.getClass().getClassLoader());
            FabricEvents.call(event, "setResult", results.getField(Boolean.TRUE.equals(value) ? "SUCCEED" : "FAIL").get(null));
        });
    }

    static void onScreenRenderPre(Object event) {
        guard("ClientGuiEvent.RENDER_PRE", () -> {
            Object result = fire("client.ClientGuiEvent", "RENDER_PRE", "render", FabricEvents.call(event, "getScreen"),
                    FabricEvents.call(event, "getGuiGraphics"), FabricEvents.call(event, "getMouseX"), FabricEvents.call(event, "getMouseY"), deltaTracker());
            if (isFalse(result)) FabricEvents.call(event, "setCanceled", true);
        });
    }

    static void onScreenRenderPost(Object event) {
        guard("ClientGuiEvent.RENDER_POST", () -> fire("client.ClientGuiEvent", "RENDER_POST", "render", FabricEvents.call(event, "getScreen"),
                FabricEvents.call(event, "getGuiGraphics"), FabricEvents.call(event, "getMouseX"), FabricEvents.call(event, "getMouseY"), deltaTracker()));
    }

    static void onCharTypedPre(Object event) {
        guard("ClientScreenInputEvent.CHAR_TYPED_PRE", () -> {
            Object result = fire("client.ClientScreenInputEvent", "CHAR_TYPED_PRE", "charTyped", minecraft(), FabricEvents.call(event, "getScreen"),
                    FabricEvents.call(event, "getCodePoint"), FabricEvents.call(event, "getModifiers"));
            if (isFalse(result)) FabricEvents.call(event, "setCanceled", true);
        });
    }

    static void onCharTypedPost(Object event) {
        guard("ClientScreenInputEvent.CHAR_TYPED_POST", () -> fire("client.ClientScreenInputEvent", "CHAR_TYPED_POST", "charTyped", minecraft(),
                FabricEvents.call(event, "getScreen"), FabricEvents.call(event, "getCodePoint"), FabricEvents.call(event, "getModifiers")));
    }

    static void onRightClickEmpty(Object event) {
        guard("InteractionEvent.CLIENT_RIGHT_CLICK_AIR", () -> fire("common.InteractionEvent", "CLIENT_RIGHT_CLICK_AIR", "click",
                FabricEvents.call(event, "getEntity"), FabricEvents.call(event, "getHand")));
    }

    private interface Body {
        void run() throws ReflectiveOperationException;
    }

    private static void guard(String what, Body body) {
        try {
            body.run();
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (FAILED.add(what)) VyneLogger.warn("[PatchLayer] Architectury " + what + " could not be answered: " + e);
        }
    }

    private static Object fire(String holder, String field, String method, Object... args) throws ReflectiveOperationException {
        ClassLoader loader = ArchitecturyBridge.class.getClassLoader();
        Object event = Class.forName(EVENTS + holder, true, loader).getField(field).get(null);
        Object invoker = Class.forName("dev.architectury.event.Event", true, loader).getMethod("invoker").invoke(event);
        for (Class<?> type : invoker.getClass().getInterfaces()) {
            for (Method m : type.getMethods()) {
                if (m.getName().equals(method) && m.getParameterCount() == args.length) return m.invoke(invoker, args);
            }
        }
        throw new NoSuchMethodException(holder + "." + field + "." + method);
    }

    private static boolean isFalse(Object result) throws ReflectiveOperationException {
        return result != null && (boolean) result.getClass().getMethod("isFalse").invoke(result);
    }

    private static boolean isServerPlayer(Object player) {
        for (Class<?> c = player.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals("net.minecraft.class_3222")) return true;
        }
        return false;
    }

    private static Object minecraft() throws ReflectiveOperationException {
        return Class.forName("net.minecraft.class_310", true, ArchitecturyBridge.class.getClassLoader()).getMethod("method_1551").invoke(null);
    }

    private static Object deltaTracker() throws ReflectiveOperationException {
        return FabricEvents.call(minecraft(), "method_60646");
    }
}
