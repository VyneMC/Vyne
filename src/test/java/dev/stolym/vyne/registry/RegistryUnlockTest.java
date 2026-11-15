/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.registry;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegistryUnlockTest {
    static class FakeRegistry {
        boolean frozen = true;
        Map<Object, Object> unregisteredIntrusiveHolders;
        int freezeCalls;

        void freeze() {
            freezeCalls++;
            if (unregisteredIntrusiveHolders != null && !unregisteredIntrusiveHolders.isEmpty()) {
                throw new IllegalStateException("Some intrusive holders were not registered");
            }
            frozen = true;
            unregisteredIntrusiveHolders = null;
        }
    }

    @Test
    void frozenRegistryIsOpenedAndFrozenAgain() {
        FakeRegistry reg = new FakeRegistry();
        RegistryUnlock.Token token = RegistryUnlock.open(reg);
        assertFalse(reg.frozen);
        assertNotNull(reg.unregisteredIntrusiveHolders);
        RegistryUnlock.close(token);
        assertTrue(reg.frozen);
        assertEquals(1, reg.freezeCalls);
        assertNull(reg.unregisteredIntrusiveHolders);
    }

    @Test
    void openRegistryIsLeftAlone() {
        FakeRegistry reg = new FakeRegistry();
        reg.frozen = false;
        RegistryUnlock.close(RegistryUnlock.open(reg));
        assertFalse(reg.frozen);
        assertEquals(0, reg.freezeCalls);
    }

    @Test
    void failedFreezeStillClosesTheRegistry() {
        FakeRegistry reg = new FakeRegistry();
        RegistryUnlock.Token token = RegistryUnlock.open(reg);
        reg.unregisteredIntrusiveHolders.put(new Object(), new Object());
        RegistryUnlock.close(token);
        assertTrue(reg.frozen);
        assertNull(reg.unregisteredIntrusiveHolders);
    }

    @Test
    void nullRegistryIsANoOp() {
        RegistryUnlock.close(RegistryUnlock.open(null));
    }
}
