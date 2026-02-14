package io.github.hotbrkm.smtpengine.agent.email.send.entry;

import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomain;
import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.EmailSendEngine;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.EmailSendEngineFactory;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchRunSummary;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.dns.DnsClient;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.routing.RoutingService;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpCommand;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpCommandResponse;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpSessionManager;
import io.github.hotbrkm.smtpengine.agent.email.send.worker.EmailBatchSenderFactory;
import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailSendEngine Bind IP policy test")
class EmailSendEngineBindIpPolicyTest {

    @TempDir
    Path spoolDir;

    @Test
    @DisplayName("Should end with 705 code when bind IP slot wait continues only")
    void testBindIpAllocationTimeoutEndsWith705() {
        EmailConfig emailConfig = EmailSendTestFactorySupport.createEmailSimulatorConfig(spoolDir.toString());
        emailConfig.getSend().setBindAddresses(List.of("127.0.0.1"));
        emailConfig.getSend().setBindIpAssignWaitTimeoutMs(100L);
        emailConfig.getSend().setRetryExcludeCode(List.of("250", "550", "452", "552", "553", "610", "602", "705", "800", "888"));

        EmailDomainManager emailDomainManager = new EmailDomainManager(
                List.of(new EmailDomain("example.com", 1, 1, 60, 60, "")),
                LocalDateTime.now()
        );
        EmailSendEngine engine = EmailSendEngineFactory.create(2,
                new EmailBatchSenderFactory(
                        emailConfig, EmailSendRunnerTestStubs.stubFactory(emailConfig, () -> new SlowStubSmtpSessionManager(emailConfig))
                ),
                emailDomainManager, emailConfig.getSend(),
                20, 0, 50L, 100L, 1.5d);
        engine.start();

        try {
            Map<String, Object> message = EmailSendRunnerTestHelper.getMessage(123456789012345L);
            Map<String, Object> sendRequest = EmailSendRunnerTestHelper.getSendRequest(123456789012345L, 1234567890123456L, 1);
            EmailSendContext context = EmailSendRunnerTestHelper.getContext(message, sendRequest);

            Map<String, Object> target1 = EmailSendRunnerTestHelper.getTarget("slot-1@example.com", "SlotUser1");
            Map<String, Object> target2 = EmailSendRunnerTestHelper.getTarget("slot-2@example.com", "SlotUser2");

            EmailSendRunner runner = new EmailSendRunner(EmailSendRunnerTestHelper.toTargets(target1, target2), context,
                    emailDomainManager, engine);

            EmailBatchRunSummary summary = runner.execute();

            assertThat(summary.totalBatches()).isEqualTo(2);
            assertThat(summary.successBatches()).isEqualTo(2);
            assertThat(summary.failedBatches()).isZero();

            long successCount = Stream.of(target1, target2)
                    .filter(target -> "SUCCESS".equals(target.get(TargetKey.SEND_STATUS)))
                    .count();
            long timeoutCount = Stream.of(target1, target2)
                    .filter(target -> "705".equals(target.get(TargetKey.SEND_CODE)))
                    .count();

            assertThat(successCount).isEqualTo(1L);
            assertThat(timeoutCount).isEqualTo(1L);
        } finally {
            engine.shutdown();
        }
    }

    /**
     * Delays the first batch to hold bind IP lease long enough.
     */
    static class SlowStubSmtpSessionManager extends SmtpSessionManager {
        private boolean valid;

        SlowStubSmtpSessionManager(EmailConfig config) {
            super(config, new EmailDomainManager(Collections.emptyList(), LocalDateTime.now()),
                    new RoutingService(config, new DnsClient(List.of("127.0.0.1"))));
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
            try {
                Thread.sleep(400L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new SmtpCommandResponse(SmtpCommand.DATA_END, List.of("250 Message accepted"));
        }

        @Override
        public SmtpCommandResponse sendRset() {
            return new SmtpCommandResponse(SmtpCommand.RSET, List.of("250 OK"));
        }
    }
}
