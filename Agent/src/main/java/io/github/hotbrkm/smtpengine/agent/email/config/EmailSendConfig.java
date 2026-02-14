package io.github.hotbrkm.smtpengine.agent.email.config;

import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.EmailSendEngine;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.EmailSendEngineFactory;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendRunnerFactory;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpSessionManagerFactory;
import io.github.hotbrkm.smtpengine.agent.email.send.worker.EmailBatchSenderFactory;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.routing.RoutingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.Collections;

@Configuration
public class EmailSendConfig {

    @Bean
    public EmailDomainManager emailDomainManager() {
        return new EmailDomainManager(Collections.emptyList(), LocalDateTime.now());
    }

    @Bean
    public RoutingService routingService(EmailConfig emailConfig) {
        return new RoutingService(emailConfig);
    }

    @Bean
    public SmtpSessionManagerFactory smtpSessionManagerFactory(EmailConfig emailConfig, EmailDomainManager emailDomainManager,
                                                               RoutingService routingService) {
        return new SmtpSessionManagerFactory(emailConfig, emailDomainManager, routingService);
    }

    @Bean
    public EmailBatchSenderFactory emailBatchSenderFactory(EmailConfig emailConfig, SmtpSessionManagerFactory smtpSessionManagerFactory) {
        return new EmailBatchSenderFactory(emailConfig, smtpSessionManagerFactory);
    }

    /**
     * EmailSendEngine Bean.
     * Calls start() on application startup and shutdown() on termination.
     */
    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public EmailSendEngine emailSendEngine(EmailConfig emailConfig, EmailBatchSenderFactory emailBatchSenderFactory,
                                           EmailDomainManager emailDomainManager) {
        return EmailSendEngineFactory.create(emailConfig.getSend(), emailDomainManager, emailBatchSenderFactory);
    }

    @Bean
    public EmailSendRunnerFactory emailSendRunnerFactory(EmailDomainManager emailDomainManager, EmailSendEngine emailSendEngine,
                                                         EmailConfig emailConfig) {
        return new EmailSendRunnerFactory(emailDomainManager, emailSendEngine, emailConfig);
    }
}
