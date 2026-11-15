/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.event.entity;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancelableEvent;

public class EntityJoinLevelEvent extends Event implements ICancelableEvent {
    private final net.minecraft.class_1297 entity;
    private final net.minecraft.class_1937 level;
    private final boolean loadedFromDisk;
    private boolean canceled;

    public EntityJoinLevelEvent() {
        this(null, null, false);
    }

    public EntityJoinLevelEvent(net.minecraft.class_1297 entity, net.minecraft.class_1937 level) {
        this(entity, level, false);
    }

    public EntityJoinLevelEvent(net.minecraft.class_1297 entity, net.minecraft.class_1937 level,
                                boolean loadedFromDisk) {
        this.entity = entity;
        this.level = level;
        this.loadedFromDisk = loadedFromDisk;
    }

    public net.minecraft.class_1297 getEntity() { return entity; }
    public net.minecraft.class_1937 getLevel() { return level; }
    public boolean loadedFromDisk() { return loadedFromDisk; }

    public boolean isCanceled() { return canceled; }
    public void setCanceled(boolean flag) { this.canceled = flag; }
}
