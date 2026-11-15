/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.fml;

import dev.stolym.vyne.core.GameLoader;
import dev.stolym.vyne.ui.VyneEarlyWindow;
import dev.stolym.vyne.ui.VyneLoadingScreen;
import dev.stolym.vyne.util.VyneLogger;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class VyneImmediateWindowHandler {
    private static final String FALLBACK = "net.neoforged.neoforge.client.loading.NoVizFallback";
    private static volatile long window;

    private VyneImmediateWindowHandler() {}

    public static void load(String launchTarget, String[] arguments) {
    }

    public static long setupMinecraftWindow(IntSupplier width, IntSupplier height, Supplier<String> title,
                                            LongSupplier monitor) {
        long handle = VyneEarlyWindow.handOff(width.getAsInt(), height.getAsInt(), title.get(), monitor.getAsLong(), 0L);
        window = handle;
        return handle;
    }

    public static boolean positionWindow(Optional<Object> monitor, IntConsumer widthSetter, IntConsumer heightSetter,
                                         IntConsumer xSetter, IntConsumer ySetter) {
        return false;
    }

    public static void updateFBSize(IntConsumer width, IntConsumer height) {
    }

    @SuppressWarnings("unchecked")
    public static <T> Supplier<T> loadingOverlay(Supplier<?> minecraft, Supplier<?> reload,
                                                 Consumer<Optional<Throwable>> onError, boolean fadeIn) {
        try {
            ClassLoader loader = GameLoader.get() != null ? GameLoader.get() : VyneImmediateWindowHandler.class.getClassLoader();
            Class<?> fallback = Class.forName(FALLBACK, true, loader);
            return (Supplier<T>) fallback.getMethod("loadingOverlay", Supplier.class, Supplier.class, Consumer.class,
                    boolean.class).invoke(null, minecraft, reload, onError, fadeIn);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("NeoForge's loading overlay is unavailable", e);
        }
    }

    public static void acceptGameLayer(ModuleLayer layer) {
    }

    public static void renderTick() {
    }

    public static String getGLVersion() {
        long handle = window;
        if (handle == 0L) return "3.2";
        return GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_CONTEXT_VERSION_MAJOR) + "."
                + GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_CONTEXT_VERSION_MINOR);
    }

    public static void updateProgress(String message) {
        if (message != null && !message.isBlank()) VyneLoadingScreen.activity(message);
    }

    public static void crash(String message) {
        VyneLogger.error("[VyneFML] " + message);
    }
}
