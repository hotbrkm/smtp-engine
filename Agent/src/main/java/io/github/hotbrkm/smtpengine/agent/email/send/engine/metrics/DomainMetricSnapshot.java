package io.github.hotbrkm.smtpengine.agent.email.send.engine.metrics;

import java.util.Map;

/**
 * Snapshot of aggregated sending metrics for a specific domain over a time window.
 *
 * @param domain               Domain name
 * @param durationSeconds      Aggregation duration (seconds)
 * @param successCount         Success count
 * @param failureCount         Failure count
 * @param resultCodeCounts     Counts per result code
 * @param retryCount           Retry count
 * @param sessionFailureCount  Session failure count
 * @param totalResponseTimeMs  Total response time (ms)
 * @param responseTimeSampleCount Response time sample count
 */
public record DomainMetricSnapshot(String domain, int durationSeconds, long successCount, long failureCount,
        Map<Integer, Long> resultCodeCounts, long retryCount, long sessionFailureCount, long totalResponseTimeMs,
        long responseTimeSampleCount) {

    /**
     * Returns the average SMTP response time (ms). Returns 0.0 if no samples.
     */
    public double avgResponseTimeMs() {
        if (responseTimeSampleCount == 0) {
            return 0.0;
        }
        return (double) totalResponseTimeMs / responseTimeSampleCount;
    }

    /**
     * Returns the sum of 4xx result codes (soft bounce).
     */
    public long softBounceCount() {
        long count = 0;
        for (Map.Entry<Integer, Long> entry : resultCodeCounts.entrySet()) {
            int code = entry.getKey();
            if (code >= 400 && code < 500) {
                count += entry.getValue();
            }
        }
        return count;
    }

    /**
     * Returns the sum of 5xx result codes (hard bounce).
     */
    public long hardBounceCount() {
        long count = 0;
        for (Map.Entry<Integer, Long> entry : resultCodeCounts.entrySet()) {
            int code = entry.getKey();
            if (code >= 500 && code < 600) {
                count += entry.getValue();
            }
        }
        return count;
    }
}
