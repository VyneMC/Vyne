/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.vyne.fixture;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

final class BlockBreakProbe {
    private static final String FABRIC_EVENTS = "net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents";
    private static final Map<String, Integer> COUNTS = new ConcurrentHashMap<>();
    private static volatile Object fabricCancelPos;
    private static volatile Object neoForgeCancelPos;

    private BlockBreakProbe() {}

    static void register() {
        ClassLoader loader = BlockBreakProbe.class.getClassLoader();
        try {
            Class.forName(FABRIC_EVENTS, false, loader);
        } catch (ClassNotFoundException e) {
            return;
        }
        try {
            Object bus = Class.forName("net.neoforged.neoforge.common.NeoForge", true, loader).getField("EVENT_BUS").get(null);
            Class<?> priority = Class.forName("net.neoforged.bus.api.EventPriority", true, loader);
            Object normal = priority.getField("NORMAL").get(null);
            Method addListener = null;
            for (Method m : bus.getClass().getMethods()) {
                if (m.getName().equals("addListener") && m.getParameterCount() == 4 && m.getParameterTypes()[2] == Class.class) addListener = m;
            }
            Class<?> started = Class.forName("net.neoforged.neoforge.event.server.ServerStartedEvent", true, loader);
            Class<?> breakEvent = Class.forName("net.neoforged.neoforge.event.level.BlockEvent$BreakEvent", true, loader);
            Consumer<Object> onStarted = event -> run(event, loader);
            Consumer<Object> onBreak = BlockBreakProbe::onNeoForgeBreak;
            addListener.invoke(bus, normal, false, started, onStarted);
            addListener.invoke(bus, normal, false, breakEvent, onBreak);
            registerFabric(loader);
            registerFuel(loader);
            System.out.println("[VyneFixture] block-break probe registered");
        } catch (Throwable t) {
            System.out.println("[VyneFixture] block-break probe could not register: " + t);
        }
    }

    private static void registerFabric(ClassLoader loader) throws Exception {
        Class<?> events = Class.forName(FABRIC_EVENTS, true, loader);
        listen(events, "BEFORE", args -> {
            count("fabric-before", args[2]);
            return !args[2].equals(fabricCancelPos);
        });
        listen(events, "AFTER", args -> {
            count("fabric-after", args[2]);
            return null;
        });
        listen(events, "CANCELED", args -> {
            count("fabric-canceled", args[2]);
            return null;
        });
    }

    private static void registerFuel(ClassLoader loader) throws Exception {
        Class<?> registry = Class.forName("net.fabricmc.fabric.api.registry.FuelRegistry", true, loader);
        Object instance = registry.getField("INSTANCE").get(null);
        Object dirt = Class.forName("net.minecraft.class_1802", true, loader).getField("field_8831").get(null);
        for (Method m : registry.getMethods()) {
            if (m.getName().equals("add") && m.getParameterCount() == 2 && !m.isBridge()) {
                m.invoke(instance, dirt, 1234);
                return;
            }
        }
    }

    private static int burnTime(ClassLoader loader, String itemField) throws Exception {
        Object item = Class.forName("net.minecraft.class_1802", true, loader).getField(itemField).get(null);
        Class<?> convertible = Class.forName("net.minecraft.class_1935", true, loader);
        Object stack = Class.forName("net.minecraft.class_1799", true, loader).getConstructor(convertible).newInstance(item);
        Object smelting = Class.forName("net.minecraft.class_3956", true, loader).getField("field_17546").get(null);
        for (Method m : stack.getClass().getMethods()) {
            if (m.getName().equals("getBurnTime") && m.getParameterCount() == 1) return (int) m.invoke(stack, smelting);
        }
        throw new NoSuchMethodException("ItemStack.getBurnTime(RecipeType)");
    }

    private static void listen(Class<?> events, String name, java.util.function.Function<Object[], Object> body) throws Exception {
        Object event = events.getField(name).get(null);
        Class<?> type = Class.forName(FABRIC_EVENTS + "$" + name.charAt(0) + name.substring(1).toLowerCase(), true, events.getClassLoader());
        Object listener = Proxy.newProxyInstance(events.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return method.invoke(BlockBreakProbe.class, args);
            return body.apply(args);
        });
        Class.forName("net.fabricmc.fabric.api.event.Event", true, events.getClassLoader())
                .getMethod("register", Object.class).invoke(event, listener);
    }

    private static void onNeoForgeBreak(Object event) {
        try {
            Object pos = event.getClass().getMethod("getPos").invoke(event);
            count("neoforge-break", pos);
            if (pos.equals(neoForgeCancelPos)) event.getClass().getMethod("setCanceled", boolean.class).invoke(event, true);
        } catch (ReflectiveOperationException e) {
            System.out.println("[VyneFixture] block-break probe: NeoForge listener failed: " + e);
        }
    }

    private static void run(Object startedEvent, ClassLoader loader) {
        try {
            Object server = startedEvent.getClass().getMethod("getServer").invoke(startedEvent);
            Object level = call(server, "method_30002");
            Object spawn = call(level, "method_43126");
            int x = (int) call(spawn, "method_10263"), y = (int) call(spawn, "method_10264") + 20, z = (int) call(spawn, "method_10260");
            Class<?> blockPos = Class.forName("net.minecraft.class_2338", true, loader);
            Object normalPos = blockPos.getConstructor(int.class, int.class, int.class).newInstance(x, y, z);
            Object fabricPos = blockPos.getConstructor(int.class, int.class, int.class).newInstance(x + 2, y, z);
            Object neoPos = blockPos.getConstructor(int.class, int.class, int.class).newInstance(x + 4, y, z);
            fabricCancelPos = fabricPos;
            neoForgeCancelPos = neoPos;
            Object stone = call(Class.forName("net.minecraft.class_2246", true, loader).getField("field_10340").get(null), "method_9564");
            Class<?> serverLevel = Class.forName("net.minecraft.class_3218", true, loader);
            Object player = Class.forName("net.fabricmc.fabric.api.entity.FakePlayer", true, loader)
                    .getMethod("get", serverLevel).invoke(null, level);
            Object gameMode = field(player, "field_13974");
            report("normal", level, gameMode, normalPos, stone);
            report("fabric-cancel", level, gameMode, fabricPos, stone);
            report("neoforge-cancel", level, gameMode, neoPos, stone);
            System.out.println("[VyneFixture] fuel: dirt=" + burnTime(loader, "field_8831") + " coal="
                    + burnTime(loader, "field_8713") + " stone=" + burnTime(loader, "field_20391"));
        } catch (Throwable t) {
            System.out.println("[VyneFixture] block-break probe failed: " + t);
        }
    }

    private static void report(String name, Object level, Object gameMode, Object pos, Object stone) throws Exception {
        call(level, "method_8501", pos, stone);
        Object result = call(gameMode, "method_14266", pos);
        boolean removed = (boolean) call(call(level, "method_8320", pos), "method_26215");
        System.out.println("[VyneFixture] block-break " + name + ": result=" + result + " removed=" + removed
                + " neoforge-break=" + get("neoforge-break", pos) + " fabric-before=" + get("fabric-before", pos)
                + " fabric-after=" + get("fabric-after", pos) + " fabric-canceled=" + get("fabric-canceled", pos));
    }

    private static void count(String what, Object pos) {
        COUNTS.merge(what + "@" + pos, 1, Integer::sum);
    }

    private static int get(String what, Object pos) {
        return COUNTS.getOrDefault(what + "@" + pos, 0);
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
        throw new NoSuchFieldException(name);
    }

    private static Object call(Object owner, String name, Object... args) throws Exception {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                    m.setAccessible(true);
                    return m.invoke(owner, args);
                }
            }
        }
        throw new NoSuchMethodException(owner.getClass().getName() + "." + name);
    }
}
