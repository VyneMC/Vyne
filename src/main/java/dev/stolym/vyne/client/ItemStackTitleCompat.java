/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.client;

import java.lang.reflect.Method;

public final class ItemStackTitleCompat {
    public static final String MAX_CHARS_PROPERTY = "vyne.ui.hoverTitleMaxChars";

    private ItemStackTitleCompat() {}

    public static Object maybeShortenHoverTitle(Object component) {
        if (component == null) {
            return null;
        }
        int max = Integer.getInteger(MAX_CHARS_PROPERTY, 0);
        if (max <= 0) {
            return component;
        }
        try {
            Method getString = component.getClass().getMethod("getString");
            String s = (String) getString.invoke(component);
            if (s == null || s.length() <= max) {
                return component;
            }
            String shortened = s.substring(0, Math.max(1, max - 1)) + "\u2026";
            Class<?> mutable = Class.forName("net.minecraft.class_2561", false,
                component.getClass().getClassLoader());
            Method literal = mutable.getMethod("method_43470", String.class);
            return literal.invoke(null, shortened);
        } catch (Throwable ignored) {
            return component;
        }
    }
}
