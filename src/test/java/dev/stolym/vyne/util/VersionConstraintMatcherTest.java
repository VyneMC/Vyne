/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionConstraintMatcherTest {
    @Test
    void anyConstraintsMatchEverything() {
        assertTrue(VersionConstraintMatcher.matches("0.0.1", null));
        assertTrue(VersionConstraintMatcher.matches("0.0.1", ""));
        assertTrue(VersionConstraintMatcher.matches("0.0.1", "*"));
        assertTrue(VersionConstraintMatcher.matches("0.0.1", "any"));
    }

    @Test
    void greaterOrEqual() {
        assertTrue(VersionConstraintMatcher.matches("1.2.1", ">=1.2.0"));
        assertTrue(VersionConstraintMatcher.matches("1.2.0", ">=1.2.0"));
        assertFalse(VersionConstraintMatcher.matches("1.1.9", ">=1.2.0"));
    }

    @Test
    void lessOrEqual() {
        assertTrue(VersionConstraintMatcher.matches("1.0.0", "<=1.2.0"));
        assertFalse(VersionConstraintMatcher.matches("1.2.1", "<=1.2.0"));
    }

    @Test
    void strictComparisons() {
        assertTrue(VersionConstraintMatcher.matches("1.2.1", ">1.2.0"));
        assertFalse(VersionConstraintMatcher.matches("1.2.0", ">1.2.0"));
        assertTrue(VersionConstraintMatcher.matches("1.1.9", "<1.2.0"));
        assertFalse(VersionConstraintMatcher.matches("1.2.0", "<1.2.0"));
    }

    @Test
    void plainPrefixMatch() {
        assertTrue(VersionConstraintMatcher.matches("1.2.1", "1.2"));
        assertFalse(VersionConstraintMatcher.matches("2.0.0", "1.2"));
    }

    @Test
    void mavenRanges() {
        assertTrue(VersionConstraintMatcher.matches("1.5", "[1.2,2.0)"));
        assertTrue(VersionConstraintMatcher.matches("1.2", "[1.2,2.0)"));
        assertFalse(VersionConstraintMatcher.matches("2.0", "[1.2,2.0)"));
        assertTrue(VersionConstraintMatcher.matches("2.0", "(1.2,2.0]"));
        assertFalse(VersionConstraintMatcher.matches("1.2", "(1.2,2.0]"));
    }

    @Test
    void numericComparisonNotLexicographic() {
        assertTrue(VersionConstraintMatcher.matches("1.10.0", ">=1.9.0"));
        assertFalse(VersionConstraintMatcher.matches("1.2.0", ">=1.10.0"));
    }

    @Test
    void uniLibScenario() {
        assertTrue(VersionConstraintMatcher.matches("1.2.1+1.21.1", ">=1.2.0"));
    }
}
