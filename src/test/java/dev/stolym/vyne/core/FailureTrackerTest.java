/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FailureTrackerTest {
    @BeforeEach
    void setUp() {
        FailureTracker.clear();
    }

    @AfterEach
    void tearDown() {
        FailureTracker.clear();
    }

    @Test
    void testMarkFailedAndLoad() {
        FailureTracker.markFailed("test_mod_1");
        FailureTracker.markFailed("test_mod_2");

        Set<String> failures = FailureTracker.loadFailures();
        assertEquals(2, failures.size());
        assertTrue(failures.contains("test_mod_1"));
        assertTrue(failures.contains("test_mod_2"));
    }

    @Test
    void testClear() {
        FailureTracker.markFailed("test_mod_1");
        FailureTracker.clear();

        Set<String> failures = FailureTracker.loadFailures();
        assertTrue(failures.isEmpty());
    }

    @Test
    void testConcurrentModFailures() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int modIndex = i;
            executor.submit(() -> {
                try {
                    latch.await();
                    FailureTracker.markFailed("stress_mod_" + modIndex);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown();

        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "Threads did not finish in time");
        executor.shutdown();

        Set<String> failures = FailureTracker.loadFailures();
        assertEquals(threadCount, failures.size(), "Expected all concurrent writes to be recorded.");
    }
}
