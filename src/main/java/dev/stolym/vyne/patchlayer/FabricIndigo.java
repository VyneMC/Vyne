/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import java.lang.reflect.Method;
import java.util.function.Function;

public final class FabricIndigo {
    private static final String CONTEXT = "net.fabricmc.fabric.impl.client.indigo.renderer.render.TerrainRenderContext";
    private static final String REGION = "net.fabricmc.fabric.impl.client.indigo.renderer.accessor.AccessChunkRendererRegion";
    private static final ThreadLocal<long[]> LAST_BLOCK = ThreadLocal.withInitial(() -> new long[] {Long.MIN_VALUE});
    private static volatile Method[] methods;

    private FabricIndigo() {}

    public static void prepare(Object region, Function<Object, Object> layers) {
        try {
            Method[] m = methods();
            Object context = ((ThreadLocal<?>) Class.forName(CONTEXT, true, FabricIndigo.class.getClassLoader()).getField("POOL").get(null)).get();
            m[0].invoke(context, region, layers);
            m[1].invoke(region, context);
            LAST_BLOCK.get()[0] = Long.MIN_VALUE;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Fabric's Indigo renderer could not prepare a chunk section", e);
        }
    }

    public static boolean render(Object region, Object dispatcher, Object state, Object pos, Object pose) {
        try {
            Method[] m = methods();
            if (m[5] == null) m[5] = find(dispatcher.getClass(), "method_3349", 1);
            if (m[7] == null) m[7] = find(pos.getClass(), "method_10063", 0);
            Object model = m[5].invoke(dispatcher, state);
            if ((boolean) m[6].invoke(model)) return false;
            long key = (long) m[7].invoke(pos);
            long[] last = LAST_BLOCK.get();
            if (last[0] == key) return true;
            last[0] = key;
            Object context = m[2].invoke(region);
            if (context == null) return false;
            m[3].invoke(context, state, pos, model, pose);
            return true;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Fabric's Indigo renderer could not render a block", e);
        }
    }

    public static void release(Object region) {
        try {
            Method[] m = methods();
            Object context = m[2].invoke(region);
            if (context == null) return;
            m[4].invoke(context);
            m[1].invoke(region, (Object) null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Fabric's Indigo renderer could not release a chunk section", e);
        }
    }

    private static Method find(Class<?> type, String name, int parameters) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameters) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static Method[] methods() throws ReflectiveOperationException {
        Method[] m = methods;
        if (m != null) return m;
        ClassLoader loader = FabricIndigo.class.getClassLoader();
        Class<?> context = Class.forName(CONTEXT, true, loader);
        Class<?> region = Class.forName(REGION, true, loader);
        m = new Method[8];
        m[6] = Class.forName("net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel", true, loader).getMethod("isVanillaAdapter");
        for (Method method : context.getMethods()) {
            if (method.getName().equals("prepare") && method.getParameterCount() == 2) m[0] = method;
            if (method.getName().equals("tessellateBlock") && method.getParameterCount() == 4) m[3] = method;
            if (method.getName().equals("release") && method.getParameterCount() == 0) m[4] = method;
        }
        m[1] = region.getMethod("fabric_setRenderer", context);
        m[2] = region.getMethod("fabric_getRenderer");
        for (int i = 0; i < 5; i++) {
            if (m[i] == null) throw new NoSuchMethodException("Indigo terrain renderer API");
        }
        methods = m;
        return m;
    }
}
