/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class FabricMenus {
    private static final String EXTENDED = "net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory";

    private FabricMenus() {}

    public static boolean shouldCloseCurrentScreen(Object provider) {
        try {
            Method method = provider.getClass().getMethod("shouldCloseCurrentScreen");
            method.setAccessible(true);
            return (boolean) method.invoke(provider);
        } catch (NoSuchMethodException e) {
            return true;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Fabric's shouldCloseCurrentScreen failed for " + provider, e);
        }
    }

    public static boolean openedByFabric(Object player, Object provider, Object menu) {
        ClassLoader loader = FabricMenus.class.getClassLoader();
        Class<?> extended;
        try {
            extended = Class.forName(EXTENDED, false, loader);
        } catch (ClassNotFoundException e) {
            return false;
        }
        if (!extended.isInstance(provider)) return false;
        try {
            int syncId = containerId(menu);
            for (Method m : Class.forName("net.fabricmc.fabric.impl.screenhandler.Networking", true, loader).getMethods()) {
                if (m.getName().equals("sendOpenPacket") && m.getParameterCount() == 4) {
                    m.invoke(null, player, provider, menu, syncId);
                    return true;
                }
            }
            throw new NoSuchMethodException("Networking.sendOpenPacket");
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Fabric extended menus are unavailable on Vyne's patch layer", e);
        }
    }

    private static int containerId(Object menu) throws ReflectiveOperationException {
        for (Class<?> c = menu.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("field_7763");
                f.setAccessible(true);
                return f.getInt(menu);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException("AbstractContainerMenu.containerId");
    }
}
