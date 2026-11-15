/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.common.capabilities;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class CapabilityToken<T> {
    private final Type typeToken;

    protected CapabilityToken() {
        Type sup = getClass().getGenericSuperclass();
        if (sup instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            this.typeToken = args.length == 1 ? args[0] : Object.class;
        } else {
            this.typeToken = Object.class;
        }
    }

    public Type getTypeToken() { return typeToken; }

    public String getTypeTokenName() {
        if (typeToken == null) return "vyne:anonymous";
        if (typeToken instanceof Class<?> c) return c.getName();
        return typeToken.toString();
    }
}
