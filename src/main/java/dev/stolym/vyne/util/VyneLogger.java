/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
public final class VyneLogger {
    private static final Logger LOG = LoggerFactory.getLogger("Vyne");

    private static final boolean NO_COLOR = true;

    private static final String RESET   = ansi("\u001B[0m");
    private static final String BOLD    = ansi("\u001B[1m");
    private static final String DIM     = ansi("\u001B[2m");
    private static final String CYAN    = ansi("\u001B[36m");
    private static final String MAGENTA = ansi("\u001B[35m");
    private static final String YELLOW  = ansi("\u001B[33m");
    private static final String RED     = ansi("\u001B[31m");
    private static final String GREEN   = ansi("\u001B[32m");
    private static final String BLUE    = ansi("\u001B[34m");

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int DIVIDER_WIDTH = 56;

    private static volatile String currentPhase = "INIT";
    private static final Map<String, Long> phaseTimestamps = new HashMap<>();

    private VyneLogger() {}

    public static void setPhase(String phase) {
        currentPhase = phase;
        phaseTimestamps.put(phase, System.currentTimeMillis());
    }

    public static void banner(String version) {
        LOG.info("");
        LOG.info(BOLD + CYAN + "  Vyne " + version + RESET + DIM + " ·  Stolym Studios" + RESET);
        LOG.info("");
    }

    public static void phase(String phase) {
        currentPhase = phase;
        phaseTimestamps.put(phase, System.currentTimeMillis());
        LOG.info("");
        LOG.info(BOLD + MAGENTA + "┌─ " + phase.toUpperCase() + " " + DIM + repeat('─', Math.max(0, DIVIDER_WIDTH - phase.length() - 3)) + RESET);
    }

    public static void endPhase() {
        long elapsed = elapsedMs(currentPhase);
        String label = elapsed >= 0 ? DIM + " (" + elapsed + " ms)" + RESET : "";
        LOG.info(BOLD + MAGENTA + "└─ " + currentPhase.toUpperCase() + label + RESET);
        LOG.info("");
    }

    public static long elapsedMs(String phase) {
        Long start = phaseTimestamps.get(phase);
        return start != null ? System.currentTimeMillis() - start : -1L;
    }

    private static void mirrorStdout(String message) {
        System.out.println("[Vyne] " + message);
    }

    private static void mirrorStderr(String message) {
        System.err.println("[Vyne] " + message);
    }

    public static void info(String message) {
        if (message.contains("TRACE StatusLogger")) return;
        LOG.info(prefixed(message));
        mirrorStdout(prefixed(message));
    }

    public static void warn(String message) {
        LOG.warn(YELLOW + prefixed(message) + RESET);
        mirrorStderr("WARN " + prefixed(message));
    }

    public static void warn(String message, Throwable t) {
        LOG.warn(YELLOW + prefixed(message) + RESET, t);
        mirrorStderr("WARN " + prefixed(message) + " — " + t);
    }

    public static void error(String message) {
        LOG.error(RED + prefixed(message) + RESET);
        mirrorStderr("ERROR " + prefixed(message));
    }

    public static void error(String message, Throwable t) {
        LOG.error(RED + prefixed("✗ " + message) + RESET);
        LOG.error(RED + "  ┌─ Vyne Error Report " + repeat('─', 36) + RESET);
        LOG.error(RED + "  │  Type   : " + t.getClass().getName() + RESET);
        LOG.error(RED + "  │  Cause  : " + prettify(t.getMessage()) + RESET);
        Throwable cause = t.getCause();
        if (cause != null) {
            LOG.error(RED + "  │  Root   : " + cause.getClass().getName() + ": " + prettify(cause.getMessage()) + RESET);
        }
        LOG.error(RED + "  └" + repeat('─', 56) + RESET);
        LOG.error(RED + "  ↓ Full stacktrace below ↓" + RESET, t);
        mirrorStderr("ERROR " + prefixed(message) + " — " + prettify(t.toString()));
        t.printStackTrace(System.err);
    }

    public static void debug(String message) {
        LOG.debug(prefixed(message));
    }

    public static void success(String message) {
        LOG.info("  " + GREEN + "✓ " + RESET + message);
    }
    public static void failure(String message) {
        LOG.error("  " + RED + "✗ " + RESET + message);
    }

    public static void warning(String message) {
        LOG.warn("  " + YELLOW + "! " + RESET + message);
    }

    public static void init(String message) {
        LOG.info("  " + CYAN + "[INIT]" + RESET + " " + message);
    }

    public static void step(int number, String message) {
        LOG.info("  " + BOLD + CYAN + "[" + number + "]" + RESET + " " + message);
    }

    public static void loading(String message) {
        LOG.info("  " + DIM + "⟳" + RESET + " " + message);
    }

    public static void detail(String message) {
        LOG.info("  " + DIM + "»" + RESET + " " + message);
    }

    public static void detail(String indicator, String message) {
        LOG.info("  " + DIM + indicator + RESET + " " + message);
    }

    public static void detect(String modId, String modType) {
        LOG.info("  " + CYAN + "[" + modType + "]" + RESET + " " + DIM + "→" + RESET + " " + BOLD + modId + RESET);
    }

    public static void discovered(int count) {
        LOG.info("  " + GREEN + "✓" + RESET + " Found " + BOLD + count + RESET + " mod candidate" + (count == 1 ? "" : "s"));
    }

    public static void summary(int fabric, int neoforge, int forge, int vyne) {
        int total = fabric + neoforge + forge + vyne;
        LOG.info("");
        LOG.info(BOLD + "  Loaded " + GREEN + total + RESET + BOLD + " mod" + (total == 1 ? "" : "s") + RESET);
        logSummaryRow("Fabric",   fabric,   BLUE);
        logSummaryRow("NeoForge", neoforge, MAGENTA);
        logSummaryRow("Forge",    forge,    YELLOW);
        logSummaryRow("Vyne",     vyne,     CYAN);
        LOG.info("");
    }

    public static void divider() {
        LOG.info(DIM + "  " + repeat('─', DIVIDER_WIDTH) + RESET);
    }

    public static void blank() {
        LOG.info("");
    }

    private static void logSummaryRow(String label, int count, String color) {
        if (count > 0) {
            LOG.info("  " + DIM + "•" + RESET + " " + color + label + RESET + ": " + count);
        }
    }

    private static String prefixed(String msg) {
        return currentPhase + " » " + prettify(msg);
    }

    private static String prettify(String msg) {
        if (msg == null) return null;
        String result = msg.replaceAll("(?m)^\\s+[0-9a-f]{7,8}:\\s+[0-9a-f\\s]+$", "");
        result = result.replaceAll("(?m)^\\s+(full|same|append|chop|same_locals_1_stack_item)_frame\\(@.*$", "");
        result = result.replaceAll("(?m)^\\s+.*@[0-9]+: [a-z]+.*$", "");
        result = result.replaceAll("(?m)^\\s+Type '.*' is not assignable to '.*'$", "");
        result = result.replaceAll("(?m)^\\s+Current Frame.*$", "");
        result = result.replaceAll("(?m)^\\s+stack\\[[0-9]+\\].*$", "");

        result = result.replaceAll("(?m)^Exception Details:.*$", "");
        result = result.replaceAll("(?m)^  Location:.*$", "");
        result = result.replaceAll("(?m)^  Reason:.*$", "");
        result = result.replaceAll("(?m)^  Bytecode:.*$", "");
        result = result.replaceAll("(?m)^  Stackmap Table:.*$", "");

        result = result.replaceAll("(?m)^\\s*$\\n", "");
        result = result.trim();

        result = result.replace("net/minecraft/class_1657", "PlayerEntity");
        result = result.replace("net/minecraft/class_1937", "Level");
        result = result.replace("net/minecraft/class_2338", "BlockPos");
        result = result.replace("net/minecraft/class_2680", "BlockState");
        result = result.replace("net/minecraft/class_1297", "Entity");
        result = result.replace("net/minecraft/class_1799", "ItemStack");
        result = result.replace("net/minecraft/class_2586", "BlockEntity");
        result = result.replace("net/minecraft/class_2350", "Direction");
        result = result.replace("net/minecraft/class_2248", "Block");
        result = result.replace("net/minecraft/class_1792", "Item");
        result = result.replace("net/minecraft/class_3222", "ServerPlayer");
        result = result.replace("net/minecraft/class_1299", "EntityType");
        result = result.replace("net/minecraft/class_2378", "Registry");
        result = result.replace("net/minecraft/class_2960", "ResourceLocation");
        result = result.replace("net/minecraft/class_2561", "Component");
        return result;
    }

    private static String ts() {
        return LocalTime.now().format(TIME_FMT);
    }

    private static String ansi(String code) {
        return NO_COLOR ? "" : code;
    }

    private static String repeat(char ch, int times) {
        return String.valueOf(ch).repeat(Math.max(0, times));
    }
}
