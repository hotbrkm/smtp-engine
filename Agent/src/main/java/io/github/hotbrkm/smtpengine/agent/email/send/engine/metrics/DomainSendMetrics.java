package io.github.hotbrkm.smtpengine.agent.email.send.engine.metrics;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggregates per-domain email sending metrics in configurable window units.
 * <p>
 * Aggregation is thread-safe on worker threads based on {@link TimeWindowCounter} without contention.
 */
public class DomainSendMetrics {

    private final ConcurrentHashMap<String, DomainCounters> countersByDomain = new ConcurrentHashMap<>();
    private final int retentionWindows;
    private final int windowSeconds;

    /**
     * @param retentionWindows Number of windows to retain
     * @param windowSeconds    Size of one window (seconds)
     */
    public DomainSendMetrics(int retentionWindows, int windowSeconds) {
        this.retentionWindows = retentionWindows;
        this.windowSeconds = windowSeconds;
    }

    /**
     * Records 1 success.
     */
    public void recordSuccess(String domain) {
        getCounters(domain).success.record(1);
    }

    /**
     * Records 1 failure and the result code.
     */
    public void recordFailure(String domain, int statusCode) {
        getCounters(domain).failure.record(1);
    }

    /**
     * Records result code only (including success).
     */
    public void recordResultCode(String domain, int statusCode) {
        getCounters(domain).resultCodes
                .computeIfAbsent(statusCode, k -> new TimeWindowCounter(retentionWindows, windowSeconds))
                .record(1);
    }

    /**
     * Records retry count.
     */
    public void recordRetry(String domain, long count) {
        if (count <= 0) {
            return;
        }
        getCounters(domain).retry.record(count);
    }

    /**
     * Records 1 session failure.
     */
    public void recordSessionFailure(String domain) {
        getCounters(domain).sessionFailure.record(1);
    }

    /**
     * Records SMTP response time.
     */
    public void recordResponseTime(String domain, long elapsedMs) {
        if (elapsedMs < 0) {
            return;
        }
        DomainCounters counters = getCounters(domain);
        counters.responseTimeSum.record(elapsedMs);
        counters.responseTimeCount.record(1);
    }

    /**
     * Returns a snapshot of the last 'seconds' for a specific domain.
     */
    public DomainMetricSnapshot snapshot(String domain, int seconds) {
        int windows = Math.max(1, seconds / windowSeconds);
        DomainCounters counters = countersByDomain.get(domain);
        if (counters == null) {
            return new DomainMetricSnapshot(domain, seconds, 0, 0, Collections.emptyMap(), 0, 0, 0, 0);
        }
        return buildSnapshot(domain, seconds, windows, counters);
    }

    /**
     * Returns snapshots of the last 'seconds' for all domains.
     */
    public Map<String, DomainMetricSnapshot> snapshotAll(int seconds) {
        int windows = Math.max(1, seconds / windowSeconds);
        Map<String, DomainMetricSnapshot> result = new HashMap<>();
        for (Map.Entry<String, DomainCounters> entry : countersByDomain.entrySet()) {
            result.put(entry.getKey(), buildSnapshot(entry.getKey(), seconds, windows, entry.getValue()));
        }
        return result;
    }

    /**
     * Removes windows that have passed the retention period.
     */
    public void evict() {
        for (DomainCounters counters : countersByDomain.values()) {
            counters.success.evict();
            counters.failure.evict();
            counters.retry.evict();
            counters.sessionFailure.evict();
            counters.responseTimeSum.evict();
            counters.responseTimeCount.evict();
            for (TimeWindowCounter codeCounter : counters.resultCodes.values()) {
                codeCounter.evict();
            }
            counters.resultCodes.entrySet().removeIf(e -> e.getValue().windowCount() == 0);
        }
        countersByDomain.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    private DomainCounters getCounters(String domain) {
        return countersByDomain.computeIfAbsent(domain, k -> new DomainCounters(retentionWindows, windowSeconds));
    }

    private DomainMetricSnapshot buildSnapshot(String domain, int durationSeconds, int windows, DomainCounters counters) {
        long success = counters.success.sumRange(windows);
        long failure = counters.failure.sumRange(windows);
        long retry = counters.retry.sumRange(windows);
        long sessionFailure = counters.sessionFailure.sumRange(windows);
        long responseTimeSum = counters.responseTimeSum.sumRange(windows);
        long responseTimeCount = counters.responseTimeCount.sumRange(windows);

        Map<Integer, Long> resultCodeCounts = new HashMap<>();
        for (Map.Entry<Integer, TimeWindowCounter> entry : counters.resultCodes.entrySet()) {
            long count = entry.getValue().sumRange(windows);
            if (count > 0) {
                resultCodeCounts.put(entry.getKey(), count);
            }
        }

        return new DomainMetricSnapshot(domain, durationSeconds, success, failure,
                Collections.unmodifiableMap(resultCodeCounts), retry, sessionFailure,
                responseTimeSum, responseTimeCount);
    }

    /**
     * Bundle of per-domain counters. All fields are thread-safe based on {@link TimeWindowCounter}.
     */
    static final class DomainCounters {
        private final TimeWindowCounter success;
        private final TimeWindowCounter failure;
        private final ConcurrentHashMap<Integer, TimeWindowCounter> resultCodes;
        private final TimeWindowCounter retry;
        private final TimeWindowCounter sessionFailure;
        private final TimeWindowCounter responseTimeSum;
        private final TimeWindowCounter responseTimeCount;

        DomainCounters(int retentionWindows, int windowSeconds) {
            this.success = new TimeWindowCounter(retentionWindows, windowSeconds);
            this.failure = new TimeWindowCounter(retentionWindows, windowSeconds);
            this.resultCodes = new ConcurrentHashMap<>();
            this.retry = new TimeWindowCounter(retentionWindows, windowSeconds);
            this.sessionFailure = new TimeWindowCounter(retentionWindows, windowSeconds);
            this.responseTimeSum = new TimeWindowCounter(retentionWindows, windowSeconds);
            this.responseTimeCount = new TimeWindowCounter(retentionWindows, windowSeconds);
        }

        boolean isEmpty() {
            return success.windowCount() == 0 && failure.windowCount() == 0 && resultCodes.isEmpty()
                    && retry.windowCount() == 0 && sessionFailure.windowCount() == 0
                    && responseTimeSum.windowCount() == 0 && responseTimeCount.windowCount() == 0;
        }
    }
}
