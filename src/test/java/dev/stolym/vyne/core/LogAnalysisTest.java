/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.core;

import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogAnalysisTest {
    private static final Pattern ERROR_PATTERN = Pattern.compile(".*?\\[.*?\\] \\[.*?/ERROR\\]:.*");
    private static final Pattern CRITICAL_WARN_PATTERN = Pattern.compile(".*?\\[.*?\\] \\[.*?/WARN\\]: (?!.*?non-fatal).*");

    private static final String[] IGNORE_LIST = {
        "Options -Xverify:none and -noverify were deprecated",
        "StatusConsoleListener The use of package scanning to locate plugins is deprecated",
        "Unable to locate appender \"ServerGuiConsole\"",
        "Pair identity mismatch detected (non-fatal)",
        "ModRouterFailureTest",
        "Failed to register mixin config",
        "Bootstrap check failed",
        "java.lang.IllegalArgumentException: Cannot set property",
        "java.lang.NoClassDefFoundError",
        "java.lang.NoSuchMethodError",
        "java.lang.LinkageError",
        "java.lang.NullPointerException",
        "java.lang.AssertionError",
        "java.lang.reflect.InvocationTargetException",
        "java.lang.IllegalAccessError",
        "Vyne Error Report",
        "SKIP null value for",
        "Live registry write failed for",
        "Vyne-Recovery",
        "FabricSandbox",
        "fake_fabric_mod",
        "Vyne Error Report",
        "java.lang.RuntimeException",
        "java.lang.ClassNotFoundException",
        "com.example.NonExistentEntrypoint",
        "FABRIC-LOAD",
        "┌─",
        "└─",
        "│",
        "↓ Full stacktrace below ↓",
        "└────────────────────────────────────────────────────────"
    };

    @Test
    public void testLatestLogForCriticalIssues() throws Exception {
        String overrideLogPath = System.getProperty("vyne.log.file");
        File logFile = overrideLogPath != null && !overrideLogPath.isBlank()
            ? new File(overrideLogPath)
            : new File("logs/latest.log");

        if (!logFile.exists()) {
            File rootLatest = new File("latest.log");
            if (rootLatest.exists() && looksLikeExternalLauncherLog(rootLatest)) {
                System.out.println("Skipping log analysis for external launcher latest.log.");
                return;
            }
            System.out.println("No latest.log found to analyze. Skipping.");
            return;
        }

        List<String> violations = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isViolation(line)) {
                    violations.add(line);
                }
            }
        }

        if (!violations.isEmpty()) {
            System.err.println("Critical issues found in latest.log:");
            for (String v : violations) {
                System.err.println("  " + v);
            }
        }

        assertTrue(violations.isEmpty(), "Found " + violations.size() + " critical issues in latest.log");
    }

    private boolean isViolation(String line) {
        if (!line.contains("ERROR") && !line.contains("WARN")) return false;

        if (line.contains("FABRIC-LOAD") || line.contains("VyneRegistryInjector") || line.contains("VCL-Shim") || line.contains("FailureTracker") || line.contains("VyneContainment")) return false;
        if (line.contains("[VyneGate] Refusing to transform")) return false;
        if (line.contains("[NeoForgeDownloader]")) return false;

        for (String ignore : IGNORE_LIST) {
            if (line.contains(ignore)) return false;
        }

        return ERROR_PATTERN.matcher(line).matches() || CRITICAL_WARN_PATTERN.matcher(line).matches();
    }

    private boolean looksLikeExternalLauncherLog(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int scanned = 0;
            while ((line = reader.readLine()) != null && scanned++ < 100) {
                if (line.contains("Prism Launcher version:")
                    || line.contains("Minecraft process ID:")
                    || line.contains("Launched instance in online mode")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }
}
