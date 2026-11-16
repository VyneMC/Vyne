/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import dev.stolym.vyne.util.VyneLogger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class FabricBlockBreak {
    private static final String EVENTS = "net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents";
    private static final ThreadLocal<Object> BLOCK_ENTITY = new ThreadLocal<>();
    private static volatile boolean failed;

    private FabricBlockBreak() {}

    public static boolean before(Object gameMode, Object pos) {
        if (failed) return true;
        try {
            Object level = field(gameMode, "field_14007");
            Object player = field(gameMode, "field_14008");
            Object state = call(level, "method_8320", pos);
            Object blockEntity = call(level, "method_8321", pos);
            Object allowed = invoke("BEFORE", "beforeBlockBreak", level, player, pos, state, blockEntity);
            if (!Boolean.FALSE.equals(allowed)) return true;
            invoke("CANCELED", "onBlockBreakCanceled", level, player, pos, state, blockEntity);
            Object connection = field(player, "field_13987");
            ClassLoader loader = gameMode.getClass().getClassLoader();
            Class<?> blockGetter = Class.forName("net.minecraft.class_1922", false, loader);
            Class<?> blockPos = Class.forName("net.minecraft.class_2338", false, loader);
            Object update = Class.forName("net.minecraft.class_2626", true, loader)
                    .getConstructor(blockGetter, blockPos).newInstance(level, pos);
            call(connection, "method_14364", update);
            if (blockEntity != null) {
                Object packet = call(blockEntity, "method_38235");
                if (packet != null) call(connection, "method_14364", packet);
            }
            return false;
        } catch (Throwable t) {
            disable(t);
            return true;
        }
    }

    public static void rememberBlockEntity(Object gameMode, Object pos) {
        if (failed) return;
        try {
            BLOCK_ENTITY.set(call(field(gameMode, "field_14007"), "method_8321", pos));
        } catch (Throwable t) {
            disable(t);
        }
    }

    public static void after(Object gameMode, Object pos, Object state, boolean removed) {
        Object blockEntity = BLOCK_ENTITY.get();
        BLOCK_ENTITY.remove();
        if (failed || !removed) return;
        try {
            invoke("AFTER", "afterBlockBreak", field(gameMode, "field_14007"), field(gameMode, "field_14008"),
                    pos, state, blockEntity);
        } catch (Throwable t) {
            disable(t);
        }
    }

    private static Object invoke(String eventName, String methodName, Object... args) throws Exception {
        Class<?> events = Class.forName(EVENTS, true, FabricBlockBreak.class.getClassLoader());
        Object event = events.getField(eventName).get(null);
        Object invoker = event.getClass().getMethod("invoker").invoke(event);
        for (Class<?> type : invoker.getClass().getInterfaces()) {
            for (Method m : type.getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == args.length) return m.invoke(invoker, args);
            }
        }
        throw new NoSuchMethodException(eventName + "." + methodName);
    }

    private static Object field(Object owner, String name) throws Exception {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(owner);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(owner.getClass().getName() + "." + name);
    }

    private static Object call(Object owner, String name, Object... args) throws Exception {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                    m.setAccessible(true);
                    return m.invoke(owner, args);
                }
            }
            for (Class<?> i : c.getInterfaces()) {
                for (Method m : i.getMethods()) {
                    if (m.getName().equals(name) && m.getParameterCount() == args.length) return m.invoke(owner, args);
                }
            }
        }
        throw new NoSuchMethodException(owner.getClass().getName() + "." + name);
    }

    private static void disable(Throwable t) {
        failed = true;
        VyneLogger.warn("[PatchLayer] Fabric block-break events are off: " + t);
    }
}
