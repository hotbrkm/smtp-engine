package io.github.hotbrkm.smtpengine.agent.email.send.planning;

import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchResult;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchRunSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Batch aggregator
 * <p>
 * - Wait for all batch Futures to complete (with timeout)
 * - Aggregate batch success/failure counts
 * - Generate per-domain transmission statistics and output summary log
 */
@Slf4j
@RequiredArgsConstructor
public final class EmailBatchAggregator {
    private final String runnerId;
    private final int topDomainSummaryLimit;

    /**
     * Aggregates Future list to create execution summary
     *
     * @param futures   List of batch processing result Futures
     * @param timeoutMs Total wait timeout (ms)
     * @return Execution summary info
     */
    public EmailBatchRunSummary aggregate(List<CompletableFuture<EmailBatchResult>> futures, long timeoutMs) {
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            all.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("runnerId={}, event=wait_timeout, timeoutMs={}", runnerId, timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("runnerId={}, event=wait_interrupted", runnerId);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("runnerId={}, event=wait_error, message={}", runnerId, cause.getMessage());
        }

        int successCount = 0;
        int failureCount = 0;
        Map<String, Integer> domainTotals = new HashMap<>();
        Map<String, Integer> domainSuccesses = new HashMap<>();

        for (CompletableFuture<EmailBatchResult> future : futures) {
            if (!future.isDone()) {
                failureCount++;
                log.warn("runnerId={}, event=batch_not_completed_within_timeout", runnerId);
                continue;
            }
            try {
                EmailBatchResult result = future.join();
                if (result.success()) {
                    successCount++;
                } else {
                    failureCount++;
                    log.warn("runnerId={}, batchId={}, event=batch_failed, errorMessage={}",
                            runnerId, result.batchId(),
                            result.exception() != null ? result.exception().getMessage() : "Unknown error");
                }
                aggregateDomainStats(domainTotals, domainSuccesses, result);
            } catch (CompletionException e) {
                failureCount++;
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                log.warn("runnerId={}, event=future_completed_exceptionally, message={}", runnerId, cause.getMessage());
            } catch (Exception e) {
                failureCount++;
                log.warn("runnerId={}, event=error_while_aggregating, message={}", runnerId, e.getMessage());
            }
        }

        log.info("runnerId={}, successBatches={}, failedBatches={}, totalBatches={}",
                runnerId, successCount, failureCount, futures.size());

        // Top domain summary log
        logTopDomainSummary(domainTotals, domainSuccesses);

        // Compose DomainStats
        Map<String, EmailBatchRunSummary.DomainStats> stats = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : domainTotals.entrySet()) {
            String domain = e.getKey();
            int total = e.getValue();
            int success = domainSuccesses.getOrDefault(domain, 0);
            stats.put(domain, new EmailBatchRunSummary.DomainStats(total, success));
        }

        return EmailBatchRunSummary.builder()
                .runnerId(runnerId)
                .totalBatches(futures.size())
                .successBatches(successCount)
                .failedBatches(failureCount)
                .domainStats(stats)
                .build();
    }

    /**
     * Accumulates per-domain total/success counts
     */
    private void aggregateDomainStats(Map<String, Integer> domainTotals, Map<String, Integer> domainSuccesses, EmailBatchResult result) {
        domainTotals.merge(result.domain(), result.totalTargets(), Integer::sum);
        domainSuccesses.merge(result.domain(), result.successCount(), Integer::sum);
    }

    /**
     * Output top domain summary log by transmission count
     */
    private void logTopDomainSummary(Map<String, Integer> domainTotals, Map<String, Integer> domainSuccesses) {
        if (domainTotals.isEmpty()) {
            return;
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(domainTotals.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        int limit = Math.min(topDomainSummaryLimit, entries.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> e = entries.get(i);
            String domain = e.getKey();
            int total = e.getValue();
            int success = domainSuccesses.getOrDefault(domain, 0);
            double rate = total > 0 ? (success * 100.0) / total : 0.0;
            if (i > 0) sb.append(", ");
            sb.append(domain)
              .append(" (total=").append(total)
              .append(", success=").append(success)
              .append(", successRate=").append(String.format("%.2f%%", rate))
              .append(")");
        }
        log.info("runnerId={}, topDomainLimit={}, topDomains={}", runnerId, topDomainSummaryLimit, sb);
    }
}
