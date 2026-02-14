package io.github.hotbrkm.smtpengine.agent.email.send.worker;

import io.github.hotbrkm.smtpengine.agent.email.send.engine.metrics.DomainSendMetrics;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;
import io.github.hotbrkm.smtpengine.agent.email.send.planning.EmailBatchSpec;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchResult;
import io.github.hotbrkm.smtpengine.agent.email.send.result.SendResult;

import java.util.List;
import java.util.Objects;

/**
 * Class responsible for reflecting results for batches classified as INVALID domain.
 * <p>
 * Collects policies on what status code/message to mark in one place,
 * and performs actual reflection work through {@link ResultApplier}.
 * <p>
 * The Engine layer (EmailSendEngine) only knows "this batch is an INVALID domain",
 * and the specific send_code / send_status / error_message / file recording method
 * is the responsibility of this handler and the ResultApplier component.
 */
public class InvalidDomainResultHandler {

    /** SMTP status code to use for INVALID domain */
    private static final int INVALID_DOMAIN_STATUS_CODE = 800;
    /** Default error message to use for INVALID domain */
    private static final String INVALID_DOMAIN_MESSAGE = "800 Invalid RCPT address";

    private final DomainSendMetrics domainSendMetrics;

    public InvalidDomainResultHandler(DomainSendMetrics domainSendMetrics) {
        this.domainSendMetrics = Objects.requireNonNull(domainSendMetrics, "domainSendMetrics must not be null");
    }

    /**
     * Processes an INVALID domain batch and reflects failure results to each target.
     *
     * @param spec INVALID domain batch spec
     * @return INVALID domain batch processing result
     */
    public EmailBatchResult handle(EmailBatchSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");

        List<EmailSendTarget> targets = spec.getEmailSendTargetList();
        int totalTargets = (targets == null) ? 0 : targets.size();

        // In normal flow, EMPTY batches do not enter the INVALID branch,
        // but defensively handle it and return failure result.
        if (totalTargets == 0) {
            return EmailBatchResult.failure(spec.getBatchId(), spec.getDomain(), 0,
                new IllegalArgumentException("Empty INVALID domain batch"));
        }

        ResultApplier applier = new ResultApplier(spec.getRunnerId(), spec.getResultWriter(), domainSendMetrics);
        for (EmailSendTarget target : targets) {
            applier.apply(target, SendResult.failure(INVALID_DOMAIN_STATUS_CODE, INVALID_DOMAIN_MESSAGE));
        }

        // INVALID domain batches are all marked as failures, but from the engine perspective,
        // "batch processing is completed without exception" is considered.
        return EmailBatchResult.success(spec.getBatchId(), spec.getDomain(), totalTargets, 0);
    }
}
