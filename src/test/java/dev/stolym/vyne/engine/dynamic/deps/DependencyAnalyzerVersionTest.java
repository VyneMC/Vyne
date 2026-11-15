/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic.deps;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.engine.dynamic.deps.DependencyAnalyzer.Report;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyAnalyzerVersionTest {
    private static DetectedMod mod(String id, String version) {
        return new DetectedMod(null, DetectedMod.LoaderType.NEOFORGE, id, id, version, null, null);
    }

    @Test
    void satisfiedRangeDoesNotDisableTheMod() {
        DetectedMod consumer = mod("craftpresence", "2.7.1");
        consumer.addDependency("unilib", ">=1.2.0");
        DetectedMod lib = mod("unilib", "1.2.1+1.21.1");

        Report report = DependencyAnalyzer.analyze(List.of(consumer, lib));

        assertFalse(report.modsToDisable.contains("craftpresence"),
                "mod must not be disabled when the installed version satisfies the range");
        assertTrue(report.issues.isEmpty() || report.issues.stream().noneMatch(i -> i.severity == DependencyAnalyzer.Severity.ERROR));
    }

    @Test
    void unsatisfiedRangeDisablesTheModWithClearMessage() {
        DetectedMod consumer = mod("consumer", "1.0.0");
        consumer.addDependency("unilib", ">=2.0.0");
        DetectedMod lib = mod("unilib", "1.2.1");

        Report report = DependencyAnalyzer.analyze(List.of(consumer, lib));

        assertTrue(report.modsToDisable.contains("consumer"));
        assertTrue(report.issues.stream().anyMatch(i ->
                        i.severity == DependencyAnalyzer.Severity.ERROR
                                && i.message.contains("installed version is 1.2.1")),
                "the report must name the installed version, not claim the dep is missing");
    }

    @Test
    void missingRequiredDependencyStillDisables() {
        DetectedMod consumer = mod("consumer", "1.0.0");
        consumer.addDependency("totally-absent", ">=1.0.0");

        Report report = DependencyAnalyzer.analyze(List.of(consumer));

        assertTrue(report.modsToDisable.contains("consumer"));
    }

    @Test
    void missingOptionalDependencyIsOnlyInfo() {
        DetectedMod consumer = mod("consumer", "1.0.0");
        consumer.addOptionalDependency("totally-absent", ">=1.0.0");

        Report report = DependencyAnalyzer.analyze(List.of(consumer));

        assertFalse(report.modsToDisable.contains("consumer"));
        assertTrue(report.issues.stream().allMatch(i -> i.severity == DependencyAnalyzer.Severity.INFO));
    }

    @Test
    void environmentIdsAreNeverChecked() {
        DetectedMod consumer = mod("consumer", "1.0.0");
        consumer.addDependency("neoforge", ">=1.0.0");

        Report report = DependencyAnalyzer.analyze(List.of(consumer));

        assertFalse(report.modsToDisable.contains("consumer"));
    }
}
