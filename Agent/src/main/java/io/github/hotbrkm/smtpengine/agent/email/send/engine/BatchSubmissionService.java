package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.domain.EmailAddressUtil;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.ExecutionMode;
import io.github.hotbrkm.smtpengine.agent.email.send.planning.EmailBatchSpec;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchResult;
import io.github.hotbrkm.smtpengine.agent.email.send.worker.InvalidDomainResultHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DispatchLane;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchQueue;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchTask;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.metrics.DomainSendMetrics;

/**
 * Service that validates batch submission requests and connects them to queue registration.
 */
@Slf4j
final class BatchSubmissionService {

    private final InvalidDomainResultHandler invalidDomainResultHandler;
    private final RunnerExecutionGuard runnerExecutionGuard;
    private final DomainBatchQueue batchQueue;

    BatchSubmissionService(RunnerExecutionGuard runnerExecutionGuard, DomainBatchQueue batchQueue, DomainSendMetrics domainSendMetrics) {
        this.runnerExecutionGuard = runnerExecutionGuard;
        this.batchQueue = batchQueue;
        this.invalidDomainResultHandler = new InvalidDomainResultHandler(domainSendMetrics);
    }

    /**
     * Validates the externally provided batch spec and returns queue registration or immediate failure result.
     */
    CompletableFuture<EmailBatchResult> submitBatchAsync(EmailBatchSpec emailBatchSpec) {
        try {
            Objects.requireNonNull(emailBatchSpec, "emailBatchSpec must not be null");
            List<EmailSendTarget> emailSendTargetList = emailBatchSpec.getEmailSendTargetList();

            if (emailSendTargetList == null || emailSendTargetList.isEmpty()) {
                log.warn("Empty DTO batch submitted for domain: {}, skipping", emailBatchSpec.getDomain());
                return CompletableFuture.completedFuture(EmailBatchResult.failure(emailBatchSpec.getBatchId(),
                        emailBatchSpec.getDomain(), 0, new IllegalArgumentException("Empty batch")));
            }

            if (EmailAddressUtil.INVALID.equalsIgnoreCase(emailBatchSpec.getDomain())) {
                // INVALID domains are not queued; returns an invalid result immediately.
                log.info("Batch [{}] submitted with INVALID domain. Marking {} targets as invalid.",
                        emailBatchSpec.getBatchId(), emailSendTargetList.size());
                EmailBatchResult invalidResult = invalidDomainResultHandler.handle(emailBatchSpec);
                return CompletableFuture.completedFuture(invalidResult);
            }

            return submitRequestsInternal(emailBatchSpec);
        } catch (Exception e) {
            String batchId = emailBatchSpec != null ? emailBatchSpec.getBatchId() : "unknown";
            String domain = emailBatchSpec != null ? emailBatchSpec.getDomain() : "unknown";
            int totalTargets = (emailBatchSpec != null && emailBatchSpec.getEmailSendTargetList() != null)
                    ? emailBatchSpec.getEmailSendTargetList().size() : 0;
            log.warn("Batch [{}] rejected before queueing. domain={}, reason={}", batchId, domain, e.getMessage(), e);
            return CompletableFuture.completedFuture(EmailBatchResult.failure(batchId, domain, totalTargets, e));
        }
    }

    /**
     * Registers the validated batch to the queue according to runtime lane rules.
     */
    CompletableFuture<EmailBatchResult> submitRequestsInternal(EmailBatchSpec emailBatchSpec) {
        CompletableFuture<EmailBatchResult> resultFuture = new CompletableFuture<>();
        ExecutionMode executionMode = resolveExecutionMode(emailBatchSpec);
        DispatchLane lane = DispatchLane.forFresh(executionMode);
        if (emailBatchSpec.getRunnerId() != null && !emailBatchSpec.getRunnerId().isBlank() && emailBatchSpec.getResultWriter() != null) {
            runnerExecutionGuard.trackRunnerTokenIfAbsent(emailBatchSpec.getRunnerId(), emailBatchSpec.getResultWriter());
        }
        DomainBatchTask domainBatchTask = new DomainBatchTask(emailBatchSpec.getEmailSendTargetList(), emailBatchSpec.getDomain(), emailBatchSpec.getBatchId(),
                resultFuture, 0, emailBatchSpec.getRunnerId(), emailBatchSpec.getResultWriter(), emailBatchSpec.getEmailSendContext(), executionMode, lane);
        batchQueue.offer(domainBatchTask);
        log.info("Batch [{}] submitted for domain: {} with {} targets",
                emailBatchSpec.getBatchId(), emailBatchSpec.getDomain(), emailBatchSpec.getEmailSendTargetList().size());
        return resultFuture;
    }

    /**
     * Resolves execution mode (REALTIME/BATCH) and throws an exception if essential values are missing.
     */
    private ExecutionMode resolveExecutionMode(EmailBatchSpec emailBatchSpec) {
        if (emailBatchSpec == null) {
            throw new IllegalArgumentException("EmailBatchSpec must not be null");
        }
        if (emailBatchSpec.getEmailSendContext() == null || emailBatchSpec.getEmailSendContext().executionMode() == null) {
            throw new IllegalArgumentException(String.format(
                    "Missing execution_mode. batch_id=%s, domain=%s, runner_id=%s",
                    emailBatchSpec.getBatchId(), emailBatchSpec.getDomain(), emailBatchSpec.getRunnerId()
            ));
        }
        return emailBatchSpec.getEmailSendContext().executionMode();
    }
}
