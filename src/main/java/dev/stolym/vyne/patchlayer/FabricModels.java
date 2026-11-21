/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Function;

public final class FabricModels {
    private FabricModels() {}

    public interface Bake {
        Object bake(Object unbaked);
    }

    public static Object bake(Object baker, Object id, Object unbaked, Object state, Function<?, ?> sprites, Bake original) {
        Object dispatcher;
        try {
            Object bakery = bakery(baker);
            dispatcher = Class.forName("net.fabricmc.fabric.impl.client.model.loading.ModelLoaderHooks", true, FabricModels.class.getClassLoader())
                    .getMethod("fabric_getDispatcher").invoke(bakery);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return original.bake(unbaked);
        }
        if (dispatcher == null) return original.bake(unbaked);
        try {
            Object modified = method(dispatcher, "modifyModelBeforeBake", 6).invoke(dispatcher, unbaked, id, null, sprites, state, baker);
            Object baked = original.bake(modified);
            return method(dispatcher, "modifyModelAfterBake", 7).invoke(dispatcher, baked, id, null, modified, sprites, state, baker);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Fabric model modifiers are unavailable on Vyne's patch layer", e);
        }
    }

    private static Object bakery(Object baker) throws ReflectiveOperationException {
        for (Class<?> c = baker.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("field_40571");
                f.setAccessible(true);
                return f.get(baker);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException("ModelBakerImpl.modelBakery");
    }

    private static Method method(Object owner, String name, int parameters) throws NoSuchMethodException {
        for (Method m : owner.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == parameters) return m;
        }
        throw new NoSuchMethodException(owner.getClass().getName() + "." + name);
    }
}
