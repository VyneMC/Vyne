/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.fabric;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class FabricSleepHooks {
    private static final Map<Player, BlockPos> SLEEPING_AT = Collections.synchronizedMap(new WeakHashMap<>());

    private FabricSleepHooks() {}

    static void onCanPlayerSleep(CanPlayerSleepEvent event) {
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        Player.BedSleepingProblem problem = event.getProblem();
        problem = check(event, problem, Player.BedSleepingProblem.NOT_POSSIBLE_HERE, EntitySleepEvents.ALLOW_BED.invoker()
                .allowBed(player, pos, event.getState(), problem != Player.BedSleepingProblem.NOT_POSSIBLE_HERE));
        problem = check(event, problem, Player.BedSleepingProblem.NOT_POSSIBLE_NOW, EntitySleepEvents.ALLOW_SLEEP_TIME.invoker()
                .allowSleepTime(player, pos, problem != Player.BedSleepingProblem.NOT_POSSIBLE_NOW));
        problem = check(event, problem, Player.BedSleepingProblem.NOT_SAFE, EntitySleepEvents.ALLOW_NEARBY_MONSTERS.invoker()
                .allowNearbyMonsters(player, pos, problem != Player.BedSleepingProblem.NOT_SAFE));
        if (problem == null) SLEEPING_AT.put(player, pos);
        else SLEEPING_AT.remove(player);
    }

    static void onCanContinueSleeping(CanContinueSleepingEvent event) {
        LivingEntity entity = event.getEntity();
        Optional<BlockPos> pos = entity.getSleepingPos();
        if (pos.isEmpty()) return;
        boolean vanilla = event.mayContinueSleeping();
        Boolean decision = decision(EntitySleepEvents.ALLOW_BED.invoker().allowBed(entity, pos.get(), entity.level().getBlockState(pos.get()), vanilla));
        if (decision != null && decision != vanilla) event.setContinueSleeping(decision);
    }

    static void onSetSpawn(PlayerSetSpawnEvent event) {
        Player player = event.getEntity();
        BlockPos sleepingAt = SLEEPING_AT.remove(player);
        BlockPos pos = event.getNewSpawn();
        if (sleepingAt == null || !sleepingAt.equals(pos)) return;
        if (!EntitySleepEvents.ALLOW_SETTING_SPAWN.invoker().allowSettingSpawn(player, pos)) event.setCanceled(true);
    }

    static void onConversion(LivingConversionEvent.Post event) {
        if (event.getEntity() instanceof MushroomCow cow && event.getOutcome() instanceof Mob outcome) {
            ServerLivingEntityEvents.MOB_CONVERSION.invoker().onConversion(cow, outcome, false);
        }
    }

    public static boolean occupationHandled(LivingEntity entity, BlockPos pos, BlockState state, boolean occupied) {
        return EntitySleepEvents.SET_BED_OCCUPATION_STATE.invoker().setBedOccupationState(entity, pos, state, occupied);
    }

    public static Optional<Vec3> wakeUpPosition(LivingEntity entity, BlockPos pos, CollisionGetter level, Optional<Vec3> vanilla) {
        Vec3 modified = EntitySleepEvents.MODIFY_WAKE_UP_POSITION.invoker().modifyWakeUpPosition(entity, pos, level.getBlockState(pos), vanilla.orElse(null));
        return Optional.ofNullable(modified);
    }

    public static Direction sleepDirection(LivingEntity entity, Direction direction) {
        Optional<BlockPos> pos = entity.getSleepingPos();
        return pos.isEmpty() ? direction : sleepDirection(entity, pos.get(), direction);
    }

    public static Direction sleepDirection(LivingEntity entity, BlockPos pos, Direction direction) {
        Direction modified = EntitySleepEvents.MODIFY_SLEEPING_DIRECTION.invoker().modifySleepDirection(entity, pos, direction);
        return modified != null ? modified : direction;
    }

    private static Player.BedSleepingProblem check(CanPlayerSleepEvent event, Player.BedSleepingProblem problem,
                                                   Player.BedSleepingProblem kind, InteractionResult result) {
        Boolean decision = decision(result);
        if (decision == null) return problem;
        if (decision && problem == kind) {
            event.setProblem(null);
            return null;
        }
        if (!decision && problem == null) {
            event.setProblem(kind);
            return kind;
        }
        return problem;
    }

    private static Boolean decision(InteractionResult result) {
        if (result == null || result == InteractionResult.PASS) return null;
        return result != InteractionResult.FAIL;
    }
}
