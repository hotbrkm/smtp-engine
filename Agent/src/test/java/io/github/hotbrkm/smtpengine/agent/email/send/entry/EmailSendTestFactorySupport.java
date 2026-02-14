package io.github.hotbrkm.smtpengine.agent.email.send.entry;

import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.routing.RoutingService;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpSessionManagerFactory;
import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

final class EmailSendTestFactorySupport {

    private EmailSendTestFactorySupport() {
    }

    static SmtpSessionManagerFactory createDefaultFactory(EmailConfig emailConfig) {
        EmailDomainManager emailDomainManager = new EmailDomainManager(Collections.emptyList(), LocalDateTime.now());
        RoutingService routingService = new RoutingService(emailConfig);
        return new SmtpSessionManagerFactory(emailConfig, emailDomainManager, routingService);
    }

    static EmailConfig createEmailSimulatorConfig(String spoolDir) {
        EmailConfig emailConfig = new EmailConfig();

        EmailConfig.Smtp smtp = new EmailConfig.Smtp();
        smtp.setHelo("localhost");
        smtp.setFrom("noreply@example.com");
        emailConfig.setSmtp(smtp);

        EmailConfig.Send send = new EmailConfig.Send();
        send.setBindAddresses(List.of("127.0.0.1"));
        send.setDnsServer(Arrays.asList("8.8.8.8", "8.8.4.4"));
        send.setExcludedDomain(new ArrayList<>());
        send.setFixedIpOfDomain(new HashMap<>());
        send.setTlsEnabledProtocols(Arrays.asList("TLSv1.2", "TLSv1.3"));
        send.setTlsApplyScope("NONE");
        send.setTlsApplyDomain(new ArrayList<>());
        send.setTlsMaxAttempts(2);
        send.setTlsRetryDelay(1000L);
        send.setSimulatorEnabled(true);
        send.setSimulatorServer("127.0.0.1:2525");
        send.setRetryExcludeCode(Arrays.asList("250,550,452,552,553,610,602,705,800,888".split(",")));
        send.setSpoolDir(spoolDir);
        emailConfig.setSend(send);
        return emailConfig;
    }
}
