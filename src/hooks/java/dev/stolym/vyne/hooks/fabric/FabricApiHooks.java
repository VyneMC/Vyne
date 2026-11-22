/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.hooks.fabric;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.impl.content.registry.FuelRegistryImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;

public final class FabricApiHooks {
    private static boolean registered;

    private FabricApiHooks() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        FabricLoader fabric = FabricLoader.getInstance();
        IEventBus bus = NeoForge.EVENT_BUS;
        if (fabric.isModLoaded("fabric-content-registries-v0")) {
            bus.addListener(FabricApiHooks::onBurnTime);
            LogUtils.getLogger().info("[Hooks] Fabric furnace fuels answered through NeoForge's FurnaceFuelBurnTimeEvent");
        }
        if (fabric.isModLoaded("fabric-entity-events-v1")) {
            bus.addListener(FabricSleepHooks::onCanPlayerSleep);
            bus.addListener(FabricSleepHooks::onCanContinueSleeping);
            bus.addListener(FabricSleepHooks::onSetSpawn);
            bus.addListener(FabricSleepHooks::onConversion);
            LogUtils.getLogger().info("[Hooks] Fabric sleep and mob conversion events answered through NeoForge's sleep, spawn and conversion events");
        }
        if (fabric.isModLoaded("fabric-events-interaction-v0")) {
            bus.addListener(FabricApiHooks::onEntityInteractSpecific);
            LogUtils.getLogger().info("[Hooks] Fabric UseEntityCallback answered on the client through NeoForge's EntityInteractSpecific");
        }
    }

    private static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.getLevel().isClientSide()) return;
        Entity target = event.getTarget();
        EntityHitResult hit = new EntityHitResult(target, event.getLocalPos().add(target.position()));
        InteractionResult result = UseEntityCallback.EVENT.invoker().interact(event.getEntity(), event.getLevel(), event.getHand(), target, hit);
        if (result == null || result == InteractionResult.PASS) return;
        event.setCancellationResult(result);
        event.setCanceled(true);
    }

    private static void onBurnTime(FurnaceFuelBurnTimeEvent event) {
        if (event.getBurnTime() > 0) return;
        ItemStack stack = event.getItemStack();
        Integer ticks = ((FuelRegistryImpl) FuelRegistryImpl.INSTANCE).getFuelTimes().get(stack.getItem());
        if (ticks != null && ticks > 0) event.setBurnTime(ticks);
    }
}
