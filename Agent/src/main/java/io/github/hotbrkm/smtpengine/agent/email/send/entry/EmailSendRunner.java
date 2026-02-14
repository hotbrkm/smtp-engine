package io.github.hotbrkm.smtpengine.agent.email.send.entry;

import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.EmailSendEngine;
import io.github.hotbrkm.smtpengine.agent.email.send.planning.EmailBatchAggregator;
import io.github.hotbrkm.smtpengine.agent.email.send.planning.EmailBatchPlanner;
import io.github.hotbrkm.smtpengine.agent.email.send.planning.EmailBatchSpec;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchResult;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchResultWriter;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchRunSummary;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Email send runner.
 * <p>
 * Takes target data (typed list) as input, groups them into domain-based batches,
 * and submits them to the engine (EmailSendEngine).
 * Waits until all submitted batches complete and outputs a completion summary log.
 */
@Slf4j
public class EmailSendRunner {

    private static final int TOP_DOMAIN_SUMMARY_LIMIT = 5;
    private static final long COMPLETION_TIMEOUT_MS = 300_000L;

    private final String runnerId;
    private final List<EmailSendTarget> emailSendTargets;
    private final EmailSendContext emailSendContext;
    private final EmailDomainManager emailDomainManager;
    private final EmailSendEngine emailSendEngine;
    private final EmailBatchResultWriter resultWriter;

    public EmailSendRunner(List<EmailSendTarget> emailSendTargets, EmailSendContext emailSendContext, EmailDomainManager emailDomainManager,
                           EmailSendEngine emailSendEngine) {
        this(emailSendTargets, emailSendContext, emailDomainManager, emailSendEngine, null);
    }

    public EmailSendRunner(List<EmailSendTarget> emailSendTargets, EmailSendContext emailSendContext, EmailDomainManager emailDomainManager,
                           EmailSendEngine emailSendEngine, EmailBatchResultWriter resultWriter) {
        this.runnerId = emailSendContext.toRunnerId();
        this.emailSendTargets = emailSendTargets;
        this.emailSendContext = emailSendContext;
        this.emailDomainManager = emailDomainManager;
        this.emailSendEngine = emailSendEngine;
        this.resultWriter = resultWriter;
    }

    /**
     * Executes batches and returns summary information.
     * Runs in order: plan → submit → wait/aggregate.
     *
     * @return execution summary information
     */
    public EmailBatchRunSummary execute() {
        log.info("runnerId={}, event=starting, targets={}", runnerId, emailSendTargets.size());
        emailSendEngine.registerRunner(runnerId, resultWriter);

        long start = System.currentTimeMillis();
        try {
            // 1. Create batches
            List<EmailBatchSpec> batchSpecs = createBatches(emailSendTargets);
            log.debug("runnerId={}, batchesCreated={}, targets={}", runnerId, batchSpecs.size(), emailSendTargets.size());

            // 2. Submit batches to Engine and collect CompletableFutures
            List<CompletableFuture<EmailBatchResult>> futures = submitBatches(batchSpecs);
            log.debug("runnerId={}, batchesSubmitted={}, targets={}", runnerId, batchSpecs.size(), emailSendTargets.size());

            // 3. Wait for all batches to complete and aggregate summary
            EmailBatchRunSummary summary = waitForCompletion(futures);

            long totalTime = System.currentTimeMillis() - start;
            log.info("runnerId={}, event=all_batches_completed, totalTimeMs={}", runnerId, totalTime);

            return summary;
        } finally {
            emailSendEngine.unregisterRunner(runnerId);
        }
    }

    /**
     * Groups target data by domain and splits them according to each domain's
     * sendCountPerSession policy to create a list of submission requests (EmailBatchSpec).
     *
     * @param emailSendTargets converted SmtpRequest list
     * @return generated list of submission requests
     */
    private List<EmailBatchSpec> createBatches(List<EmailSendTarget> emailSendTargets) {
        EmailBatchPlanner planner = new EmailBatchPlanner(runnerId, emailSendTargets, emailDomainManager, emailSendContext, resultWriter);
        return planner.plan();
    }

    /**
     * Submits generated requests to the engine and returns a list of CompletableFutures.
     *
     * @param batchSpecs list of requests to submit
     * @return list of CompletableFutures containing each batch's processing result
     */
    private List<CompletableFuture<EmailBatchResult>> submitBatches(List<EmailBatchSpec> batchSpecs) {
        List<CompletableFuture<EmailBatchResult>> futures = new ArrayList<>();
        for (EmailBatchSpec request : batchSpecs) {
            CompletableFuture<EmailBatchResult> future = emailSendEngine.submitBatchAsync(request);
            futures.add(future);
        }
        return futures;
    }

    /**
     * Waits until all submitted batches complete.
     * Joins each batch's future sequentially to aggregate results.
     *
     * @param futures list of CompletableFutures to track
     */
    private EmailBatchRunSummary waitForCompletion(List<CompletableFuture<EmailBatchResult>> futures) {
        EmailBatchAggregator aggregator = new EmailBatchAggregator(runnerId, TOP_DOMAIN_SUMMARY_LIMIT);
        return aggregator.aggregate(futures, COMPLETION_TIMEOUT_MS);
    }

}
