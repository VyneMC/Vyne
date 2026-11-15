/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NeoForgeVersionResolverTest {
    @Test
    void testSatisfies() {
        assertTrue(NeoForgeVersionResolver.satisfies("21.1.233", "[21.1,)"));
        assertTrue(NeoForgeVersionResolver.satisfies("21.1.233", "[21.1.0, 21.2.0)"));
        assertFalse(NeoForgeVersionResolver.satisfies("21.1.233", "[21.1.0, 21.1.200]"));
        assertTrue(NeoForgeVersionResolver.satisfies("21.1.233", "21.1.233"));
        assertFalse(NeoForgeVersionResolver.satisfies("21.1.233", "21.1.234"));
        assertTrue(NeoForgeVersionResolver.satisfies("21.1.233", "*"));
        assertTrue(NeoForgeVersionResolver.satisfies("21.1.233", ""));
    }

    @Test
    void testSatisfiesComplex() {
        assertTrue(NeoForgeVersionResolver.satisfies("21.1.233", "[21.0, 22.0)"));
        assertFalse(NeoForgeVersionResolver.satisfies("21.1.233", "(21.1.233, 22.0)"));
        assertTrue(NeoForgeVersionResolver.satisfies("21.1.233", "[21.1.233, 21.1.233]"));
    }
}
