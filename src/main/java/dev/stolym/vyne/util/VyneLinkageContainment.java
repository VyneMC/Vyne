/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.util;

import java.lang.reflect.Field;

public final class VyneLinkageContainment {
    private VyneLinkageContainment() {}

    public static boolean shouldContain(Throwable t) {
        Throwable root = rootCause(t);
        if (root instanceof LinkageError || root instanceof ClassNotFoundException) {
            return containsModFrame(t);
        }
        return false;
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    private static boolean containsModFrame(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            for (StackTraceElement ste : cur.getStackTrace()) {
                String name = ste.getClassName();
                if (name.startsWith("net.neoforged.")
                    || name.startsWith("net.minecraft.")
                    || name.startsWith("net.minecraftforge.")
                    || name.startsWith("dev.stolym.vyne.vcl.")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Object interactionResultPass(ClassLoader cl) {
        if (cl == null) {
            cl = Thread.currentThread().getContextClassLoader();
        }
        try {
            Class<?> c = Class.forName("net.minecraft.class_1269", false, cl);
            Field f = c.getField("field_5811");
            return f.get(null);
        } catch (Throwable t) {
            throw new IllegalStateException("Could not resolve InteractionResult.PASS", t);
        }
    }
}
