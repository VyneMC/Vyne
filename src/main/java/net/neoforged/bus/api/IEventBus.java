/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.bus.api;
public interface IEventBus {
    void addListener(java.util.function.Consumer<?> consumer);
    void addListener(net.neoforged.bus.api.EventPriority priority, java.util.function.Consumer<?> consumer);
    void addListener(net.neoforged.bus.api.EventPriority priority, boolean receiveCancelled, java.util.function.Consumer<?> consumer);

    void addListener(net.neoforged.bus.api.EventPriority priority, boolean receiveCancelled,
                     java.lang.Class<?> eventType, java.util.function.Consumer<?> consumer);

    void addListener(java.lang.Class<?> eventType, java.util.function.Consumer<?> consumer);

    void addListener(net.neoforged.bus.api.EventPriority priority, java.lang.Class<?> eventType,
                     java.util.function.Consumer<?> consumer);

    void register(Object obj);
    void unregister(Object obj);
    void post(Object event);
}
