package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Set of normalized runtime options for the email engine.
 */
record EngineRuntimeOptions(int workerCount,
                            int schedulerIntervalMs,
                            int maxRetryCount,
                            int realtimeAttachmentMaxInFlight,
                            int batchAttachmentMaxInFlight,
                            long attachmentAssignWaitTimeoutMs,
                            long initialRetryDelayMs,
                            long maxRetryDelayMs,
                            double retryBackoffMultiplier,
                            List<String> bindIps,
                            Set<Integer> bindIpCooldownTriggerCodes) {

    /**
     * Calculates runtime options from send configuration.
     */
    static EngineRuntimeOptions fromSendConfig(EmailConfig.Send sendConfig) {
        Objects.requireNonNull(sendConfig, "sendConfig must not be null");
        return of(sendConfig,
                sendConfig.resolveRealtimeAgentCount(),
                sendConfig.resolveBatchSchedulerIntervalMs(),
                sendConfig.resolveBatchMaxRetryCount(),
                sendConfig.resolveBatchInitialRetryDelayMs(),
                sendConfig.resolveBatchMaxRetryDelayMs(),
                sendConfig.resolveBatchRetryBackoffMultiplier());
    }

    /**
     * Calculates runtime options from explicit parameters for testing/custom execution.
     */
    static EngineRuntimeOptions fromExplicit(EmailConfig.Send sendConfig,
                                             int maxConcurrentWorkers, int schedulerIntervalMs, int maxRetryCount,
                                             long initialRetryDelayMillis, long maxRetryDelayMillis, double retryBackoffMultiplier) {
        Objects.requireNonNull(sendConfig, "sendConfig must not be null");
        return of(sendConfig, maxConcurrentWorkers, schedulerIntervalMs, maxRetryCount,
                initialRetryDelayMillis, maxRetryDelayMillis, retryBackoffMultiplier);
    }

    /**
     * Normalizes various input configuration values into an executable form.
     */
    private static EngineRuntimeOptions of(EmailConfig.Send sendConfig,
                                           int maxConcurrentWorkers, int schedulerIntervalMs, int maxRetryCount,
                                           long initialRetryDelayMillis, long maxRetryDelayMillis, double retryBackoffMultiplier) {
        int normalizedWorkerCount = Math.max(1, maxConcurrentWorkers);
        int normalizedSchedulerIntervalMs = Math.max(10, schedulerIntervalMs);
        int normalizedMaxRetryCount = Math.max(0, maxRetryCount);
        int globalAttachment = Math.max(0, sendConfig.getAttachmentMaxInFlight());
        int normalizedRealtimeAttachment = resolvePerMode(sendConfig.getRealtimeAttachmentMaxInFlight(), globalAttachment);
        int normalizedBatchAttachment = resolvePerMode(sendConfig.getBatchAttachmentMaxInFlight(), globalAttachment);
        long normalizedAttachmentAssignWaitTimeoutMs = Math.max(0L, sendConfig.getAttachmentAssignWaitTimeoutMs());
        long normalizedInitialRetryDelayMs = Math.max(0L, initialRetryDelayMillis);
        long normalizedMaxRetryDelayMs = Math.max(normalizedInitialRetryDelayMs, maxRetryDelayMillis);
        double normalizedRetryBackoffMultiplier = retryBackoffMultiplier <= 0
                ? EmailConfig.Send.DEFAULT_BATCH_RETRY_BACKOFF_MULTIPLIER
                : retryBackoffMultiplier;
        List<String> normalizedBindIps = normalizeBindIps(sendConfig.getBindAddresses());
        if (normalizedBindIps.isEmpty()) {
            throw new IllegalArgumentException("email.send.bind-addresses must not be empty");
        }
        Set<Integer> normalizedBindIpCooldownTriggerCodes = toStatusCodeSet(sendConfig.getBindIpCooldownTriggerCodes());
        return new EngineRuntimeOptions(
                normalizedWorkerCount,
                normalizedSchedulerIntervalMs,
                normalizedMaxRetryCount,
                normalizedRealtimeAttachment,
                normalizedBatchAttachment,
                normalizedAttachmentAssignWaitTimeoutMs,
                normalizedInitialRetryDelayMs,
                normalizedMaxRetryDelayMs,
                normalizedRetryBackoffMultiplier,
                normalizedBindIps,
                normalizedBindIpCooldownTriggerCodes
        );
    }

    private static int resolvePerMode(int perModeValue, int globalFallback) {
        int normalized = Math.max(0, perModeValue);
        return normalized > 0 ? normalized : globalFallback;
    }

    private static Set<Integer> toStatusCodeSet(List<Integer> codes) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Integer> result = new HashSet<>();
        for (Integer code : codes) {
            if (code != null && code > 0) {
                result.add(code);
            }
        }
        return result;
    }

    private static List<String> normalizeBindIps(List<String> bindIps) {
        if (bindIps == null || bindIps.isEmpty()) {
            return List.of();
        }
        return bindIps.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .distinct()
                .toList();
    }
}
