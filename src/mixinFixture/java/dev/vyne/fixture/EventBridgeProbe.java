/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.vyne.fixture;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

final class EventBridgeProbe {
    private static final String SLEEP = "net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents";
    private static final String LIVING = "net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents";
    private static final AtomicInteger CONVERSIONS = new AtomicInteger();
    private static volatile Object posA;
    private static volatile Object posB;
    private static volatile Object posC;

    private EventBridgeProbe() {}

    static void registerFabric(ClassLoader loader) throws Exception {
        listen(loader, SLEEP, "ALLOW_SLEEP_TIME", "AllowSleepTime", args -> args[1].equals(posA) ? result(loader, "SUCCESS") : result(loader, "PASS"));
        listen(loader, SLEEP, "ALLOW_NEARBY_MONSTERS", "AllowNearbyMonsters", args -> args[1].equals(posB) ? result(loader, "FAIL") : result(loader, "PASS"));
        listen(loader, SLEEP, "ALLOW_BED", "AllowBed", args -> args[1].equals(posC) ? result(loader, "FAIL") : result(loader, "PASS"));
        listen(loader, SLEEP, "ALLOW_SETTING_SPAWN", "AllowSettingSpawn", args -> !args[1].equals(posA));
        listen(loader, LIVING, "MOB_CONVERSION", "MobConversion", args -> {
            CONVERSIONS.incrementAndGet();
            return null;
        });
    }

    static void run(ClassLoader loader, Object level, Object player, int x, int y, int z) throws Exception {
        Class<?> blockPos = Class.forName("net.minecraft.class_2338", true, loader);
        Constructor<?> pos = blockPos.getConstructor(int.class, int.class, int.class);
        posA = pos.newInstance(x, y + 3, z);
        posB = pos.newInstance(x + 2, y + 3, z);
        posC = pos.newInstance(x + 4, y + 3, z);
        Class<?> problems = Class.forName("net.minecraft.class_1657$class_1658", true, loader);
        Object notNow = enumValue(problems, "NOT_POSSIBLE_NOW");
        Object bus = Class.forName("net.neoforged.neoforge.common.NeoForge", true, loader).getField("EVENT_BUS").get(null);

        Object sleepA = post(bus, create(loader, "net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent", player, posA, notNow));
        Object sleepB = post(bus, create(loader, "net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent", player, posB, null));
        Object overworld = Class.forName("net.minecraft.class_1937", true, loader).getField("field_25179").get(null);
        post(bus, create(loader, "net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent", player, posA, notNow));
        Object spawnA = post(bus, create(loader, "net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent", player, overworld, posA, false));
        Object spawnB = post(bus, create(loader, "net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent", player, overworld, posB, false));
        call(player, "method_18402", posC);
        Object continueC = post(bus, create(loader, "net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent", player, null));
        call(player, "method_18399");

        Class<?> types = Class.forName("net.minecraft.class_1299", true, loader);
        Object mooshroom = call(types.getField("field_6143").get(null), "method_5883", level);
        Object cow = call(types.getField("field_6085").get(null), "method_5883", level);
        Class<?> living = Class.forName("net.minecraft.class_1309", true, loader);
        post(bus, Class.forName("net.neoforged.neoforge.event.entity.living.LivingConversionEvent$Post", true, loader)
                .getConstructor(living, living).newInstance(mooshroom, cow));

        System.out.println("[VyneFixture] sleep: time-allowed=" + call(sleepA, "getProblem") + " monsters-refused=" + call(sleepB, "getProblem")
                + " spawn-refused=" + call(spawnA, "isCanceled") + " spawn-other=" + call(spawnB, "isCanceled")
                + " continue-refused=" + !(boolean) call(continueC, "mayContinueSleeping") + " conversions=" + CONVERSIONS.get());
    }

    private interface Body {
        Object apply(Object[] args) throws Exception;
    }

    private static void listen(ClassLoader loader, String holder, String field, String type, Body body) throws Exception {
        Class<?> holderClass = Class.forName(holder, true, loader);
        Object event = holderClass.getField(field).get(null);
        Class<?> listenerType = Class.forName(holder + "$" + type, true, loader);
        Object listener = Proxy.newProxyInstance(loader, new Class<?>[] {listenerType}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return method.invoke(EventBridgeProbe.class, args);
            return body.apply(args);
        });
        Class.forName("net.fabricmc.fabric.api.event.Event", true, loader).getMethod("register", Object.class).invoke(event, listener);
    }

    private static Object result(ClassLoader loader, String name) throws Exception {
        return enumValue(Class.forName("net.minecraft.class_1269", true, loader), name);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }

    private static Object create(ClassLoader loader, String event, Object... args) throws Exception {
        for (Constructor<?> c : Class.forName(event, true, loader).getConstructors()) {
            if (c.getParameterCount() == args.length) return c.newInstance(args);
        }
        throw new NoSuchMethodException(event + " constructor with " + args.length + " arguments");
    }

    private static Object post(Object bus, Object event) throws Exception {
        for (Method m : bus.getClass().getMethods()) {
            if (m.getName().equals("post") && m.getParameterCount() == 1) {
                m.invoke(bus, event);
                return event;
            }
        }
        throw new NoSuchMethodException("IEventBus.post");
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
        for (Method m : owner.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) return m.invoke(owner, args);
        }
        throw new NoSuchMethodException(owner.getClass().getName() + "." + name);
    }
}
