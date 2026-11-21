/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.patchlayer;

import dev.stolym.vyne.util.VyneLogger;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class FabricItems {
    private static final ThreadLocal<Object> LOADING_FROM = new ThreadLocal<>();
    private static volatile Method recipeRemainder;
    private static volatile Method canBeEnchantedWith;
    private static volatile Method isEmpty;
    private static volatile Object primary;
    private static volatile Object acceptable;

    private FabricItems() {}

    public static Object remainder(Object item, Object stack) {
        try {
            Method method = recipeRemainder;
            if (method == null) {
                method = Class.forName("net.fabricmc.fabric.api.item.v1.FabricItem", true, FabricItems.class.getClassLoader())
                        .getMethod("getRecipeRemainder", stack.getClass());
                recipeRemainder = method;
            }
            return method.invoke(item, stack);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw failure("recipe remainders", e);
        }
    }

    public static boolean hasRemainder(Object item, Object stack) {
        try {
            Object remainder = remainder(item, stack);
            Method method = isEmpty;
            if (method == null) {
                method = remainder.getClass().getMethod("method_7960");
                isEmpty = method;
            }
            return !(boolean) method.invoke(remainder);
        } catch (ReflectiveOperationException e) {
            throw failure("recipe remainders", e);
        }
    }

    public static boolean canEnchant(Object stack, Object enchantment, boolean primaryItem) {
        try {
            Method method = canBeEnchantedWith;
            if (method == null) {
                Class<?> context = Class.forName("net.fabricmc.fabric.api.item.v1.EnchantingContext", true, FabricItems.class.getClassLoader());
                primary = context.getField("PRIMARY").get(null);
                acceptable = context.getField("ACCEPTABLE").get(null);
                method = Class.forName("net.fabricmc.fabric.api.item.v1.FabricItemStack", true, FabricItems.class.getClassLoader())
                        .getMethod("canBeEnchantedWith", Class.forName("net.minecraft.class_6880", true, stack.getClass().getClassLoader()), context);
                canBeEnchantedWith = method;
            }
            return (boolean) method.invoke(stack, enchantment, primaryItem ? primary : acceptable);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw failure("enchanting checks", e);
        }
    }

    public static int damage(Object item, Object stack, int amount, Object entity, Consumer<Object> onBroken) {
        if (entity == null) return amount;
        try {
            Object handler = item.getClass().getMethod("fabric_getCustomDamageHandler").invoke(item);
            if (handler == null || (boolean) FabricEvents.call(entity, "method_56992")) return amount;
            Object slot = slot(entity, stack);
            Runnable breakCallback = () -> {
                try {
                    FabricEvents.call(stack, "method_7934", 1);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
                onBroken.accept(item);
            };
            Method damage = null;
            for (Method m : handler.getClass().getMethods()) {
                if (m.getName().equals("damage") && m.getParameterCount() == 5) damage = m;
            }
            damage.setAccessible(true);
            return (int) damage.invoke(handler, stack, amount, entity, slot, breakCallback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw failure("custom item damage", e);
        }
    }

    private static Object slot(Object entity, Object stack) throws ReflectiveOperationException {
        Object[] slots = Class.forName("net.minecraft.class_1304", true, stack.getClass().getClassLoader()).getEnumConstants();
        for (Object slot : slots) {
            if (FabricEvents.call(entity, "method_6118", slot) == stack) return slot;
        }
        return slots[0];
    }

    public static void loadingFrom(Object resource) {
        if (resource == null) LOADING_FROM.remove();
        else LOADING_FROM.set(resource);
    }

    public static Object modifyEnchantment(Object key, Object value) {
        Object resource = LOADING_FROM.get();
        if (resource == null || !value.getClass().getName().equals("net.minecraft.class_1887")) return value;
        try {
            Class<?> util = Class.forName("net.fabricmc.fabric.impl.item.EnchantmentUtil", true, FabricItems.class.getClassLoader());
            Object source = null;
            Method modify = null;
            for (Method m : util.getMethods()) {
                if (m.getName().equals("determineSource")) source = m.invoke(null, resource);
                if (m.getName().equals("modify")) modify = m;
            }
            return modify.invoke(null, key, value, source);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw failure("enchantment modifications", e);
        }
    }

    private static IllegalStateException failure(String what, Throwable t) {
        VyneLogger.warn("[PatchLayer] Fabric " + what + " could not be answered: " + t);
        return new IllegalStateException("Fabric " + what + " are unavailable on Vyne's patch layer", t);
    }
}
