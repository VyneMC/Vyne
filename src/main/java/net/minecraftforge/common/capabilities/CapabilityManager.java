/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package net.minecraftforge.common.capabilities;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class CapabilityManager {
    private static final Map<String, Capability<?>> CACHE = new ConcurrentHashMap<>();

    private CapabilityManager() {}

    @SuppressWarnings("unchecked")
    public static <T> Capability<T> get(CapabilityToken<T> token) {
        String key = token == null ? "vyne:null-token" : token.getTypeTokenName();
        return (Capability<T>) CACHE.computeIfAbsent(key, Capability::new);
    }
}
