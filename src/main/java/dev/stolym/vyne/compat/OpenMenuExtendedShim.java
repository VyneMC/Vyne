/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.compat;

import dev.stolym.vyne.util.VyneLogger;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

public final class OpenMenuExtendedShim {
    private OpenMenuExtendedShim() {}

    private static final String EXT_FACTORY_CLASS =
        "net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory";

    private static volatile Method VANILLA_OPEN_MENU;

    private static volatile Class<?> CACHED_MENU_PROVIDER;

    public static void invokeVanillaOpenMenu(Object serverPlayer, Object provider) throws Throwable {
        if (serverPlayer == null) return;
        Method m = VANILLA_OPEN_MENU;
        if (m == null || !m.getDeclaringClass().isInstance(serverPlayer)) {
            m = resolveVanillaOpenMenu(serverPlayer);
            if (m != null) {
                VANILLA_OPEN_MENU = m;
            }
        }
        if (m == null) {
            VyneLogger.warn("[OpenMenuShim] Could not find vanilla openMenu(MenuProvider) on "
                + serverPlayer.getClass().getName() + " — menu won't open.");
            return;
        }
        try {
            m.invoke(serverPlayer, provider);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            throw ite.getCause() != null ? ite.getCause() : ite;
        }
    }

    private static Method resolveVanillaOpenMenu(Object serverPlayer) {
        for (Class<?> c = serverPlayer.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() != 1) continue;
                Class<?>[] p = m.getParameterTypes();
                if (!"class_3908".equals(p[0].getSimpleName()) && !"MenuProvider".equals(p[0].getSimpleName())) {
                    continue;
                }
                if (!java.util.OptionalInt.class.equals(m.getReturnType())) continue;
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    public static Object wrapAsExtended(Object provider, Consumer<?> extraDataWriter) {
        if (provider == null) {
            return null;
        }
        if (extraDataWriter == null) {
            return provider;
        }

        ClassLoader cl = pickClassLoader(provider);
        Class<?> extFactoryCls;
        try {
            extFactoryCls = Class.forName(EXT_FACTORY_CLASS, false, cl);
        } catch (ClassNotFoundException cnfe) {
            VyneLogger.debug("[OpenMenuShim] Fabric screen-handler API not on classpath; "
                + "opening menu without extra data (provider="
                + provider.getClass().getName() + ").");
            return provider;
        }

        Class<?> menuProviderCls = resolveMenuProvider(provider, cl);
        if (menuProviderCls == null) {
            VyneLogger.debug("[OpenMenuShim] Could not resolve class_3908; "
                + "returning raw provider. provider=" + provider.getClass().getName());
            return provider;
        }

        try {
            return Proxy.newProxyInstance(
                cl,
                new Class<?>[] { extFactoryCls, menuProviderCls },
                new Handler(provider, extraDataWriter)
            );
        } catch (Throwable t) {
            VyneLogger.warn("[OpenMenuShim] Failed to build ExtendedScreenHandlerFactory proxy: " + t
                + " — falling back to vanilla openMenu(provider). provider="
                + provider.getClass().getName());
            return provider;
        }
    }

    private static Class<?> resolveMenuProvider(Object provider, ClassLoader preferred) {
        Class<?> cached = CACHED_MENU_PROVIDER;
        if (cached != null && cached.isInstance(provider)) return cached;

        ClassLoader[] candidates = {
            preferred,
            provider.getClass().getClassLoader(),
            Thread.currentThread().getContextClassLoader(),
            OpenMenuExtendedShim.class.getClassLoader()
        };
        for (ClassLoader cl : candidates) {
            if (cl == null) continue;
            try {
                Class<?> c = Class.forName("net.minecraft.class_3908", false, cl);
                if (c.isInstance(provider)) {
                    CACHED_MENU_PROVIDER = c;
                    return c;
                }
            } catch (ClassNotFoundException ignored) {
            }
        }

        for (Class<?> c = provider.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Class<?> itf : c.getInterfaces()) {
                if ("class_3908".equals(itf.getSimpleName())
                    || "MenuProvider".equals(itf.getSimpleName())) {
                    CACHED_MENU_PROVIDER = itf;
                    return itf;
                }
            }
        }
        return null;
    }

    private static ClassLoader pickClassLoader(Object provider) {
        ClassLoader cl = provider.getClass().getClassLoader();
        if (cl != null) return cl;
        cl = Thread.currentThread().getContextClassLoader();
        if (cl != null) return cl;
        return OpenMenuExtendedShim.class.getClassLoader();
    }

    private static final class Handler implements InvocationHandler {
        private final Object provider;
        private final Consumer<?> writer;

        Handler(Object provider, Consumer<?> writer) {
            this.provider = provider;
            this.writer = writer;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "equals": return proxy == args[0];
                    case "hashCode": return System.identityHashCode(proxy);
                    case "toString":
                        return "ExtFactoryProxy(" + provider.getClass().getName() + ")";
                    default: break;
                }
            }

            if ("getScreenOpeningData".equals(method.getName()) && method.getParameterCount() == 1) {
                Object player = args.length > 0 ? args[0] : null;
                return buildBufferAndWrite(player);
            }

            try {
                Method target = findOnProvider(method);
                if (target != null) {
                    target.setAccessible(true);
                    return target.invoke(provider, args);
                }
            } catch (java.lang.reflect.InvocationTargetException ite) {
                throw ite.getCause() != null ? ite.getCause() : ite;
            }

            Class<?> rt = method.getReturnType();
            return defaultFor(rt);
        }

        private Object buildBufferAndWrite(Object serverPlayer) {
            try {
                ClassLoader cl = provider.getClass().getClassLoader();
                Object registryAccess = resolveRegistryAccess(serverPlayer);

                Class<?> byteBufCls = Class.forName("io.netty.buffer.ByteBuf", false, cl);
                Class<?> unpooledCls = Class.forName("io.netty.buffer.Unpooled", false, cl);
                Object rawBuf = unpooledCls.getMethod("buffer").invoke(null);

                Class<?> regBuf = Class.forName("net.minecraft.class_9129", false, cl);
                Object buf = null;
                for (java.lang.reflect.Constructor<?> ctor : regBuf.getDeclaredConstructors()) {
                    if (ctor.getParameterCount() != 2) continue;
                    Class<?>[] p = ctor.getParameterTypes();
                    if (!byteBufCls.isAssignableFrom(p[0])) continue;
                    if (registryAccess != null && !p[1].isInstance(registryAccess)) continue;
                    ctor.setAccessible(true);
                    buf = ctor.newInstance(rawBuf, registryAccess);
                    break;
                }
                if (buf == null) {
                    VyneLogger.debug("[OpenMenuShim] Could not construct class_9129; "
                        + "skipping extra-data encoding.");
                    return null;
                }
                @SuppressWarnings({"unchecked", "rawtypes"})
                Consumer consumer = this.writer;
                consumer.accept(buf);
                return buf;
            } catch (Throwable t) {
                VyneLogger.warn("[OpenMenuShim] getScreenOpeningData failed: " + t);
                return null;
            }
        }

        private Object resolveRegistryAccess(Object serverPlayer) {
            if (serverPlayer == null) return null;
            try {
                for (Class<?> c = serverPlayer.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                    for (Method m : c.getDeclaredMethods()) {
                        if (m.getParameterCount() != 0) continue;
                        String n = m.getName();
                        if (!"registryAccess".equals(n) && !"method_30349".equals(n)) continue;
                        m.setAccessible(true);
                        return m.invoke(serverPlayer);
                    }
                }
            } catch (Throwable ignored) {
            }
            return null;
        }

        private Method findOnProvider(Method wanted) {
            Class<?>[] params = wanted.getParameterTypes();
            for (Class<?> c = provider.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    return c.getDeclaredMethod(wanted.getName(), params);
                } catch (NoSuchMethodException ignored) {
                }
            }
            for (Method m : provider.getClass().getMethods()) {
                if (!m.getName().equals(wanted.getName())) continue;
                if (m.getParameterCount() != wanted.getParameterCount()) continue;
                return m;
            }
            return null;
        }

        private static Object defaultFor(Class<?> rt) {
            if (rt == boolean.class) return Boolean.FALSE;
            if (rt == int.class) return 0;
            if (rt == long.class) return 0L;
            if (rt == short.class) return (short) 0;
            if (rt == byte.class) return (byte) 0;
            if (rt == char.class) return (char) 0;
            if (rt == float.class) return 0.0f;
            if (rt == double.class) return 0.0d;
            if (rt == void.class) return null;
            return null;
        }
    }
}
