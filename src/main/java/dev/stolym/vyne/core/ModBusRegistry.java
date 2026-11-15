/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.core;

import dev.stolym.vyne.util.VyneLogger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModBusRegistry {
    private static final Map<String, Object> BUSES = Collections.synchronizedMap(new LinkedHashMap<>());

    private ModBusRegistry() {}

    public static void register(String modId, Object bus) {
        if (modId == null || bus == null) return;
        BUSES.putIfAbsent(modId, bus);
    }

    public static List<Object> buses() {
        synchronized (BUSES) {
            Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            List<Object> out = new ArrayList<>();
            for (Object bus : BUSES.values()) {
                if (seen.add(bus)) out.add(bus);
            }
            return out;
        }
    }

    public static List<String> modIds() {
        synchronized (BUSES) {
            return new ArrayList<>(BUSES.keySet());
        }
    }

    public static boolean postTo(String modId, Object event) {
        Object bus = BUSES.get(modId);
        return bus != null && postOn(bus, event);
    }

    public static int post(Object event) {
        int posted = 0;
        for (Object bus : buses()) {
            if (postOn(bus, event)) posted++;
        }
        return posted;
    }

    private static boolean postOn(Object bus, Object event) {
        try {
            Method post = null;
            for (Method m : bus.getClass().getMethods()) {
                if (m.getName().equals("post") && m.getParameterCount() == 1) {
                    post = m;
                    if (m.getParameterTypes()[0] == Object.class) break;
                }
            }
            if (post == null) return false;
            post.setAccessible(true);
            post.invoke(bus, event);
            return true;
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            VyneLogger.warn("[ModBus] " + event.getClass().getSimpleName() + " failed on a mod bus: " + cause);
            return false;
        }
    }
}
