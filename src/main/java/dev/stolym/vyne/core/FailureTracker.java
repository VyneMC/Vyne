/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.core;

import dev.stolym.vyne.containment.VyneModStateRegistry;
import dev.stolym.vyne.util.VyneLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class FailureTracker {
    private static final Path FAILURE_FILE = Paths.get(".vyne", "failures.txt");

    public static void markFailed(String modId) {
        markFailed(modId, "Persisted from previous session",
                VyneModStateRegistry.FailureSubsystem.UNKNOWN, null);
    }

    public static void markFailed(String modId, String reason,
                                   VyneModStateRegistry.FailureSubsystem subsystem,
                                   Throwable cause) {
        VyneModStateRegistry.freeze(modId, reason, subsystem, cause);
        synchronized (FAILURE_FILE) {
            try {
                Set<String> failures = loadFailures();
                failures.add(modId);
                Files.createDirectories(FAILURE_FILE.getParent());
                Files.write(FAILURE_FILE, failures);
            } catch (IOException ignored) {}
        }
        VyneLogger.warn("[FailureTracker] Marked '" + modId + "' as failed (persisted).");
    }

    public static Set<String> loadFailures() {
        try {
            if (!Files.exists(FAILURE_FILE)) return new HashSet<>();
            return Files.readAllLines(FAILURE_FILE).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            return new HashSet<>();
        }
    }

    public static void clear() {
        try {
            Files.deleteIfExists(FAILURE_FILE);
        } catch (IOException ignored) {}
    }
}
