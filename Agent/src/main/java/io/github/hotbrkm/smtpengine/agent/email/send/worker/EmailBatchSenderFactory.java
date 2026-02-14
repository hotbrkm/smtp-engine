package io.github.hotbrkm.smtpengine.agent.email.send.worker;

import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import io.github.hotbrkm.smtpengine.agent.email.mime.EmailMimeComposer;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchTask;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.metrics.DomainSendMetrics;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendContext;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpSessionManager;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpSessionManagerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Default {@link EmailBatchSenderFactory} implementation.
 * <p>
 * Assembles infrastructure objects needed by {@link EmailBatchSender} in one place.
 */
public class EmailBatchSenderFactory {

    private final EmailConfig emailConfig;
    private final SmtpSessionManagerFactory smtpSessionManagerFactory;
    private volatile DomainSendMetrics domainSendMetrics;

    public EmailBatchSenderFactory(EmailConfig emailConfig, SmtpSessionManagerFactory smtpSessionManagerFactory) {
        this.emailConfig = emailConfig;
        this.smtpSessionManagerFactory = smtpSessionManagerFactory;
    }

    /**
     * Injects DomainSendMetrics during engine assembly.
     * Should be called once before engine starts.
     */
    public void setDomainSendMetrics(DomainSendMetrics domainSendMetrics) {
        this.domainSendMetrics = Objects.requireNonNull(domainSendMetrics, "domainSendMetrics must not be null");
    }

    public EmailBatchSender create(DomainBatchTask domainBatchTask, String bindIp) {
        Objects.requireNonNull(domainBatchTask, "domainBatchTask must not be null");
        Objects.requireNonNull(bindIp, "bindIp must not be null");

        SmtpSessionManager smtpSessionManager = smtpSessionManagerFactory.create();

        EmailSendContext emailSendContext = domainBatchTask.getEmailSendContext();
        EmailMimeComposer emailMimeComposer = new EmailMimeComposer(emailConfig, emailSendContext, emailConfig.getSend().getSpoolDir());

        ResultApplier resultApplier = new ResultApplier(domainBatchTask.getRunnerId(), domainBatchTask.getResultWriter(), domainSendMetrics);
        List<String> retryExcludeCode = emailConfig.getSend().getRetryExcludeCode();

        return new EmailBatchSender(domainBatchTask.getBatch(), domainBatchTask.getDomain(), bindIp, smtpSessionManager,
                emailMimeComposer, resultApplier, retryExcludeCode, domainSendMetrics);
    }
}
