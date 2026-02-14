package io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp;

import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.routing.RoutingService;

import java.util.Objects;

/**
 * SMTP session manager factory.
 */
public class SmtpSessionManagerFactory {
    private final EmailConfig emailConfig;
    private final EmailDomainManager emailDomainManager;
    private final RoutingService routingService;

    public SmtpSessionManagerFactory(EmailConfig emailConfig, EmailDomainManager emailDomainManager, RoutingService routingService) {
        this.emailConfig = Objects.requireNonNull(emailConfig, "emailConfig must not be null");
        this.emailDomainManager = Objects.requireNonNull(emailDomainManager, "emailDomainManager must not be null");
        this.routingService = Objects.requireNonNull(routingService, "routingService must not be null");
    }

    public SmtpSessionManager create() {
        return new SmtpSessionManager(emailConfig, emailDomainManager, routingService);
    }
}
