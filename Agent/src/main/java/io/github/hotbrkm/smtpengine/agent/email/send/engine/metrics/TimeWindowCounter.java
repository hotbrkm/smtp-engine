package io.github.hotbrkm.smtpengine.agent.email.send.engine.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Configurable window-based time window counter.
 * <p>
 * Thread-safe, uses {@link ConcurrentHashMap} and {@link LongAdder} to accumulate counts
 * without contention in high-concurrency environments.
 * <p>
 * Each window is managed in {@code windowSeconds} units and removed via {@link #evict()}
 * after the retention window count passes.
 */
class TimeWindowCounter {

    private final ConcurrentHashMap<Long, LongAdder> windows = new ConcurrentHashMap<>();
    private final int retentionWindows;
    private final int windowSeconds;

    /**
     * Creates a counter with the specified window unit.
     *
     * @param retentionWindows Number of windows to retain
     * @param windowSeconds    Size of one window (seconds)
     */
    TimeWindowCounter(int retentionWindows, int windowSeconds) {
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be positive: " + windowSeconds);
        }
        this.retentionWindows = retentionWindows;
        this.windowSeconds = windowSeconds;
    }

    /**
     * Accumulates counts in the current time window.
     *
     * @param count Count to accumulate
     */
    void record(long count) {
        if (count <= 0) {
            return;
        }
        long windowKey = currentWindowKey();
        windows.computeIfAbsent(windowKey, k -> new LongAdder()).add(count);
    }

    /**
     * Returns the sum of only the last {@code windows} completed windows.
     *
     * @param windows Number of windows to query
     * @return Sum of the range
     */
    long sumRange(int windows) {
        if (windows <= 0) {
            return 0;
        }

        long now = currentWindowKey();
        long from = now - windows;
        long total = 0;

        for (long t = from; t < now; t++) {
            LongAdder adder = this.windows.get(t);
            if (adder != null) {
                total += adder.sum();
            }
        }
        return total;
    }

    /**
     * Removes windows that have passed the retention period.
     */
    void evict() {
        long cutoff = currentWindowKey() - retentionWindows;
        windows.keySet().removeIf(key -> key < cutoff);
    }

    /**
     * Returns the number of currently held windows. (For testing/monitoring)
     */
    int windowCount() {
        return windows.size();
    }

    private long currentWindowKey() {
        return System.currentTimeMillis() / 1000 / windowSeconds;
    }
}
