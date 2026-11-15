/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VyneFabricCrashFormat {
    private static final int MAX_FRAMES = 6;
    private static final int CLIPBOARD_TRACE_LINES = 48;
    private static final int CLIPBOARD_TRACE_LINES_DATAPACK = 10;

    private static final Pattern RE_FAILED_PARSE = Pattern.compile(
        "Failed to parse\\s+(\\S+)\\s+from\\s+pack\\s+(\\S+)");

    private static final Pattern RE_UNKNOWN_REGISTRY = Pattern.compile(
        "Unknown registry key in ResourceKey\\[([^\\]]+)\\]\\s*:\\s*(\\S+)");

    private static final Pattern RE_ERRORS_IN_ELEMENT = Pattern.compile(
        "(?m)(?:>>\\s*)?Errors in element\\s+(\\S+)\\s*:");

    private VyneFabricCrashFormat() {}

    public static String formatDeveloperSummary(Throwable t) {
        if (t == null) {
            return "(no exception)";
        }
        String datapack = tryCompactDatapackRegistryFailure(t);
        if (datapack != null) {
            return datapack;
        }
        return formatDeveloperSummaryVerbose(t);
    }

    private static String formatDeveloperSummaryVerbose(Throwable t) {
        Throwable leaf = unwrap(t);
        StringBuilder sb = new StringBuilder(512);
        sb.append("What: ").append(leaf.getClass().getSimpleName());
        String msg = leaf.getMessage();
        if (msg != null && !msg.isEmpty()) {
            sb.append("\nDetail: ").append(truncate(msg, 400));
        }
        String causeChain = causeChain(t, 3);
        if (!causeChain.isEmpty()) {
            sb.append("\nCause chain:\n").append(causeChain);
        }
        List<String> frames = relevantFrames(leaf, MAX_FRAMES);
        if (!frames.isEmpty()) {
            sb.append("\nLikely origin:\n");
            for (String f : frames) {
                sb.append("  • ").append(f).append('\n');
            }
        }
        String hint = hintFor(leaf);
        if (!hint.isEmpty()) {
            sb.append("Hint: ").append(hint);
        }
        return sb.toString().trim();
    }

    public static String formatClipboard(String mainText, Throwable t) {
        StringWriter out = new StringWriter();
        out.append(mainText);
        if (t == null) {
            return out.toString();
        }
        out.append(System.lineSeparator()).append(System.lineSeparator());
        out.append("── Vyne summary (not the full trace) ──").append(System.lineSeparator());
        String compactDpBlock = tryCompactDatapackRegistryFailure(t);
        out.append(compactDpBlock != null ? compactDpBlock : formatDeveloperSummaryVerbose(t));
        out.append(System.lineSeparator()).append(System.lineSeparator());
        boolean compactDp = compactDpBlock != null;
        int traceLines = compactDp ? CLIPBOARD_TRACE_LINES_DATAPACK : CLIPBOARD_TRACE_LINES;
        out.append("── Stack trace (truncated to ~").append(String.valueOf(traceLines))
            .append(" lines) ──").append(System.lineSeparator());
        if (compactDp) {
            out.append("(Datapack/registry errors above are usually enough; full trace is abbreviated.)")
                .append(System.lineSeparator());
        }
        out.append(truncatedStackTrace(t, traceLines));
        return out.toString();
    }

    private static String tryCompactDatapackRegistryFailure(Throwable t) {
        String blob = collectThrowableMessages(t);
        if (blob.isEmpty()) {
            return null;
        }

        String element = null;
        Matcher el = RE_ERRORS_IN_ELEMENT.matcher(blob);
        if (el.find()) {
            element = el.group(1);
        }

        String dataPath = null;
        String packId = null;
        Matcher fp = RE_FAILED_PARSE.matcher(blob);
        if (fp.find()) {
            dataPath = fp.group(1);
            packId = fp.group(2);
        }

        String registryCtx = null;
        String missingId = null;
        Matcher uk = RE_UNKNOWN_REGISTRY.matcher(blob);
        if (uk.find()) {
            registryCtx = uk.group(1).trim();
            missingId = uk.group(2);
        }

        if (dataPath == null && missingId == null && element == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder(384);
        sb.append("Datapack / registry failure (compact)\n");
        if (element != null) {
            sb.append("• Element: ").append(element).append('\n');
        }
        if (dataPath != null) {
            sb.append("• JSON: ").append(dataPath).append('\n');
        }
        if (packId != null) {
            sb.append("• Pack: ").append(packId).append('\n');
        }
        if (missingId != null) {
            sb.append("• Missing / invalid id: ").append(missingId).append('\n');
        }
        if (registryCtx != null) {
            sb.append("• Registry context: ").append(registryCtx).append('\n');
        }
        sb.append("Hint: The datapack references something not registered (wrong mod version, disabled mod, "
            + "or load order). Fix or remove the pack entry; full trace below is optional.");
        return sb.toString().trim();
    }

    private static String collectThrowableMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable c = t;
        int guard = 0;
        while (c != null && c != c.getCause() && guard++ < 16) {
            if (c.getMessage() != null) {
                sb.append(c.getMessage()).append('\n');
            }
            c = c.getCause();
        }
        return sb.toString();
    }

    private static Throwable unwrap(Throwable t) {
        Throwable cur = t;
        int guard = 0;
        while (cur.getCause() != null && cur.getCause() != cur && guard++ < 12) {
            cur = cur.getCause();
        }
        return cur;
    }

    private static String causeChain(Throwable t, int maxDepth) {
        List<String> lines = new ArrayList<>();
        Throwable c = t;
        int depth = 0;
        while (c != null && depth < maxDepth) {
            String m = c.getMessage();
            lines.add("  [" + depth + "] " + c.getClass().getSimpleName()
                + (m == null || m.isEmpty() ? "" : ": " + truncate(m, 180)));
            if (c.getCause() == null || c.getCause() == c) {
                break;
            }
            c = c.getCause();
            depth++;
        }
        return depth == 0 ? "" : String.join(System.lineSeparator(), lines);
    }

    private static List<String> relevantFrames(Throwable t, int max) {
        List<String> out = new ArrayList<>();
        if (t == null) {
            return out;
        }
        for (StackTraceElement ste : t.getStackTrace()) {
            if (out.size() >= max) {
                break;
            }
            String cn = ste.getClassName();
            if (shouldSkipFrame(cn)) {
                continue;
            }
            out.add(ste.getClassName() + "." + ste.getMethodName()
                + "(" + ste.getFileName() + ":" + ste.getLineNumber() + ")");
        }
        if (out.isEmpty() && t.getStackTrace().length > 0) {
            StackTraceElement ste = t.getStackTrace()[0];
            out.add(ste.getClassName() + "." + ste.getMethodName()
                + "(" + ste.getFileName() + ":" + ste.getLineNumber() + ")");
        }
        return out;
    }

    private static boolean shouldSkipFrame(String className) {
        return className.startsWith("java.")
            || className.startsWith("jdk.")
            || className.startsWith("sun.")
            || className.startsWith("org.spongepowered.asm.")
            || className.startsWith("net.fabricmc.loader.impl.launch.")
            || className.startsWith("net.fabricmc.loader.impl.game.minecraft.Hooks");
    }

    private static String hintFor(Throwable t) {
        if (t instanceof ClassNotFoundException || t instanceof NoClassDefFoundError) {
            return "A class from another loader/mod API is missing. Often a NeoForge shim gap on Fabric — "
                + "check the class name in Detail and add or delegate it in Vyne.";
        }
        if (t instanceof NoSuchMethodError) {
            String m = String.valueOf(t.getMessage());
            if (m.contains("codecStart") || m.contains("RecordCodecBuilder")) {
                return "Serialization bridge mismatch (codec/RecordCodecBuilder). Remapper or classloader "
                    + "may need to align DataFixers vs Record builder types for this call site.";
            }
            return "Binary API mismatch (method removed or different signature). Compare mod expectations "
                + "with Vyne NeoForge shims.";
        }
        if (t instanceof NullPointerException) {
            return "Unexpected null — often half-initialized registration when an earlier step failed.";
        }
        return "";
    }

    public static String formatAbbreviatedStackTrace(Throwable t, int maxLines) {
        if (t == null) {
            return "(no exception)";
        }
        return truncatedStackTrace(t, maxLines);
    }

    private static String truncatedStackTrace(Throwable t, int maxLines) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String[] lines = sw.toString().split("\\R");
        StringBuilder sb = new StringBuilder();
        int n = Math.min(lines.length, maxLines);
        for (int i = 0; i < n; i++) {
            sb.append(lines[i]).append(System.lineSeparator());
        }
        if (lines.length > maxLines) {
            sb.append("… (").append(lines.length - maxLines).append(" more lines omitted)").append(System.lineSeparator());
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1) + "…";
    }
}
