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
    private static final AtomicInteger OCCUPATION = new AtomicInteger();
    private static final AtomicInteger WAKE_UP = new AtomicInteger();
    private static final AtomicInteger DIRECTION = new AtomicInteger();
    private static final AtomicInteger MODIFIED = new AtomicInteger();
    private static volatile String sharpnessSource = "none";
    private static volatile Object posA;
    private static volatile Object posB;
    private static volatile Object posC;
    private static volatile Object bedPos;
    private static volatile Object payloadId;

    private EventBridgeProbe() {}

    static void registerFabric(ClassLoader loader) throws Exception {
        listen(loader, SLEEP, "ALLOW_SLEEP_TIME", "AllowSleepTime", args -> args[1].equals(posA) || args[1].equals(bedPos) ? result(loader, "SUCCESS") : result(loader, "PASS"));
        listen(loader, SLEEP, "ALLOW_NEARBY_MONSTERS", "AllowNearbyMonsters", args -> args[1].equals(posB) ? result(loader, "FAIL") : result(loader, "PASS"));
        listen(loader, SLEEP, "ALLOW_BED", "AllowBed", args -> args[1].equals(posC) ? result(loader, "FAIL") : result(loader, "PASS"));
        listen(loader, SLEEP, "ALLOW_SETTING_SPAWN", "AllowSettingSpawn", args -> !args[1].equals(posA));
        listen(loader, SLEEP, "SET_BED_OCCUPATION_STATE", "SetBedOccupationState", args -> {
            OCCUPATION.incrementAndGet();
            return false;
        });
        listen(loader, SLEEP, "MODIFY_WAKE_UP_POSITION", "ModifyWakeUpPosition", args -> {
            WAKE_UP.incrementAndGet();
            return args[3];
        });
        listen(loader, SLEEP, "MODIFY_SLEEPING_DIRECTION", "ModifySleepingDirection", args -> {
            DIRECTION.incrementAndGet();
            return args[2];
        });
        listen(loader, "net.fabricmc.fabric.api.item.v1.EnchantmentEvents", "ALLOW_ENCHANTING", "AllowEnchanting", args -> {
            String key = String.valueOf(call(args[0], "method_40230"));
            String item = String.valueOf(call(args[1], "method_7909"));
            Class<?> triState = Class.forName("net.fabricmc.fabric.api.util.TriState", true, loader);
            if (key.contains("sharpness") && item.equals("minecraft:stick")) return enumValue(triState, "TRUE");
            if (key.contains("unbreaking") && item.equals("minecraft:diamond_pickaxe")) return enumValue(triState, "FALSE");
            return enumValue(triState, "DEFAULT");
        });
        listen(loader, "net.fabricmc.fabric.api.item.v1.EnchantmentEvents", "MODIFY", "Modify", args -> {
            MODIFIED.incrementAndGet();
            if (String.valueOf(args[0]).contains("minecraft:sharpness")) sharpnessSource = String.valueOf(args[2]);
            return null;
        });
        registerPayload(loader);
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

    static void sleepInBed(ClassLoader loader, Object level, Object player, int x, int y, int z) throws Exception {
        bedPos = Class.forName("net.minecraft.class_2338", true, loader).getConstructor(int.class, int.class, int.class).newInstance(x + 6, y + 3, z);
        Object bed = call(Class.forName("net.minecraft.class_2246", true, loader).getField("field_10069").get(null), "method_9564");
        call(level, "method_29199", 13000L);
        call(level, "method_8501", bedPos, bed);
        call(player, "method_5814", x + 6.5, (double) (y + 3), z + 1.5);
        Object result = call(player, "method_7269", bedPos);
        boolean slept = ((java.util.Optional<?>) call(result, "right")).isPresent();
        Object villager = call(Class.forName("net.minecraft.class_1299", true, loader).getField("field_6077").get(null), "method_5883", level);
        call(villager, "method_5814", x + 6.5, (double) (y + 3), z + 1.5);
        call(villager, "method_18403", bedPos);
        call(villager, "method_18400");
        if (!slept) System.out.println("[VyneFixture] bed refused: " + call(result, "left"));
        System.out.println("[VyneFixture] bed: slept=" + slept + " occupation=" + OCCUPATION.get() + " wake-up=" + WAKE_UP.get()
                + " direction-asked=" + (DIRECTION.get() > 0));
    }

    static void items(ClassLoader loader, Object level) throws Exception {
        Object registry = call(call(level, "method_30349"), "method_30530", Class.forName("net.minecraft.class_7924", true, loader).getField("field_41265").get(null));
        Class<?> enchantments = Class.forName("net.minecraft.class_1893", true, loader);
        Object sharpness = call(registry, "method_40290", enchantments.getField("field_9118").get(null));
        Object unbreaking = call(registry, "method_40290", enchantments.getField("field_9119").get(null));
        Object stick = stack(loader, "field_8600");
        Object pickaxe = stack(loader, "field_8377");
        System.out.println("[VyneFixture] items: stick-sharpness=" + call(stick, "supportsEnchantment", sharpness)
                + " pickaxe-unbreaking=" + call(pickaxe, "supportsEnchantment", unbreaking)
                + " pickaxe-sharpness=" + call(pickaxe, "supportsEnchantment", sharpness)
                + " bucket-remainder=" + call(call(stack(loader, "field_8705"), "getCraftingRemainingItem"), "method_7909")
                + " stick-remainder=" + call(call(stick, "getCraftingRemainingItem"), "method_7960")
                + " modified-many=" + (MODIFIED.get() > 30) + " sharpness-source=" + sharpnessSource);
    }

    static void durability(ClassLoader loader, Object player) throws Exception {
        Object pickaxeItem = Class.forName("net.minecraft.class_1802", true, loader).getField("field_8377").get(null);
        Class<?> handlerType = Class.forName("net.fabricmc.fabric.api.item.v1.CustomDamageHandler", true, loader);
        AtomicInteger calls = new AtomicInteger();
        Object handler = Proxy.newProxyInstance(loader, new Class<?>[] {handlerType}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return method.invoke(EventBridgeProbe.class, args);
            if (calls.incrementAndGet() == 2) ((Runnable) args[4]).run();
            return 0;
        });
        call(pickaxeItem, "fabric_setCustomDamageHandler", handler);
        try {
            Object slots = Class.forName("net.minecraft.class_1304", true, loader).getEnumConstants()[0];
            Object pickaxe = stack(loader, "field_8377");
            call(player, "method_5673", slots, pickaxe);
            call(pickaxe, "method_7970", 5, player, slots);
            int damageAfterFirst = (int) call(pickaxe, "method_7919");
            call(pickaxe, "method_7970", 5, player, slots);
            System.out.println("[VyneFixture] durability: handler-calls=" + calls.get() + " damage-after-first=" + damageAfterFirst
                    + " broken=" + call(pickaxe, "method_7960"));
        } finally {
            call(pickaxeItem, "fabric_setCustomDamageHandler", (Object) null);
        }
    }

    static void ingredients(ClassLoader loader) throws Exception {
        Class<?> ingredient = Class.forName("net.minecraft.class_1856", true, loader);
        Object ops = Class.forName("com.mojang.serialization.JsonOps", true, loader).getField("INSTANCE").get(null);
        Class<?> parser = Class.forName("com.google.gson.JsonParser", true, loader);
        Method parse = parser.getMethod("parseString", String.class);
        String any = "{\"fabric:type\":\"fabric:any\",\"ingredients\":[{\"item\":\"minecraft:stick\"},{\"item\":\"minecraft:diamond\"}]}";
        Object single = decode(ingredient.getField("field_46095").get(null), ops, parse.invoke(null, any));
        java.util.List<?> list = (java.util.List<?>) decode(ingredient.getField("LIST_CODEC_NONEMPTY").get(null), ops, parse.invoke(null, "[" + any + ",{\"item\":\"minecraft:dirt\"}]"));
        Object encoded = call(call(call(ingredient.getField("field_46095").get(null), "encodeStart", ops, single), "result"), "orElseThrow");
        System.out.println("[VyneFixture] ingredients: stick=" + call(single, "method_8093", stack(loader, "field_8600"))
                + " dirt=" + call(single, "method_8093", stack(loader, "field_8831"))
                + " list-first-diamond=" + call(list.get(0), "method_8093", stack(loader, "field_8477"))
                + " list-second-dirt=" + call(list.get(1), "method_8093", stack(loader, "field_8831"))
                + " encoded-fabric=" + String.valueOf(encoded).contains("fabric:any"));
    }

    private static Object decode(Object codec, Object ops, Object json) throws Exception {
        return call(call(call(codec, "parse", ops, json), "result"), "orElseThrow");
    }

    private record Text(String value) implements java.lang.reflect.InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "hashCode" -> value.hashCode();
                case "equals" -> proxy == args[0];
                case "toString" -> "payload(" + value + ")";
                default -> payloadId;
            };
        }
    }

    private static void registerPayload(ClassLoader loader) throws Exception {
        Object location = Class.forName("net.minecraft.class_2960", true, loader).getMethod("method_60655", String.class, String.class).invoke(null, "vyne_fixture", "probe");
        payloadId = Class.forName("net.minecraft.class_8710$class_9154", true, loader).getConstructors()[0].newInstance(location);
        Class<?> payloadType = Class.forName("net.minecraft.class_8710", true, loader);
        Object strings = Class.forName("net.minecraft.class_9135", true, loader).getField("field_48554").get(null);
        java.util.function.Function<Object, Object> wrap = text -> Proxy.newProxyInstance(loader, new Class<?>[] {payloadType}, new Text((String) text));
        java.util.function.Function<Object, Object> unwrap = payload -> ((Text) Proxy.getInvocationHandler(payload)).value();
        Object codec = call(strings, "method_56432", wrap, unwrap);
        Object registry = Class.forName("net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry", true, loader).getMethod("playS2C").invoke(null);
        call(registry, "register", payloadId, codec);
    }

    static void payloads(ClassLoader loader, Object level) throws Exception {
        Class<?> packetType = Class.forName("net.minecraft.class_2658", true, loader);
        Object codec = packetType.getField("field_48620").get(null);
        Object payload = Proxy.newProxyInstance(loader, new Class<?>[] {Class.forName("net.minecraft.class_8710", true, loader)}, new Text("hello from Fabric"));
        Object packet = packetType.getConstructors()[0].newInstance(payload);
        Object raw = Class.forName("io.netty.buffer.Unpooled", true, loader).getMethod("buffer").invoke(null);
        Object buf = Class.forName("net.minecraft.class_9129", true, loader)
                .getConstructor(Class.forName("io.netty.buffer.ByteBuf", true, loader), Class.forName("net.minecraft.class_5455", true, loader))
                .newInstance(raw, call(level, "method_30349"));
        String result;
        try {
            call(codec, "encode", buf, packet);
            Object decoded = call(call(codec, "decode", buf), "comp_1646");
            result = Proxy.isProxyClass(decoded.getClass()) ? ((Text) Proxy.getInvocationHandler(decoded)).value() : "foreign:" + decoded;
        } catch (Exception e) {
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException ite ? ite.getCause() : e;
            result = "failed:" + cause;
        }
        System.out.println("[VyneFixture] payload: round-trip=" + result);
    }

    static void menus(ClassLoader loader, Object player) throws Exception {
        AtomicInteger closeAsked = new AtomicInteger();
        Class<?> factory = Class.forName("net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory", true, loader);
        Object provider = Proxy.newProxyInstance(loader, new Class<?>[] {factory}, (proxy, method, args) -> switch (method.getName()) {
            case "createMenu" -> Class.forName("net.minecraft.class_1707", true, loader).getMethod("method_19244", int.class, Class.forName("net.minecraft.class_1661", true, loader))
                    .invoke(null, args[0], args[1]);
            case "method_5476" -> Class.forName("net.minecraft.class_2561", true, loader).getMethod("method_43470", String.class).invoke(null, "probe");
            case "shouldCloseCurrentScreen" -> {
                closeAsked.incrementAndGet();
                yield false;
            }
            case "getScreenOpeningData" -> "data";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "fixture menu";
            default -> java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
        });
        Object open = Class.forName("net.minecraft.class_1707", true, loader).getMethod("method_19244", int.class, Class.forName("net.minecraft.class_1661", true, loader))
                .invoke(null, 99, call(player, "method_31548"));
        java.lang.reflect.Field containerMenu = Class.forName("net.minecraft.class_1657", true, loader).getDeclaredField("field_7512");
        containerMenu.setAccessible(true);
        containerMenu.set(player, open);
        String route;
        try {
            call(player, "openMenu", provider, (java.util.function.Consumer<Object>) buf -> {});
            route = "vanilla";
        } catch (java.lang.reflect.InvocationTargetException e) {
            route = String.valueOf(e.getCause()).contains("generic_9x1") ? "fabric" : "failed:" + e.getCause();
        }
        Object language = Class.forName("net.minecraft.class_2477", true, loader).getMethod("method_10517").invoke(null);
        System.out.println("[VyneFixture] menus: route=" + route + " close-asked=" + (closeAsked.get() > 0)
                + " language-stone=" + call(language, "method_48307", "block.minecraft.stone"));
    }

    private static Object stack(ClassLoader loader, String itemField) throws Exception {
        Object item = Class.forName("net.minecraft.class_1802", true, loader).getField(itemField).get(null);
        Class<?> convertible = Class.forName("net.minecraft.class_1935", true, loader);
        return Class.forName("net.minecraft.class_1799", true, loader).getConstructor(convertible).newInstance(item);
    }

    private static final AtomicInteger ARCH_BREAKS = new AtomicInteger();
    private static final AtomicInteger ARCH_ADDS = new AtomicInteger();

    static boolean registerArchitectury(ClassLoader loader) {
        try {
            Class.forName("dev.architectury.event.Event", false, loader);
        } catch (ClassNotFoundException e) {
            return false;
        }
        try {
            Class<?> results = Class.forName("dev.architectury.event.EventResult", true, loader);
            Object pass = results.getMethod("pass").invoke(null);
            Object veto = results.getMethod("interruptFalse").invoke(null);
            Class<?> pigType = Class.forName("net.minecraft.class_1452", true, loader);
            architecturyListen(loader, "common.BlockEvent", "BREAK", "Break", args -> {
                ARCH_BREAKS.incrementAndGet();
                return pass;
            });
            architecturyListen(loader, "common.EntityEvent", "ADD", "Add", args -> {
                if (!pigType.isInstance(args[0])) return pass;
                ARCH_ADDS.incrementAndGet();
                return veto;
            });
            return true;
        } catch (Exception e) {
            System.out.println("[VyneFixture] architectury listeners could not register: " + e);
            return false;
        }
    }

    static void architectury(ClassLoader loader, Object level, int x, int y, int z) throws Exception {
        Object pig = call(Class.forName("net.minecraft.class_1299", true, loader).getField("field_6093").get(null), "method_5883", level);
        call(pig, "method_5814", x + 0.5, (double) (y + 5), z + 0.5);
        boolean added = (boolean) call(level, "method_8649", pig);
        System.out.println("[VyneFixture] architectury: break-fired=" + (ARCH_BREAKS.get() > 0) + " pig-vetoed=" + (!added && ARCH_ADDS.get() > 0));
    }

    private static void architecturyListen(ClassLoader loader, String holder, String field, String type, Body body) throws Exception {
        String owner = "dev.architectury.event.events." + holder;
        Object event = Class.forName(owner, true, loader).getField(field).get(null);
        Class<?> listenerType = Class.forName(owner + "$" + type, true, loader);
        Object listener = Proxy.newProxyInstance(loader, new Class<?>[] {listenerType}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return method.invoke(EventBridgeProbe.class, args);
            return body.apply(args);
        });
        Class.forName("dev.architectury.event.Event", true, loader).getMethod("register", Object.class).invoke(event, listener);
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
