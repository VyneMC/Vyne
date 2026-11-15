/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.network.registration;

import dev.stolym.vyne.network.VynePayloadRegistration;
import dev.stolym.vyne.util.VyneLogger;

import java.lang.reflect.Proxy;

public interface IPayloadRegistrar {
    static IPayloadRegistrar of(String modId) {
        return createProxy(modId);
    }

    String getModId();

    default IPayloadRegistrar play(Object type, Object reader) { return (IPayloadRegistrar)this; }
    default IPayloadRegistrar play(Object type, Object reader, Object handler) { return (IPayloadRegistrar)this; }
    default IPayloadRegistrar configuration(Object type, Object reader) { return (IPayloadRegistrar)this; }
    default IPayloadRegistrar configuration(Object type, Object reader, Object handler) { return (IPayloadRegistrar)this; }
    default IPayloadRegistrar common(Object type, Object reader) { return (IPayloadRegistrar)this; }
    default IPayloadRegistrar common(Object type, Object reader, Object handler) { return (IPayloadRegistrar)this; }

    static IPayloadRegistrar createProxy(String modId) {
        return (IPayloadRegistrar) Proxy.newProxyInstance(
            IPayloadRegistrar.class.getClassLoader(),
            new Class<?>[]{IPayloadRegistrar.class, net.neoforged.neoforge.network.IPayloadRegistrar.class},
            (proxy, method, args) -> {
                try {
                    String name = method.getName();
                    if (method.getDeclaringClass() == Object.class) {
                        if ("toString".equals(name)) {
                            return "VynePayloadRegistrar[" + modId + "]";
                        }
                        if ("hashCode".equals(name)) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(name)) {
                            return proxy == (args != null && args.length > 0 ? args[0] : null);
                        }
                    }

                    if ("getModId".equals(name)) {
                        return modId;
                    }

                    Class<?> ret = method.getReturnType();
                    if (ret == void.class || ret == Void.class) {
                        if (shouldRecord(name)) {
                            VynePayloadRegistration.record(modId, name, args);
                        }
                        return null;
                    }
                    if (IPayloadRegistrar.class.isAssignableFrom(ret)
                            || net.neoforged.neoforge.network.IPayloadRegistrar.class.isAssignableFrom(ret)) {
                        if (shouldRecord(name)) {
                            VynePayloadRegistration.record(modId, name, args);
                        }
                        return proxy;
                    }

                    if (shouldRecord(name)) {
                        VynePayloadRegistration.record(modId, name, args);
                    }
                    if (ret.isPrimitive()) {
                        if (ret == boolean.class) {
                            return false;
                        }
                        if (ret == int.class) {
                            return 0;
                        }
                        if (ret == long.class) {
                            return 0L;
                        }
                    }
                    return null;
                } catch (Throwable t) {
                    VyneLogger.warn("[Vyne-Net] PayloadRegistrar proxy error for " + modId
                        + "#" + method.getName() + ": " + t.getMessage());
                    if (t instanceof RuntimeException re) {
                        throw re;
                    }
                    if (t instanceof Error err) {
                        throw err;
                    }
                    throw new RuntimeException(t);
                }
            });
    }

    private static boolean shouldRecord(String name) {
        return "play".equals(name) || "configuration".equals(name) || "common".equals(name)
            || "register".equals(name);
    }
}
