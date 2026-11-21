/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import dev.stolym.vyne.util.VyneLogger;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class FabricSleep {
    private static final String SLEEP = "net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents";
    private static final String LIVING = "net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents";
    private static final Map<Object, Object> SLEEPING_AT = Collections.synchronizedMap(new WeakHashMap<>());

    private FabricSleep() {}

    static void onCanPlayerSleep(Object event) {
        try {
            Object player = FabricEvents.call(event, "getEntity");
            Object pos = FabricEvents.call(event, "getPos");
            Object state = FabricEvents.call(event, "getState");
            Enum<?> problem = (Enum<?>) FabricEvents.call(event, "getProblem");
            Class<?> problems = problemType(event);
            problem = check(event, problems, problem, "NOT_POSSIBLE_HERE",
                    FabricEvents.fire(SLEEP, "ALLOW_BED", "allowBed", player, pos, state, !named(problem, "NOT_POSSIBLE_HERE")));
            problem = check(event, problems, problem, "NOT_POSSIBLE_NOW",
                    FabricEvents.fire(SLEEP, "ALLOW_SLEEP_TIME", "allowSleepTime", player, pos, !named(problem, "NOT_POSSIBLE_NOW")));
            problem = check(event, problems, problem, "NOT_SAFE",
                    FabricEvents.fire(SLEEP, "ALLOW_NEARBY_MONSTERS", "allowNearbyMonsters", player, pos, !named(problem, "NOT_SAFE")));
            if (problem == null) SLEEPING_AT.put(player, pos);
            else SLEEPING_AT.remove(player);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled("sleep checks", e);
        }
    }

    static void onCanContinueSleeping(Object event) {
        try {
            Object entity = FabricEvents.call(event, "getEntity");
            Optional<?> pos = (Optional<?>) FabricEvents.call(entity, "method_18398");
            if (pos.isEmpty()) return;
            Object level = FabricEvents.call(entity, "method_37908");
            Object state = FabricEvents.call(level, "method_8320", pos.get());
            boolean vanilla = (boolean) FabricEvents.call(event, "mayContinueSleeping");
            Boolean decision = FabricEvents.decision(FabricEvents.fire(SLEEP, "ALLOW_BED", "allowBed", entity, pos.get(), state, vanilla));
            if (decision != null && decision != vanilla) FabricEvents.call(event, "setContinueSleeping", decision);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled("bed checks while sleeping", e);
        }
    }

    static void onSetSpawn(Object event) {
        try {
            Object player = FabricEvents.call(event, "getEntity");
            Object sleepingAt = SLEEPING_AT.remove(player);
            Object pos = FabricEvents.call(event, "getNewSpawn");
            if (sleepingAt == null || !sleepingAt.equals(pos)) return;
            Object allowed = FabricEvents.fire(SLEEP, "ALLOW_SETTING_SPAWN", "allowSettingSpawn", player, pos);
            if (Boolean.FALSE.equals(allowed)) FabricEvents.call(event, "setCanceled", true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled("bed spawn points", e);
        }
    }

    static void onConversion(Object event) {
        try {
            Object entity = FabricEvents.call(event, "getEntity");
            if (!entity.getClass().getName().equals("net.minecraft.class_1438")) return;
            Object outcome = FabricEvents.call(event, "getOutcome");
            FabricEvents.fire(LIVING, "MOB_CONVERSION", "onConversion", entity, outcome, false);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled("mooshroom conversion", e);
        }
    }

    public static boolean occupationHandled(Object entity, Object pos, Object state, boolean occupied) {
        try {
            return Boolean.TRUE.equals(FabricEvents.fire(SLEEP, "SET_BED_OCCUPATION_STATE", "setBedOccupationState", entity, pos, state, occupied));
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled("bed occupation", e);
            return false;
        }
    }

    public static Optional<?> wakeUpPosition(Object entity, Object pos, Object level, Optional<?> vanilla) {
        try {
            Object state = FabricEvents.call(level, "method_8320", pos);
            Object modified = FabricEvents.fire(SLEEP, "MODIFY_WAKE_UP_POSITION", "modifyWakeUpPosition", entity, pos, state, vanilla.orElse(null));
            return Optional.ofNullable(modified);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled("wake-up position", e);
            return vanilla;
        }
    }

    public static Object sleepDirection(Object entity, Object direction) {
        try {
            Optional<?> sleeping = (Optional<?>) FabricEvents.call(entity, "method_18398");
            return sleeping.isEmpty() ? direction : sleepDirection(entity, sleeping.get(), direction);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled("sleeping direction", e);
            return direction;
        }
    }

    public static Object sleepDirection(Object entity, Object pos, Object direction) {
        try {
            Object modified = FabricEvents.fire(SLEEP, "MODIFY_SLEEPING_DIRECTION", "modifySleepDirection", entity, pos, direction);
            return modified != null ? modified : direction;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled("sleeping direction", e);
            return direction;
        }
    }

    private static Enum<?> check(Object event, Class<?> problems, Enum<?> problem, String name, Object result) throws ReflectiveOperationException {
        Boolean decision = FabricEvents.decision(result);
        if (decision == null) return problem;
        if (decision && named(problem, name)) {
            FabricEvents.call(event, "setProblem", (Object) null);
            return null;
        }
        if (!decision && problem == null) {
            Enum<?> value = enumConstant(problems, name);
            FabricEvents.call(event, "setProblem", value);
            return value;
        }
        return problem;
    }

    private static boolean named(Enum<?> problem, String name) {
        return problem != null && problem.name().equals(name);
    }

    private static Class<?> problemType(Object event) throws ReflectiveOperationException {
        return Class.forName("net.minecraft.class_1657$class_1658", true, event.getClass().getClassLoader());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Enum<?> enumConstant(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }

    private static void disabled(String what, Throwable t) {
        VyneLogger.warn("[PatchLayer] Fabric " + what + " could not be answered: " + t);
    }
}
