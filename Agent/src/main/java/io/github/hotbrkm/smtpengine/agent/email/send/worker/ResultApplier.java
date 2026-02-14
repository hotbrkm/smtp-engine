package io.github.hotbrkm.smtpengine.agent.email.send.worker;

import io.github.hotbrkm.smtpengine.agent.email.send.engine.metrics.DomainSendMetrics;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchResultWriter;
import io.github.hotbrkm.smtpengine.agent.email.send.result.SendResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Default ResultApplier implementation that reflects results in targetData (Map).
 * - Result reflection: send_code, send_status, error_message, end_datetime, email_domain
 * - File recording: Add NDJSON line to EmailBatchResultWriter
 * - Cleanup: Remove message/mailBody/encodedBody/mime
 */
public class ResultApplier {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final String runnerId;
    private final EmailBatchResultWriter resultWriter;
    private final DomainSendMetrics domainSendMetrics;

    public ResultApplier(String runnerId, EmailBatchResultWriter resultWriter, DomainSendMetrics domainSendMetrics) {
        this.runnerId = runnerId;
        this.resultWriter = resultWriter;
        this.domainSendMetrics = Objects.requireNonNull(domainSendMetrics, "domainSendMetrics must not be null");
    }

    /**
     * Reflects the result. (send_code/send_status/error_message etc.)
     */
    public void apply(EmailSendTarget emailSendTarget, SendResult sendResult) {
        emailSendTarget.applySendResult(sendResult, LocalDateTime.now().format(DATETIME_FORMATTER), emailSendTarget.getDomain());

        if (runnerId != null && resultWriter != null) {
            resultWriter.writeResult(emailSendTarget.toProgress());
        }

        String domain = emailSendTarget.getDomain();
        int statusCode = sendResult.statusCode();
        domainSendMetrics.recordResultCode(domain, statusCode);
        if (sendResult.success()) {
            domainSendMetrics.recordSuccess(domain);
        } else {
            domainSendMetrics.recordFailure(domain, statusCode);
        }
    }

    /**
     * Performs post-processing such as cleaning up heavy derived fields.
     */
    public void cleanup(EmailSendTarget emailSendTarget) {
        emailSendTarget.removeAttribute("message");
        emailSendTarget.removeAttribute("mailBody");
        emailSendTarget.removeAttribute("encodedBody");
        emailSendTarget.removeAttribute("mime");
    }

    /**
     * Increments retry_count for retry targets.
     */
    public void incrementRetryCount(List<EmailSendTarget> emailSendTargets) {
        if (emailSendTargets == null) {
            return;
        }
        for (EmailSendTarget req : emailSendTargets) {
            req.incrementRetryCount();
        }
    }
}
