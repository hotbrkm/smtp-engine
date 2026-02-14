package io.github.hotbrkm.smtpengine.agent.email.send.entry;

import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.dns.DnsClient;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.routing.RoutingService;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpSessionManagerFactory;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpCommand;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpCommandResponse;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpSessionManager;

import java.util.Collections;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

/**
 * Collection of stub/fake implementations used in EmailSendRunnerTest.
 */
final class EmailSendRunnerTestStubs {

    private EmailSendRunnerTestStubs() {
    }

    static SmtpSessionManagerFactory stubFactory(EmailConfig emailConfig, Supplier<SmtpSessionManager> supplier) {
        List<String> dnsServers = List.of("127.0.0.1");
        if (emailConfig != null && emailConfig.getSend() != null
                && emailConfig.getSend().getDnsServer() != null
                && !emailConfig.getSend().getDnsServer().isEmpty()) {
            dnsServers = emailConfig.getSend().getDnsServer();
        }

        EmailConfig factoryConfig = new EmailConfig();
        factoryConfig.getSend().setDnsServer(dnsServers);
        return new SmtpSessionManagerFactory(
                factoryConfig,
                new EmailDomainManager(Collections.emptyList(), LocalDateTime.now()),
                new RoutingService(factoryConfig)
        ) {
            @Override
            public SmtpSessionManager create() {
                return supplier.get();
            }
        };
    }

    /**
     * Stub implementation that simulates SMTP calls as success
     */
    static class StubSmtpSessionManager extends SmtpSessionManager {
        private boolean valid;

        StubSmtpSessionManager() {
            super(minimalConfig(), null, new RoutingService(minimalConfig(), new DnsClient(List.of("127.0.0.1"))));
        }

        @Override
        public void openSession(String domain, String bindIp) {
            valid = true;
        }

        @Override
        public void closeSession() {
            valid = false;
        }

        @Override
        public boolean isSessionValid() {
            return valid;
        }

        @Override
        public SmtpCommandResponse sendMailFrom(String mailFrom) {
            return new SmtpCommandResponse(SmtpCommand.MAIL_FROM, List.of("250 OK"));
        }

        @Override
        public SmtpCommandResponse sendRcptTo(String rcptTo) {
            return new SmtpCommandResponse(SmtpCommand.RCPT_TO, List.of("250 OK"));
        }

        @Override
        public SmtpCommandResponse sendData() {
            return new SmtpCommandResponse(SmtpCommand.DATA, List.of("354 Start mail input"));
        }

        @Override
        public SmtpCommandResponse sendMessage(String message, String domain) {
            return new SmtpCommandResponse(SmtpCommand.DATA_END, List.of("250 Message accepted"));
        }

        @Override
        public SmtpCommandResponse sendRset() {
            return new SmtpCommandResponse(SmtpCommand.RSET, List.of("250 OK"));
        }

        static EmailConfig minimalConfig() {
            EmailConfig cfg = new EmailConfig();
            cfg.getSmtp().setHelo("localhost");
            cfg.getSmtp().setFrom("no-reply@example.com");
            // Minimum Send field settings to prevent NPE
            EmailConfig.Send send = cfg.getSend();
            send.setBindAddresses(List.of("127.0.0.1"));
            send.setDnsServer(List.of());
            send.setExcludedDomain(List.of());
            send.setFixedIpOfDomain(new HashMap<>());
            send.setTlsEnabledProtocols(List.of("TLSv1.2"));
            send.setTlsApplyScope("NONE");
            send.setTlsApplyDomain(List.of());
            return cfg;
        }
    }
}
