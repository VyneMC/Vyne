/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.fml;

import dev.stolym.vyne.core.GameLoader;
import dev.stolym.vyne.ui.VyneLoadingScreen;
import dev.stolym.vyne.util.VyneLogger;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.DeferredWorkQueue;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.fml.event.lifecycle.ParallelDispatchEvent;
import net.neoforged.fml.loading.modscan.ModAnnotation;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public final class VyneModLoader {
    private static final Type SUBSCRIBER = Type.getType("Lnet/neoforged/fml/common/EventBusSubscriber;");
    private static final List<ModLoadingIssue> ISSUES = new CopyOnWriteArrayList<>();

    private VyneModLoader() {}

    public static void gatherAndInitializeMods(Executor syncExecutor, Executor parallelExecutor, Runnable periodicTask) {
        VyneMods.discover();
        dev.stolym.vyne.patchlayer.PatchLayerEvents.register();
        Dist dist = VyneFMLLoader.getDist();
        ClassLoader loader = GameLoader.get() != null ? GameLoader.get() : VyneModLoader.class.getClassLoader();
        List<VyneModContainer> containers = VyneMods.containers();
        VyneLoadingScreen.update("Loading mods", 70);
        int index = 0;
        for (VyneModContainer container : containers) {
            index++;
            String modId = container.getModId();
            VyneLoadingScreen.task(modId, index, containers.size());
            VyneLoadingScreen.modState(modId, VyneLoadingScreen.MOD_WORKING);
            try {
                container.construct(dist, loader);
                int subscribers = registerSubscribers(container, dist, loader);
                VyneLogger.info("[VyneFML] Constructed '" + modId + "': " + container.modClasses().size()
                        + " @Mod class(es), " + subscribers + " event listener(s)");
                VyneLoadingScreen.modState(modId, VyneLoadingScreen.MOD_DONE);
                VyneLoadingScreen.activity("Loaded " + modId);
            } catch (Throwable t) {
                Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
                VyneLogger.error("[VyneFML] Mod '" + modId + "' failed to construct: " + cause, cause);
                addLoadingIssue(new ModLoadingIssue(ModLoadingIssue.Severity.ERROR, "fml.modloadingissue.failedtoloadmod",
                        List.of(modId)).withCause(cause).withAffectedMod(container.getModInfo()));
                dev.stolym.vyne.core.ModActivity.FAILED.add(modId);
            }
        }
        dispatchParallelEvent("construct", syncExecutor, parallelExecutor, periodicTask, FMLConstructModEvent::new);
        List<ModLoadingIssue> errors = ISSUES.stream()
                .filter(issue -> issue.severity() == ModLoadingIssue.Severity.ERROR)
                .toList();
        if (!errors.isEmpty()) throw new net.neoforged.fml.ModLoadingException(errors);
    }

    private static int registerSubscribers(VyneModContainer container, Dist dist, ClassLoader loader) {
        int registered = 0;
        VyneModFileInfo file = (VyneModFileInfo) container.getModInfo().getOwningFile();
        ModFileScanData scan = file.getFile().getScanResult();
        String onlyMod = file.getMods().size() == 1 ? file.getMods().get(0).getModId() : null;
        IEventBus gameBus = null;
        for (ModFileScanData.AnnotationData data : scan.getAnnotations()) {
            if (!SUBSCRIBER.equals(data.annotationType())) continue;
            Object modid = data.annotationData().get("modid");
            String target = modid instanceof String s && !s.isEmpty() ? s : onlyMod;
            if (!container.getModId().equals(target)) continue;
            if (!VyneMods.matchesDist(data.annotationData().get("value"), dist)) continue;
            Object bus = data.annotationData().get("bus");
            String explicitBus = bus instanceof ModAnnotation.EnumHolder holder ? holder.value() : null;
            try {
                Class<?> type = Class.forName(data.clazz().getClassName(), true, loader);
                if (gameBus == null) gameBus = VyneFMLLoader.getBindings().getGameBus();
                registered += subscribeStatics(type, container.getEventBus(), gameBus, explicitBus);
            } catch (Throwable t) {
                VyneLogger.error("[VyneFML] Could not register event subscriber " + data.clazz().getClassName()
                        + " for '" + container.getModId() + "': " + t, t);
            }
        }
        return registered;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int subscribeStatics(Class<?> type, IEventBus modBus, IEventBus gameBus, String explicitBus) {
        int count = 0;
        for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
            net.neoforged.bus.api.SubscribeEvent subscribe = method.getAnnotation(net.neoforged.bus.api.SubscribeEvent.class);
            if (subscribe == null || !java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
            if (method.getParameterCount() != 1 || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                throw new IllegalArgumentException("Method " + method + " has @SubscribeEvent but does not take exactly one Event");
            }
            Class eventType = method.getParameterTypes()[0];
            boolean toModBus = explicitBus != null ? explicitBus.equals("MOD") : IModBusEvent.class.isAssignableFrom(eventType);
            method.setAccessible(true);
            java.util.function.Consumer listener = event -> {
                try {
                    method.invoke(null, event);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException runtime) throw runtime;
                    if (cause instanceof Error error) throw error;
                    throw new RuntimeException(cause);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            };
            (toModBus ? modBus : gameBus).addListener(subscribe.priority(), subscribe.receiveCanceled(), eventType, listener);
            count++;
        }
        return count;
    }

    public static void runInitTask(String name, Executor syncExecutor, Runnable periodicTask, Runnable task) {
        task.run();
    }

    public static void dispatchParallelEvent(String name, Executor syncExecutor, Executor parallelExecutor, Runnable periodicTask,
                                             BiFunction<ModContainer, DeferredWorkQueue, ParallelDispatchEvent> eventGenerator) {
        DeferredWorkQueue queue = new DeferredWorkQueue(name);
        for (ModContainer container : VyneModList.get().getSortedMods()) {
            if (dev.stolym.vyne.core.ModActivity.isInactive(container.getModId())) continue;
            withActive(container, () -> container.acceptEvent(eventGenerator.apply(container, queue)));
        }
        queue.runTasks();
    }

    public static void waitForTask(String name, Runnable periodicTask, CompletableFuture<?> future) {
        future.join();
    }

    public static void dispatchParallelTask(String name, Executor parallelExecutor, Runnable periodicTask, Consumer<ModContainer> task) {
        for (ModContainer container : VyneModList.get().getSortedMods()) withActive(container, () -> task.accept(container));
    }

    public static <T extends Event & IModBusEvent> void runEventGenerator(Function<ModContainer, T> generator) {
        for (ModContainer container : VyneModList.get().getSortedMods()) {
            withActive(container, () -> container.acceptEvent(generator.apply(container)));
        }
    }

    public static <T extends Event & IModBusEvent> void postEvent(T event) {
        for (EventPriority priority : EventPriority.values()) {
            for (ModContainer container : VyneModList.get().getSortedMods()) container.acceptEvent(priority, event);
        }
    }

    public static <T extends Event & IModBusEvent> T postEventWithReturn(T event) {
        postEvent(event);
        return event;
    }

    public static <T extends Event & IModBusEvent> void postEventWrapContainerInModOrder(T event) {
        postEventWithWrapInModOrder(event, (container, e) -> ModLoadingContext.get().setActiveContainer(container),
                (container, e) -> ModLoadingContext.get().setActiveContainer(null));
    }

    public static <T extends Event & IModBusEvent> void postEventWithWrapInModOrder(T event, BiConsumer<ModContainer, T> pre,
                                                                                  BiConsumer<ModContainer, T> post) {
        for (EventPriority priority : EventPriority.values()) {
            for (ModContainer container : VyneModList.get().getSortedMods()) {
                pre.accept(container, event);
                try {
                    container.acceptEvent(priority, event);
                } finally {
                    post.accept(container, event);
                }
            }
        }
    }

    public static boolean hasErrors() {
        for (ModLoadingIssue issue : ISSUES) if (issue.severity() == ModLoadingIssue.Severity.ERROR) return true;
        return false;
    }

    public static List<ModLoadingIssue> getLoadingErrors() {
        return filter(ModLoadingIssue.Severity.ERROR);
    }

    public static List<ModLoadingIssue> getLoadingWarnings() {
        return filter(ModLoadingIssue.Severity.WARNING);
    }

    public static List<ModLoadingIssue> getLoadingIssues() {
        return List.copyOf(ISSUES);
    }

    public static void clearLoadingIssues() {
        ISSUES.clear();
    }

    public static void addLoadingIssue(ModLoadingIssue issue) {
        ISSUES.add(issue);
    }

    private static List<ModLoadingIssue> filter(ModLoadingIssue.Severity severity) {
        List<ModLoadingIssue> out = new ArrayList<>();
        for (ModLoadingIssue issue : ISSUES) if (issue.severity() == severity) out.add(issue);
        return out;
    }

    private static void withActive(ModContainer container, Runnable action) {
        ModLoadingContext context = ModLoadingContext.get();
        ModContainer previous = context.getActiveContainer();
        context.setActiveContainer(container);
        try {
            action.run();
        } catch (Throwable t) {
            VyneLogger.error("[VyneFML] Event failed for '" + container.getModId() + "': " + t, t);
            addLoadingIssue(new ModLoadingIssue(ModLoadingIssue.Severity.ERROR, "fml.modloadingissue.dispatchfailed",
                    List.of(container.getModId())).withCause(t).withAffectedMod(container.getModInfo()));
        } finally {
            context.setActiveContainer(previous);
        }
    }
}
