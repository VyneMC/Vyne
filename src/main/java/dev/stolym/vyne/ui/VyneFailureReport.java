/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.ui;

import dev.stolym.vyne.containment.VyneFailureTriage;
import dev.stolym.vyne.containment.VyneModState;
import dev.stolym.vyne.util.VyneLogger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public final class VyneFailureReport {
    private VyneFailureReport() {}

    private static final AtomicInteger REPORT_COUNTER = new AtomicInteger(0);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int BOX_WIDTH = 54;

    public static void renderAndLog(String modId, VyneModState state,
                                    String technicalReason,
                                    VyneFailureTriage.FailureTier tier,
                                    Throwable cause) {
        String reportFile = writeDetailedLog(modId, technicalReason, tier, cause);
        String box = buildBox(modId, state, technicalReason, tier, cause, reportFile);
        System.err.println(box);
        VyneLogger.warn(box);
    }

    public static void renderWarn(String modId, String reason, Throwable cause) {
        String header = center("VYNE — NON-CRITICAL WARNING", BOX_WIDTH);
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(top());
        sb.append(row(header));
        sb.append(divider());
        sb.append(row(""));
        if (modId != null) sb.append(row(fit("MOD:    " + modId)));
        sb.append(row(fit("REASON: " + shortReason(cause, reason))));
        sb.append(row(""));
        sb.append(row(fit("Server continues normally.")));
        sb.append(row(""));
        sb.append(bottom());
        System.err.println(sb);
        VyneLogger.warn(sb.toString());
    }

    private static String buildBox(String modId, VyneModState state, String technicalReason,
                                   VyneFailureTriage.FailureTier tier, Throwable cause,
                                   String reportFile) {
        String tierLabel = switch (tier) {
            case TIER_1_ISOLATE       -> "1 — ISOLATED (server continues)";
            case TIER_2_WARN          -> "2 — WARNING  (non-critical)";
            case TIER_3_CLEAN_SHUTDOWN -> "3 — CLEAN SHUTDOWN";
            case TIER_4_EMERGENCY_HALT -> "4 — EMERGENCY HALT";
        };

        String stateLabel = switch (state) {
            case FROZEN      -> "Mod frozen. Other mods keep running.";
            case HARD_STOPPED -> "Shutdown initiated.";
            case RUNNING     -> "Running (unexpected state in report)";
        };

        String[] actionLines = buildActionLines(cause, modId);
        String friendlyReason = buildFriendlyReason(cause, modId);

        StringBuilder sb = new StringBuilder("\n");
        sb.append(top());
        sb.append(row(center("VYNE — MOD FAILURE", BOX_WIDTH)));
        sb.append(divider());
        sb.append(row(""));
        if (modId != null) sb.append(row(fit("MOD:    " + modId)));
        sb.append(row(fit("TIER:   " + tierLabel)));
        sb.append(row(fit("STATUS: " + stateLabel)));
        sb.append(row(""));
        sb.append(row(fit("REASON:")));
        for (String line : wordWrap(friendlyReason, BOX_WIDTH - 4)) {
            sb.append(row("  " + line));
        }
        sb.append(row(""));
        sb.append(row(fit("WHAT TO DO:")));
        for (String action : actionLines) {
            for (String line : wordWrap(action, BOX_WIDTH - 4)) {
                sb.append(row("  " + line));
            }
        }
        sb.append(row(""));
        if (reportFile != null) {
            sb.append(row(fit("Full details: " + reportFile)));
        }
        sb.append(row(""));
        sb.append(bottom());
        return sb.toString();
    }

    private static String buildFriendlyReason(Throwable cause, String modId) {
        if (cause == null) return "Unknown failure.";

        if (cause instanceof VyneFailureTriage.MissingDependencyException e) {
            return "This mod requires '" + e.missingModId + "' (version " + e.requiredVersion
                    + ") to be installed.";
        }
        if (cause instanceof VyneFailureTriage.VersionMismatchException e) {
            return "'" + e.depModId + "' is installed (version " + e.foundVersion
                    + ") but this mod needs " + e.requiredRange + ".";
        }
        if (cause instanceof VyneFailureTriage.DuplicateRegistryKeyException e) {
            return "Two mods are trying to register the same game object: '" + e.key
                    + "' in " + e.registryPath + ". They conflict.";
        }

        String msg = cause.getMessage() != null ? cause.getMessage() : "";

        if (cause instanceof NullPointerException) {
            return "The mod tried to use something that doesn't exist yet. "
                    + "This is usually a mod bug or a load order problem.";
        }
        if (cause instanceof ClassNotFoundException) {
            return "The mod is looking for a class that isn't available at runtime. "
                    + "A required library or dependency may be missing.";
        }
        if (cause instanceof NoClassDefFoundError) {
            return "A class the mod depends on was found during compile but not at runtime. "
                    + "A library JAR may be missing from your mods folder.";
        }
        if (cause instanceof NoSuchMethodError) {
            return "The mod is calling a method that doesn't exist in this version of Minecraft "
                    + "or one of its dependencies. This is usually a version mismatch.";
        }
        if (cause instanceof NoSuchFieldError) {
            return "The mod is accessing a field that doesn't exist in this version. "
                    + "Usually caused by a version mismatch or incompatible dependency.";
        }
        if (cause instanceof ClassCastException) {
            return "The mod tried to treat one type of object as another incompatible type. "
                    + "This can happen with incompatible dependency versions or cross-loader issues.";
        }
        if (cause instanceof StackOverflowError) {
            return "The mod caused an infinite loop in its own code. This is a mod bug.";
        }
        if (cause instanceof OutOfMemoryError) {
            return "The server ran out of memory. Either allocate more RAM or reduce the number "
                    + "of loaded mods. Check your JVM -Xmx setting.";
        }
        if (cause instanceof IllegalStateException) {
            if (msg.contains("Unknown registry key")) {
                return "The mod references a game object (block/item/entity) that hasn't been "
                        + "registered yet. This is a Forge→Fabric timing issue that Vyne "
                        + "attempted to recover from, but could not inject all missing entries.";
            }
            if (msg.contains("already frozen") || msg.contains("already registered")) {
                return "The mod tried to register a game object after Minecraft's registry was "
                        + "already locked. This is a mod bug — registrations must happen during "
                        + "the registration phase, not later.";
            }
            return "The mod tried to perform an operation that isn't valid in the current state. "
                    + "This is usually a mod bug or a load order conflict.";
        }
        if (cause instanceof IllegalArgumentException) {
            return "The mod passed an invalid argument to a Minecraft function. "
                    + "This is usually a mod bug.";
        }
        if (cause instanceof UnsupportedOperationException) {
            return "The mod called a function that isn't supported in this environment. "
                    + "This may be a Forge-only feature that Vyne's bridge doesn't fully implement yet.";
        }
        if (cause instanceof ExceptionInInitializerError) {
            return "The mod failed to initialise one of its classes when it was first loaded. "
                    + "This is often caused by a missing dependency or a mod bug in its static fields.";
        }
        if (cause instanceof LinkageError) {
            return "A classloader conflict was detected. Two versions of the same library may be "
                    + "present, or a mod compiled against a different Minecraft version is being loaded.";
        }
        if (cause instanceof VerifyError) {
            return "The mod's bytecode failed Java's verification check. This usually means "
                    + "the mod was compiled against a different Java version or is corrupted.";
        }
        if (cause instanceof SecurityException) {
            return "The mod tried to access a restricted system resource. "
                    + "This may be a security configuration issue.";
        }
        if (cause instanceof java.io.IOException) {
            return "The mod failed to read or write a file. Check that the server has "
                    + "correct file permissions in the world/mods directory.";
        }
        if (cause instanceof java.util.concurrent.TimeoutException) {
            return "The mod's initialisation took too long and timed out. "
                    + "The server may be under heavy load or the mod is stuck in a loop.";
        }
        if (msg.contains("mixin") || msg.contains("Mixin")) {
            return "A Mixin (bytecode patch) from this mod or a conflicting mod failed to apply. "
                    + "Two mods may be patching the same Minecraft code in incompatible ways.";
        }

        return "An unexpected error occurred in this mod: "
                + cause.getClass().getSimpleName()
                + (msg.isEmpty() ? "." : " — " + msg);
    }

    private static String[] buildActionLines(Throwable cause, String modId) {
        if (cause instanceof VyneFailureTriage.MissingDependencyException e) {
            return new String[]{
                "→ Install '" + e.missingModId + "' version " + e.requiredVersion + " or newer.",
                "→ OR remove '" + (modId != null ? modId : "this mod") + "' from your mods folder."
            };
        }
        if (cause instanceof VyneFailureTriage.VersionMismatchException e) {
            return new String[]{
                "→ Update '" + e.depModId + "' to meet the requirement: " + e.requiredRange,
                "→ OR update '" + (modId != null ? modId : "this mod") + "' to a version that "
                    + "supports " + e.depModId + " " + e.foundVersion + "."
            };
        }
        if (cause instanceof VyneFailureTriage.DuplicateRegistryKeyException) {
            return new String[]{
                "→ You likely have two mods that add the same block/item.",
                "→ Check if two versions of the same mod are in your mods folder.",
                "→ Report this to the mod authors if you only have one version of each."
            };
        }
        if (cause instanceof OutOfMemoryError) {
            return new String[]{
                "→ Increase your server's RAM: add -Xmx4G (or more) to your JVM arguments.",
                "→ Remove large mods you don't need.",
                "→ Check your hosting panel for a RAM increase option."
            };
        }
        if (cause instanceof NoSuchMethodError || cause instanceof NoSuchFieldError
                || cause instanceof ClassNotFoundException || cause instanceof NoClassDefFoundError) {
            return new String[]{
                "→ Check that all mods are designed for the same Minecraft version (" 
                    + getMinecraftVersion() + ").",
                "→ Update outdated mods to versions compatible with your Minecraft version.",
                "→ Remove any duplicate mod JARs from your mods folder."
            };
        }
        if (cause instanceof IllegalStateException ise
                && ise.getMessage() != null && ise.getMessage().contains("Unknown registry key")) {
            return new String[]{
                "→ Update the mod to its latest version — this is often fixed in newer releases.",
                "→ If the problem persists, report it to Vyne's issue tracker with your mod list.",
                "→ As a workaround, try removing this mod to allow the server to start."
            };
        }
        if (cause instanceof VerifyError || cause instanceof LinkageError) {
            return new String[]{
                "→ Check that you're running Java 21 or newer (required for Minecraft 1.21+).",
                "→ Remove duplicate JAR files from your mods folder.",
                "→ If only one version of the mod is installed, report it to the mod author."
            };
        }

        return new String[]{
            "→ Try updating the mod to its latest version.",
            "→ Check that all mods are compatible with Minecraft " + getMinecraftVersion() + ".",
            "→ If the issue persists, share the full report file with the mod author or Vyne's support."
        };
    }

    private static String writeDetailedLog(String modId, String reason,
                                           VyneFailureTriage.FailureTier tier, Throwable cause) {
        int num = REPORT_COUNTER.incrementAndGet();
        String fileName = String.format("vyne-report-%03d.log", num);
        Path reportDir = Paths.get(".vyne", "reports");
        Path reportPath = reportDir.resolve(fileName);
        try {
            Files.createDirectories(reportDir);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("=== Vyne Failure Report #" + num + " ===");
            pw.println("Timestamp : " + LocalDateTime.now().format(TS));
            pw.println("Mod       : " + (modId != null ? modId : "unknown"));
            pw.println("Tier      : " + tier);
            pw.println("Reason    : " + reason);
            pw.println("Phase     : " + dev.stolym.vyne.containment.VynePhaseTracker.current());
            pw.println();
            pw.println("--- Stack Trace ---");
            if (cause != null) {
                cause.printStackTrace(pw);
            } else {
                pw.println("(no throwable available)");
            }
            pw.println();
            pw.println("--- Active Mod States ---");
            pw.println(dev.stolym.vyne.containment.VyneModStateRegistry.getReport());
            pw.flush();
            Files.writeString(reportPath, sw.toString());
            return fileName;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String shortReason(Throwable t, String fallback) {
        if (t == null) return fallback;
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg != null ? ": " + msg : "");
    }

    private static String top() {
        return "╔" + "═".repeat(BOX_WIDTH) + "╗\n";
    }

    private static String bottom() {
        return "╚" + "═".repeat(BOX_WIDTH) + "╝\n";
    }

    private static String divider() {
        return "╠" + "═".repeat(BOX_WIDTH) + "╣\n";
    }

    private static String row(String content) {
        int pad = BOX_WIDTH - content.length();
        if (pad < 0) content = content.substring(0, BOX_WIDTH);
        return "║" + content + " ".repeat(Math.max(0, pad)) + "║\n";
    }

    private static String center(String text, int width) {
        int pad = Math.max(0, width - text.length());
        int left = pad / 2;
        int right = pad - left;
        return " ".repeat(left) + text + " ".repeat(right);
    }

    private static String fit(String text) {
        if (text.length() > BOX_WIDTH) return text.substring(0, BOX_WIDTH - 1) + "…";
        return text;
    }

    private static java.util.List<String> wordWrap(String text, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) { lines.add(""); return lines; }
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() + word.length() + 1 > maxWidth) {
                if (!current.isEmpty()) lines.add(current.toString().trim());
                current = new StringBuilder();
            }
            if (!current.isEmpty()) current.append(' ');
            current.append(word);
        }
        if (!current.isEmpty()) lines.add(current.toString().trim());
        return lines.isEmpty() ? java.util.List.of(text) : lines;
    }

    private static String getMinecraftVersion() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getModContainer("minecraft")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("1.21.x");
        } catch (Throwable t) {
            return "1.21.x";
        }
    }
}
