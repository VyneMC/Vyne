/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.ui;

import dev.stolym.vyne.util.VyneLogger;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

public final class VyneEarlyWindow {
    private static volatile long handle;
    private static volatile boolean running;
    private static volatile Thread renderThread;
    private static volatile Thread mainThread;
    private static volatile boolean renderFailed;

    private VyneEarlyWindow() {}

    private static volatile VyneEarlyScreen screen;

    public static boolean isEnabled() {
        String flag = System.getProperty("vyne.earlyWindow");
        if (flag != null) return Boolean.parseBoolean(flag);
        return VyneLoadingTheme.current().earlyWindow;
    }

    public static boolean isActive() {
        return handle != 0L && running;
    }

    public static void open(String title, int width, int height, String version) {
        if (handle != 0L || !isEnabled()) return;
        try {
            System.setProperty("java.awt.headless", "true");
            if (!GLFW.glfwInit()) return;
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_CREATION_API, GLFW.GLFW_NATIVE_CONTEXT_API);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
            long window = GLFW.glfwCreateWindow(Math.max(320, width), Math.max(240, height), title, 0L, 0L);
            if (window == 0L) return;
            handle = window;
            mainThread = Thread.currentThread();
            running = true;
            VyneEarlyScreen painter = new VyneEarlyScreen(version);
            screen = painter;
            Thread thread = new Thread(() -> renderLoop(window, painter), "vyne-early-window");
            thread.setDaemon(true);
            renderThread = thread;
            thread.start();
        } catch (Throwable t) {
            running = false;
            handle = 0L;
            VyneLogger.warn("[EarlyWindow] Not available, Minecraft will open its own window: " + rootCause(t));
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause;
    }

    public static void pump() {
        if (!running || handle == 0L || Thread.currentThread() != mainThread) return;
        try {
            GLFW.glfwPollEvents();
        } catch (Throwable ignored) {
        }
    }

    public static long handOff(int width, int height, CharSequence title, long monitor, long share) {
        long window = handle;
        if (window == 0L) {
            return GLFW.glfwCreateWindow(width, height, title, monitor, share);
        }
        running = false;
        Thread thread = renderThread;
        boolean released = true;
        if (thread != null) {
            try {
                thread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            released = !thread.isAlive();
        }
        handle = 0L;
        renderThread = null;
        VyneEarlyScreen painted = screen;
        if (painted != null && released && !renderFailed) {
            float[] rect = painted.logoFraction();
            if (rect != null) VyneLoadingScreen.handoffLogo(rect[0], rect[1], rect[2], rect[3]);
        }
        if (!released || renderFailed || share != 0L) {
            if (released) GLFW.glfwDestroyWindow(window);
            return GLFW.glfwCreateWindow(width, height, title, monitor, share);
        }
        try {
            GLFW.glfwSetWindowTitle(window, title);
            if (monitor != 0L) {
                GLFW.glfwSetWindowMonitor(window, monitor, 0, 0, width, height, GLFW.GLFW_DONT_CARE);
            } else {
                int[] w = new int[1];
                int[] h = new int[1];
                GLFW.glfwGetWindowSize(window, w, h);
                if (w[0] != width || h[0] != height) GLFW.glfwSetWindowSize(window, width, height);
            }
        } catch (Throwable t) {
            VyneLogger.debug("[EarlyWindow] Hand-off adjustment failed: " + t);
        }
        VyneLogger.info("[EarlyWindow] Window handed to Minecraft.");
        return window;
    }

    private static void renderLoop(long window, VyneEarlyScreen painter) {
        int texture = 0;
        int framebuffer = 0;
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            texture = GL11.glGenTextures();
            framebuffer = GL30.glGenFramebuffers();
            int[] pixels = new int[0];
            int logicalWidth = 0;
            int logicalHeight = 0;
            int[] w = new int[1];
            int[] h = new int[1];
            while (running) {
                GLFW.glfwGetFramebufferSize(window, w, h);
                int width = w[0];
                int height = h[0];
                if (width > 0 && height > 0) {
                    int scale = VyneEarlyScreen.scaleFor(width, height);
                    int lw = (width + scale - 1) / scale;
                    int lh = (height + scale - 1) / scale;
                    if (lw != logicalWidth || lh != logicalHeight) {
                        logicalWidth = lw;
                        logicalHeight = lh;
                        pixels = new int[lw * lh];
                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, lw, lh, 0,
                                GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, (ByteBuffer) null);
                        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
                        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                                GL11.GL_TEXTURE_2D, texture, 0);
                    }
                    painter.paint(pixels, lw, lh, System.currentTimeMillis());
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                    GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, lw, lh,
                            GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
                    GL30.glBlitFramebuffer(0, 0, lw, lh, 0, height, lw * scale, height - lh * scale,
                            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
                    GLFW.glfwSwapBuffers(window);
                }
                try {
                    Thread.sleep(33);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } catch (Throwable t) {
            renderFailed = true;
            VyneLogger.debug("[EarlyWindow] Rendering stopped: " + t);
        } finally {
            try {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
                if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer);
                if (texture != 0) GL11.glDeleteTextures(texture);
            } catch (Throwable ignored) {
            }
            try {
                GL.setCapabilities(null);
                GLFW.glfwMakeContextCurrent(0L);
            } catch (Throwable ignored) {
            }
        }
    }
}
