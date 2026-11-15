/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.compat;

public final class BlockStateBridge {
    private BlockStateBridge() {}

    private static volatile java.lang.reflect.Method SOUND_GETTER;
    private static volatile Object STONE_SOUND_FALLBACK;

    public static Object getSoundType(Object blockState,
                                      Object level,
                                      Object pos,
                                      Object entity) {
        if (blockState != null) {
            try {
                java.lang.reflect.Method m = SOUND_GETTER;
                if (m == null) {
                    Class<?> cls = Class.forName("net.minecraft.class_2680", false,
                        blockState.getClass().getClassLoader());
                    m = cls.getMethod("method_26231");
                    SOUND_GETTER = m;
                }
                Object result = m.invoke(blockState);
                if (result != null) return result;
            } catch (Throwable ignored) {
            }
        }
        return stoneFallback();
    }

    private static Object stoneFallback() {
        Object cached = STONE_SOUND_FALLBACK;
        if (cached != null) return cached;
        try {
            Class<?> cls = Class.forName("net.minecraft.class_2498", true,
                Thread.currentThread().getContextClassLoader());
            java.lang.reflect.Field f = cls.getField("field_11544");
            Object v = f.get(null);
            if (v != null) STONE_SOUND_FALLBACK = v;
            return v;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
