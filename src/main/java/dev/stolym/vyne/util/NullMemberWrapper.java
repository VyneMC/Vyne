/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.util;

import dev.stolym.vyne.vcl.VyneStubDefaults;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class NullMemberWrapper implements InvocationHandler {
    private final Object target;
    private final Class<?> targetInterface;

    private NullMemberWrapper(Object target, Class<?> targetInterface) {
        this.target = target;
        this.targetInterface = targetInterface;
    }

    public static Object wrap(Object obj, Class<?> itf) {
        if (!itf.isInterface()) return obj;
        return Proxy.newProxyInstance(itf.getClassLoader(), new Class<?>[]{itf}, new NullMemberWrapper(obj, itf));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals("equals")) return proxy == args[0];
        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
        if (method.getName().equals("toString")) return "VyneNullWrapper[" + targetInterface.getSimpleName() + " -> " + target + "]";

        if (target == null) {
            String desc = org.objectweb.asm.Type.getDescriptor(method.getReturnType());
            Object def = VyneStubDefaults.defaultFor(desc);
            if (def == null && method.getReturnType().isInterface()) {
                return wrap(null, method.getReturnType());
            }
            return def;
        }

        try {
            Object result = method.invoke(target, args);
            if (result == null && method.getReturnType().isInterface()) {
                return wrap(null, method.getReturnType());
            }
            return result;
        } catch (java.lang.reflect.InvocationTargetException ite) {
            throw ite.getCause();
        } catch (Throwable t) {
            if (method.getReturnType().isInterface()) {
                return wrap(null, method.getReturnType());
            }
            return VyneStubDefaults.defaultFor(org.objectweb.asm.Type.getDescriptor(method.getReturnType()));
        }
    }
}
