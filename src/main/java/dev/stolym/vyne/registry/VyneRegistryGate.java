/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.registry;

import java.util.concurrent.CountDownLatch;

public class VyneRegistryGate {
    private static final CountDownLatch latch = new CountDownLatch(1);

    public static void unlock() {
        latch.countDown();
    }

    public static void await() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
