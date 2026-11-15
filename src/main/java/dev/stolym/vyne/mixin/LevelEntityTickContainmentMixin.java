/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.stolym.vyne.server.ServerEntityTickContainment;
import dev.stolym.vyne.server.ServerLagMitigationConfig;
import dev.stolym.vyne.server.ServerLagMitigator;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Consumer;

@Mixin(targets = "net.minecraft.class_1937")
public abstract class LevelEntityTickContainmentMixin {
    @WrapMethod(method = "method_18472")
    private void vyne$wrapGuardEntityTick(Consumer<Entity> consumer, Entity entity, Operation<Void> original) {
        if (!"net.minecraft.class_3218".equals(this.getClass().getName())) {
            original.call(consumer, entity);
            return;
        }
        boolean profile = ServerLagMitigationConfig.shouldSampleEntityTicks();
        long t0 = profile ? System.nanoTime() : 0L;
        try {
            original.call(consumer, entity);
        } catch (Throwable t) {
            if (profile) {
                ServerLagMitigator.recordEntityTickNanos(entity, System.nanoTime() - t0);
            }
            if (ServerEntityTickContainment.shouldAbsorbEntityTick(t)) {
                ServerEntityTickContainment.logAbsorbed(t, entity);
                ServerEntityTickContainment.tryDiscardEntity(entity);
                return;
            }
            if (t instanceof Error err) {
                throw err;
            }
            if (t instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(t);
        }
        if (profile) {
            ServerLagMitigator.recordEntityTickNanos(entity, System.nanoTime() - t0);
        }
    }
}
