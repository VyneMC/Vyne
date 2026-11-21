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

    private static final ThreadLocal<java.util.ArrayDeque<Object>> UNBAKED = ThreadLocal.withInitial(java.util.ArrayDeque::new);

    public static Object beforeBake(Object baker, Object id, Object unbaked, Object state, Function<?, ?> sprites) {
        Object modified = unbaked;
        Object dispatcher = dispatcher(baker);
        if (dispatcher != null) {
            modified = invoke(dispatcher, "modifyModelBeforeBake", 6, unbaked, id, null, sprites, state, baker);
        }
        UNBAKED.get().push(modified);
        return modified;
    }

    public static Object afterBake(Object baker, Object id, Object baked, Object state, Function<?, ?> sprites) {
        java.util.ArrayDeque<Object> stack = UNBAKED.get();
        Object unbaked = stack.isEmpty() ? null : stack.pop();
        Object dispatcher = dispatcher(baker);
        if (dispatcher == null) return baked;
        return invoke(dispatcher, "modifyModelAfterBake", 7, baked, id, null, unbaked, sprites, state, baker);
    }

    private static Object dispatcher(Object baker) {
        try {
            return Class.forName("net.fabricmc.fabric.impl.client.model.loading.ModelLoaderHooks", true, FabricModels.class.getClassLoader())
                    .getMethod("fabric_getDispatcher").invoke(bakery(baker));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Object invoke(Object dispatcher, String name, int parameters, Object... args) {
        try {
            return method(dispatcher, name, parameters).invoke(dispatcher, args);
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
