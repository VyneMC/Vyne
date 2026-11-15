/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.client;

import dev.stolym.vyne.util.VyneFabricCrashFormat;
import dev.stolym.vyne.util.VyneLogger;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class VyneDebugCrashHandler {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private VyneDebugCrashHandler() {}

    public static boolean isInterceptable(Throwable t) {
        if (t == null) {
            return false;
        }
        for (Throwable c = t; c != null; c = c.getCause()) {
            String m = c.getMessage();
            if (m != null && m.contains("Manually triggered debug crash")) {
                return true;
            }
            String s = c.toString();
            if (s.contains("Manually triggered debug crash")) {
                return true;
            }
        }
        return false;
    }

    public static void handle(Object client, Object keyboard, Throwable crash) {
        try {
            writeIntentionalCrashLog(crash);
        } catch (Throwable ignored) {
        }
        resetKeyboardDebugCrashFields(keyboard);
        try {
            openConfirmScreen(client, crash);
        } catch (Throwable t) {
            VyneLogger.warn("[Vyne] F3+C intercept: could not open confirm screen — " + t.getMessage());
            VyneLogger.warn("[Vyne] " + userFacingMessage());
        }
    }

    private static void openConfirmScreen(Object client, Throwable originalCrash) throws Exception {
        Class<?> textClass = Class.forName("net.minecraft.class_2561");
        Method literal = null;
        for (Method m : textClass.getMethods()) {
            if (("method_43470".equals(m.getName()) || "literal".equals(m.getName()))
                && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                literal = m;
                break;
            }
        }
        if (literal == null) {
            return;
        }
        Object title = literal.invoke(null, "Vyne — intentional crash (F3+C)");
        Object message = literal.invoke(null,
            "Vyne intercepted an intentional crash. Do you actually want to kill the game, "
                + "or just generate a log file and keep playing?");
        Object yesLabel = literal.invoke(null, "Crash game");
        Object noLabel = literal.invoke(null, "Log only — keep playing");

        final Object clientRef = client;
        BooleanConsumer callback = v -> {
            try {
                if (v) {
                    sneakyThrow(originalCrash);
                } else {
                    Method setScreen = findSetScreen(clientRef);
                    if (setScreen != null) {
                        setScreen.invoke(clientRef, new Object[] {null});
                    }
                    VyneLogger.info("[Vyne] F3+C: user chose log-only; see vyne/intentional-crash/");
                }
            } catch (Throwable ex) {
                VyneLogger.error("[Vyne] F3+C follow-up failed", ex);
            }
        };

        Class<?> confirmClass = Class.forName("net.minecraft.class_410");
        Constructor<?> ctor = confirmClass.getConstructor(
            BooleanConsumer.class,
            textClass,
            textClass,
            textClass,
            textClass);
        Object screen = ctor.newInstance(callback, title, message, yesLabel, noLabel);

        Method setScreen = findSetScreen(client);
        if (setScreen != null) {
            setScreen.invoke(client, screen);
        }
    }

    private static Method findSetScreen(Object client) {
        for (Method m : client.getClass().getMethods()) {
            if (m.getParameterCount() == 1 && "method_1507".equals(m.getName())) {
                return m;
            }
        }
        for (Method m : client.getClass().getMethods()) {
            if (m.getParameterCount() == 1 && "setScreen".equals(m.getName())) {
                return m;
            }
        }
        return null;
    }

    private static void resetKeyboardDebugCrashFields(Object keyboard) {
        if (keyboard == null) {
            return;
        }
        for (String f : new String[] {"field_1682", "field_1681", "field_1680"}) {
            try {
                java.lang.reflect.Field field = keyboard.getClass().getDeclaredField(f);
                field.setAccessible(true);
                if (field.getType() == long.class) {
                    field.setLong(keyboard, 0L);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void writeIntentionalCrashLog(Throwable crash) throws Exception {
        Path dir = intentionalCrashDir();
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(TS);
        Path out = dir.resolve("f3c-intentional-" + stamp + ".log");
        StringWriter sw = new StringWriter();
        sw.append("Vyne F3+C (manual debug crash) — intercepted before game exit.\n");
        sw.append("Time: ").append(LocalDateTime.now().toString()).append('\n');
        sw.append('\n');
        sw.append(VyneFabricCrashFormat.formatDeveloperSummary(crash)).append("\n\n--- full trace ---\n");
        crash.printStackTrace(new PrintWriter(sw));
        Files.writeString(out, sw.toString(), StandardCharsets.UTF_8);
        VyneLogger.info("[Vyne] F3+C intentional crash log: " + out.toAbsolutePath());
    }

    private static Path intentionalCrashDir() {
        String gd = System.getProperty("vyne.gameDir");
        if (gd != null && !gd.isEmpty()) {
            return Paths.get(gd, "vyne", "intentional-crash");
        }
        return Paths.get("vyne", "intentional-crash").toAbsolutePath().normalize();
    }

    static String userFacingMessage() {
        return "Vyne intercepted an intentional crash (F3+C). Check the log under vyne/intentional-crash/ "
            + "or reopen the confirm dialog if it failed to show.";
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable t) throws E {
        throw (E) t;
    }
}
