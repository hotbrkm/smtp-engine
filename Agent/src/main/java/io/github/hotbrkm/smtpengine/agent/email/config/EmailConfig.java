package io.github.hotbrkm.smtpengine.agent.email.config;

import lombok.Data;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "email")
@ConditionalOnProperty(prefix = "email", name = "enabled", havingValue = "true")
@Component
public class EmailConfig {

    private boolean unsubscribeHeaderEnabled;

    private Smtp smtp = new Smtp();
    private Dkim dkim = new Dkim();
    private Send send = new Send();

    @Data
    public static class Smtp {
        private String helo;
        private String from;
    }

    @Data
    public static class Dkim {
        private boolean enabled;

        private String domain;
        private String selector;

        private List<String> targetDomainList;

        private String keyPath;

        public boolean isTargetDomain(String domain) {
            if (domain == null || targetDomainList == null) {
                return false;
            }
            return targetDomainList.contains(domain.toLowerCase(Locale.ROOT));
        }
    }

    @Data
    public static class Send {
        public static final int DEFAULT_REALTIME_AGENT_COUNT = 100;
        public static final int DEFAULT_BATCH_SCHEDULER_INTERVAL_MS = 100;
        public static final int DEFAULT_BATCH_MAX_RETRY_COUNT = 3;
        public static final long DEFAULT_BATCH_INITIAL_RETRY_DELAY_MS = 30_000L;
        public static final long DEFAULT_BATCH_MAX_RETRY_DELAY_MS = 600_000L;
        public static final double DEFAULT_BATCH_RETRY_BACKOFF_MULTIPLIER = 2.0d;

        private List<String> bindAddresses;
        private int realtimeAgentCount = DEFAULT_REALTIME_AGENT_COUNT;
        private int scheduleAgentCount;
        private int bufferingSize;
        private List<String> retryExcludeCode;
        private List<String> dnsServer;
        private List<String> excludedDomain;
        private Map<String, String> fixedIpOfDomain;
        private String spoolDir;
        private String resultUploadDir;

        private boolean dnsTrace;
        private boolean smtpTrace;

        private int maxQueueSize = 30_000;

        private List<String> tlsEnabledProtocols;
        private String tlsApplyScope;
        private List<String> tlsApplyDomain;
        private int tlsMaxAttempts = 2;
        private long tlsRetryDelay = 1000L;

        private int dataReadTimeout = 300;
        private long bindIpAssignWaitTimeoutMs = 30_000L;
        private long bindIpFailureCooldownMs = 5_000L;
        private List<Integer> bindIpCooldownTriggerCodes = List.of(421, 451);
        private Map<Integer, Integer> bindIpCooldownCodeThresholds = new HashMap<>(Map.of(421, 1, 451, 2));


        private boolean simulatorEnabled;
        private String simulatorServer;

        // EmailSendEngine runtime tuning
        private int batchSchedulerIntervalMs = DEFAULT_BATCH_SCHEDULER_INTERVAL_MS;
        private int batchMaxRetryCount = DEFAULT_BATCH_MAX_RETRY_COUNT;
        private long batchInitialRetryDelayMs = DEFAULT_BATCH_INITIAL_RETRY_DELAY_MS;
        private long batchMaxRetryDelayMs = DEFAULT_BATCH_MAX_RETRY_DELAY_MS;
        private double batchRetryBackoffMultiplier = DEFAULT_BATCH_RETRY_BACKOFF_MULTIPLIER;
        private long batchHoldMaxMs;
        private int batchReleasePercentPerCycle = 20;
        private int realtimeRetryMaxPercent = 35;
        private int batchRetryMaxPercent = 35;
        private int retryAgingRelaxPercent = 70;
        private long retryAgingRelaxThresholdMs = 3_000L;
        private int freshReserveMin = 1;
        private int attachmentMaxInFlight = 0;

        // Per-mode override (0 means use global value)
        private int realtimeAttachmentMaxInFlight = 0;
        private int batchAttachmentMaxInFlight = 0;
        private long attachmentAssignWaitTimeoutMs = 30_000L;
        private long domainRefreshIntervalMs = 5000L;
        private long domainRefreshInitialDelayMs = 5000L;

        public int resolveRealtimeAgentCount() {
            return realtimeAgentCount > 0 ? realtimeAgentCount : DEFAULT_REALTIME_AGENT_COUNT;
        }

        public int resolveBatchSchedulerIntervalMs() {
            return batchSchedulerIntervalMs > 0 ? batchSchedulerIntervalMs : DEFAULT_BATCH_SCHEDULER_INTERVAL_MS;
        }

        public int resolveBatchMaxRetryCount() {
            return batchMaxRetryCount >= 0 ? batchMaxRetryCount : DEFAULT_BATCH_MAX_RETRY_COUNT;
        }

        public long resolveBatchInitialRetryDelayMs() {
            return batchInitialRetryDelayMs >= 0 ? batchInitialRetryDelayMs : DEFAULT_BATCH_INITIAL_RETRY_DELAY_MS;
        }

        public long resolveBatchMaxRetryDelayMs() {
            return batchMaxRetryDelayMs >= 0 ? batchMaxRetryDelayMs : DEFAULT_BATCH_MAX_RETRY_DELAY_MS;
        }

        public double resolveBatchRetryBackoffMultiplier() {
            return batchRetryBackoffMultiplier > 0 ? batchRetryBackoffMultiplier : DEFAULT_BATCH_RETRY_BACKOFF_MULTIPLIER;
        }

        public int getCooldownThresholdForCode(int statusCode) {
            if (bindIpCooldownCodeThresholds == null || bindIpCooldownCodeThresholds.isEmpty()) {
                return 1;
            }
            return Math.max(1, bindIpCooldownCodeThresholds.getOrDefault(statusCode, 1));
        }

        public boolean isTlsRequired(String domain) {
            if ("GLOBAL".equals(tlsApplyScope)) {
                return true;
            }

            return tlsApplyDomain.contains(domain.toLowerCase());
        }
    }

}
