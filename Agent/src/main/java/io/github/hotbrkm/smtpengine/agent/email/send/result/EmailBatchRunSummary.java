package io.github.hotbrkm.smtpengine.agent.email.send.result;

import lombok.Builder;

import java.util.Collections;
import java.util.Map;

/**
 * Class containing email batch execution summary information.
 * Includes runner ID, batch-level success/failure counts, and per-domain statistics.
 */
@Builder
public record EmailBatchRunSummary(String runnerId, int totalBatches, int successBatches, int failedBatches,
                                   Map<String, DomainStats> domainStats) {

    public Map<String, DomainStats> domainStatsView() {
        return domainStats == null ? Collections.emptyMap() : Collections.unmodifiableMap(domainStats);
    }

    public record DomainStats(int total, int success) {
        public double successRate() {
            return total > 0 ? (success * 100.0) / total : 0.0;
        }
    }
}
