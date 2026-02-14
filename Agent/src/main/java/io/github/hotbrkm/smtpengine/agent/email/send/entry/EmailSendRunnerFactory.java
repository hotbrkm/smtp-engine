package io.github.hotbrkm.smtpengine.agent.email.send.entry;

import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.EmailSendEngine;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchResultWriter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * EmailSendRunner factory.
 * <p>
 * Encapsulates EmailSendRunner's dependencies (EmailDomainManager, EmailSendEngine, EmailConfig)
 * so callers don't need to know about them.
 */
@RequiredArgsConstructor
public class EmailSendRunnerFactory {

    private final EmailDomainManager emailDomainManager;
    private final EmailSendEngine emailSendEngine;
    private final EmailConfig emailConfig;

    public EmailSendRunner create(List<EmailSendTarget> emailSendTargets, EmailSendContext emailSendContext, EmailBatchResultWriter resultWriter) {
        return new EmailSendRunner(emailSendTargets, emailSendContext, emailDomainManager, emailSendEngine, resultWriter);
    }
}
