/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.client.event;

import net.neoforged.bus.api.Event;

public class EntityRenderersEvent extends Event {
    public static class RegisterRenderers extends EntityRenderersEvent {
        public <T> void registerEntityRenderer(Object entityType, Object rendererProvider) {
        }

        public <T> void registerBlockEntityRenderer(Object blockEntityType, Object rendererProvider) {
        }
    }

    public static class AddLayers extends EntityRenderersEvent {
        public java.util.Map<net.minecraft.class_1299<?>, Object> getRenderers() {
            return java.util.Collections.emptyMap();
        }

        public Object getRenderer(net.minecraft.class_1299<?> type) {
            return null;
        }

        public Object getSkin(Object model) {
            return null;
        }

        public Object getPlayerSkin(Object model) {
            return null;
        }

        public Context getContext() {
            return new Context();
        }

        public Iterable<net.minecraft.class_1299<?>> getEntityTypes() {
            return java.util.Collections.emptyList();
        }

        public static class Context {
            public EntityRenderDispatcherShim getEntityRenderDispatcher() {
                return new EntityRenderDispatcherShim();
            }
        }

        public static class EntityRenderDispatcherShim {
            public final java.util.Map<net.minecraft.class_1299<?>, Object> renderers =
                    java.util.Collections.emptyMap();
        }
    }

    public static class RegisterLayerDefinitions extends EntityRenderersEvent {
        public void registerLayerDefinition(Object location, java.util.function.Supplier<Object> supplier) {
        }
    }
}
