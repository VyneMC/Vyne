/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForgeVersionConfigTest {
    @Test
    void olderStoredVersionDoesNotWin() {
        assertFalse(VyneContainmentConfig.isNewer("21.1.233", "21.1.265"));
        assertFalse(VyneContainmentConfig.isNewer("21.1.265", "21.1.265"));
        assertFalse(VyneContainmentConfig.isNewer("", "21.1.265"));
    }

    @Test
    void newerStoredVersionWins() {
        assertTrue(VyneContainmentConfig.isNewer("21.1.266", "21.1.265"));
        assertTrue(VyneContainmentConfig.isNewer("21.2.1", "21.1.265"));
        assertTrue(VyneContainmentConfig.isNewer("21.1.300-beta", "21.1.265"));
    }
}
