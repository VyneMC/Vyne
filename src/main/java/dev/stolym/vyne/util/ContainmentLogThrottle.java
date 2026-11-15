/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ContainmentLogThrottle {
    public static final long DEFAULT_WINDOW_NANOS = 5_000_000_000L;

    public static final long MAX_WINDOW_NANOS = 5L * 60L * 1_000_000_000L;

    private static final int BACKOFF_AFTER_LOGS = 3;

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    private ContainmentLogThrottle() {}

    public static boolean shouldLog(String siteKey, String signature) {
        return shouldLog(siteKey, signature, DEFAULT_WINDOW_NANOS);
    }

    public static boolean shouldLog(String siteKey, String signature, long windowNanos) {
        Entry entry = ENTRIES.computeIfAbsent(siteKey, k -> new Entry());
        long now = System.nanoTime();
        synchronized (entry) {
            boolean signatureChanged = !signature.equals(entry.signature);

            long effectiveWindow = windowNanos;
            if (!signatureChanged && entry.consecutiveLogs >= BACKOFF_AFTER_LOGS) {
                int shift = Math.min(10, entry.consecutiveLogs - BACKOFF_AFTER_LOGS + 1);
                long scaled = windowNanos << shift;
                if (scaled < 0 || scaled > MAX_WINDOW_NANOS) scaled = MAX_WINDOW_NANOS;
                effectiveWindow = scaled;
            }
            if (signatureChanged || now - entry.lastLogNanos > effectiveWindow) {
                int skipped = entry.suppressed.getAndSet(0);
                if (signatureChanged) {
                    entry.consecutiveLogs = 1;
                } else {
                    entry.consecutiveLogs++;
                }
                entry.signature = signature;
                entry.lastLogNanos = now;
                entry.pendingSkippedReport = skipped;
                return true;
            }
            entry.suppressed.incrementAndGet();
            return false;
        }
    }

    public static String drainSkippedSummary(String siteKey) {
        Entry entry = ENTRIES.get(siteKey);
        if (entry == null) return null;
        int skipped;
        synchronized (entry) {
            skipped = entry.pendingSkippedReport;
            entry.pendingSkippedReport = 0;
        }
        if (skipped <= 0) return null;
        return "(" + skipped + " identical throwable" + (skipped == 1 ? "" : "s") + " suppressed since the last log)";
    }

    public static void flush() {
        ENTRIES.clear();
    }

    private static final class Entry {
        volatile String signature = "";
        volatile long lastLogNanos = 0L;
        final AtomicInteger suppressed = new AtomicInteger();
        int pendingSkippedReport = 0;

        int consecutiveLogs = 0;
    }

    public static String signatureOf(Throwable t, String discriminator) {
        String base = t.getClass().getName() + ":" + java.util.Objects.toString(t.getMessage(), "");
        return discriminator == null || discriminator.isEmpty() ? base : base + "|" + discriminator;
    }
}
