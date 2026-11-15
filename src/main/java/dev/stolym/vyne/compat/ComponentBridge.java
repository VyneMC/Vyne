/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.compat;

public final class ComponentBridge {
    private ComponentBridge() {}

    private static volatile java.lang.reflect.Method EMPTY_FACTORY;
    private static volatile java.lang.reflect.Method LITERAL_FACTORY;

    public static Object empty() {
        try {
            java.lang.reflect.Method m = EMPTY_FACTORY;
            if (m == null) {
                Class<?> cls = Class.forName("net.minecraft.class_2561", true,
                    Thread.currentThread().getContextClassLoader());
                m = cls.getMethod("method_43473");
                EMPTY_FACTORY = m;
            }
            return m.invoke(null);
        } catch (Throwable ignored) {
            try {
                java.lang.reflect.Method lit = LITERAL_FACTORY;
                if (lit == null) {
                    Class<?> cls = Class.forName("net.minecraft.class_2561", true,
                        Thread.currentThread().getContextClassLoader());
                    lit = cls.getMethod("method_43470", String.class);
                    LITERAL_FACTORY = lit;
                }
                return lit.invoke(null, "");
            } catch (Throwable inner) {
                return null;
            }
        }
    }
}
