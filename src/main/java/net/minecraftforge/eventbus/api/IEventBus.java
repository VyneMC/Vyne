/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.minecraftforge.eventbus.api;

public interface IEventBus {
    void register(Object target);
    void post(Object event);
    void addListener(java.util.function.Consumer<?> consumer);
}
