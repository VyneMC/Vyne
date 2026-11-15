/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.fml;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.event.IModBusEvent;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class VyneModContainer extends ModContainer {
    private final IEventBus eventBus;
    private final List<String> modClasses;
    private final Set<Object> instances = Collections.newSetFromMap(new IdentityHashMap<>());

    public VyneModContainer(VyneModInfo info, List<String> modClasses) {
        super(info);
        this.modClasses = modClasses;
        this.eventBus = BusBuilder.builder().markerType(IModBusEvent.class).allowPerPhasePost().build();
    }

    @Override
    public IEventBus getEventBus() {
        return eventBus;
    }

    public boolean owns(Object instance) {
        return instances.contains(instance);
    }

    public List<String> modClasses() {
        return modClasses;
    }

    public void construct(Dist dist, ClassLoader loader) throws ReflectiveOperationException {
        ModLoadingContext context = ModLoadingContext.get();
        ModContainer previous = context.getActiveContainer();
        context.setActiveContainer(this);
        try {
            for (String name : modClasses) {
                Class<?> type = Class.forName(name, true, loader);
                instances.add(instantiate(type, dist));
            }
        } finally {
            context.setActiveContainer(previous);
        }
    }

    private Object instantiate(Class<?> type, Dist dist) throws ReflectiveOperationException {
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        List<Constructor<?>> ordered = new ArrayList<>(List.of(constructors));
        ordered.sort((a, b) -> Integer.compare(b.getParameterCount(), a.getParameterCount()));
        for (Constructor<?> constructor : ordered) {
            Object[] args = new Object[constructor.getParameterCount()];
            boolean usable = true;
            Class<?>[] params = constructor.getParameterTypes();
            for (int i = 0; i < params.length && usable; i++) {
                if (params[i] == IEventBus.class) args[i] = eventBus;
                else if (params[i].isInstance(this)) args[i] = this;
                else if (params[i] == Dist.class) args[i] = dist;
                else usable = false;
            }
            if (!usable) continue;
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        }
        throw new NoSuchMethodException(type.getName() + " has no constructor taking only IEventBus, ModContainer and Dist");
    }
}
