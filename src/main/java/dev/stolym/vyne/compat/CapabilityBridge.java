/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.compat;

import dev.stolym.vyne.util.VyneLogger;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CapabilityBridge {
    private CapabilityBridge() {}

    private static final Map<Object, List<ICapabilityProvider>> PROVIDERS =
            Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static final Map<Object, Boolean> ATTACH_IN_PROGRESS =
            Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    public static LazyOptional<?> getCapability(Object target, Capability<?> cap, Object side) {
        if (target == null || cap == null) return LazyOptional.empty();
        List<ICapabilityProvider> list = PROVIDERS.get(target);
        if (list == null) return LazyOptional.empty();
        net.minecraft.class_2350 direction = side instanceof net.minecraft.class_2350 d ? d : null;
        for (ICapabilityProvider provider : list) {
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                LazyOptional<?> opt = provider.getCapability((Capability) cap, direction);
                if (opt != null && opt.isPresent()) {
                    return opt;
                }
            } catch (Throwable t) {
                VyneLogger.warn("[CapBridge] Provider " + provider.getClass().getSimpleName()
                        + " threw on cap " + cap + ": " + t);
            }
        }
        return LazyOptional.empty();
    }

    public static LazyOptional<?> getCapabilityUnsided(Object target, Capability<?> cap) {
        return getCapability(target, cap, null);
    }

    public static LazyOptional<?> getCapability(Object target, Capability<?> cap,
                                                 net.minecraft.class_2350 side) {
        return getCapability(target, cap, (Object) side);
    }

    public static Capability<?> getCapabilityByToken(Object token) {
        return TOKEN_PLACEHOLDER;
    }

    private static final Capability<?> TOKEN_PLACEHOLDER;
    static {
        Capability<?> placeholder;
        try {
            java.lang.reflect.Constructor<?> ctor = Capability.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            placeholder = (Capability<?>) ctor.newInstance();
        } catch (Throwable t) {
            placeholder = null;
        }
        TOKEN_PLACEHOLDER = placeholder;
    }

    public static void attachDefaultProviders(Object target) {
        if (target == null) return;
        if (PROVIDERS.containsKey(target)) return;

        if (Boolean.TRUE.equals(ATTACH_IN_PROGRESS.putIfAbsent(target, Boolean.TRUE))) {
            return;
        }
        try {
            net.minecraftforge.event.AttachCapabilitiesEvent<Object> event =
                    new net.minecraftforge.event.AttachCapabilitiesEvent<>((Class<Object>) target.getClass(), target);
            try {
                net.neoforged.bus.api.VyneEventBus.global().post(event);
            } catch (Throwable t) {
                VyneLogger.warn("[CapBridge] AttachCapabilitiesEvent dispatch for "
                        + target.getClass().getSimpleName() + " failed: " + t);
                return;
            }
            Map<net.minecraft.resources.ResourceLocation, ICapabilityProvider> raw =
                    event.getCapabilities();
            if (raw.isEmpty()) {
                PROVIDERS.put(target, Collections.emptyList());
                return;
            }
            List<ICapabilityProvider> list = new ArrayList<>(raw.values());
            PROVIDERS.put(target, list);
        } finally {
            ATTACH_IN_PROGRESS.remove(target);
        }
    }

    public static void reset() {
        PROVIDERS.clear();
    }
}
