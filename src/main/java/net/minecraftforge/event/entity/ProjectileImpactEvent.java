/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.event.entity;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancelableEvent;

public class ProjectileImpactEvent extends Event implements ICancelableEvent {
    private boolean canceled;
    public Object getProjectile() { return null; }
    public Object getRayTraceResult() { return null; }
    public boolean isCanceled() { return canceled; }
    public void setCanceled(boolean flag) { this.canceled = flag; }

    public static class Arrow extends ProjectileImpactEvent {}
    public static class Fireball extends ProjectileImpactEvent {}
    public static class Throwable extends ProjectileImpactEvent {}
}
