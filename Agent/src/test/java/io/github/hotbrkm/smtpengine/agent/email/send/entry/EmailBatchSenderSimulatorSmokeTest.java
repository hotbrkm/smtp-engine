package io.github.hotbrkm.smtpengine.agent.email.send.entry;

import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import io.github.hotbrkm.smtpengine.agent.email.mime.EmailMimeComposer;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.routing.RoutingService;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpCommandResponse;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpSessionManager;
import io.github.hotbrkm.smtpengine.agent.email.send.worker.EmailBatchSender;
import io.github.hotbrkm.smtpengine.agent.email.send.worker.ResultApplier;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.metrics.DomainSendMetrics;
import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailBatchSender smoke test (Embedded Simulator)")
class EmailBatchSenderSimulatorSmokeTest {

    @TempDir
    Path spoolDir;

    @DisplayName("Send email successfully via real SMTP session path and clean up with QUIT/closeSession")
    @Test
    void callShouldSendMailAndCloseSessionWithQuit() throws Exception {
        Path inboxDir = spoolDir.resolve("simulator-inbox-sender-smoke");

        try (EmbeddedSimulatorServer simulator = EmbeddedSimulatorServer.startDefault(inboxDir)) {
            EmailConfig emailConfig = EmailSendTestFactorySupport.createEmailSimulatorConfig(spoolDir.toString());
            emailConfig.getSend().setSimulatorServer(simulator.serverAddress());

            EmailDomainManager emailDomainManager = new EmailDomainManager(List.of(), LocalDateTime.now());
            RoutingService routingService = new RoutingService(emailConfig);
            TrackingSmtpSessionManager smtpSessionManager =
                    new TrackingSmtpSessionManager(emailConfig, emailDomainManager, routingService);

            EmailSendContext emailSendContext = new EmailSendContext(
                    1001L, 2001L, 1, "INFO", "GENERAL", ExecutionMode.BATCH
            );
            EmailMimeComposer emailMimeComposer = new EmailMimeComposer(emailConfig, emailSendContext, spoolDir.toString());
            DomainSendMetrics domainSendMetrics = new DomainSendMetrics(5, 60);
            ResultApplier resultApplier = new ResultApplier(null, null, domainSendMetrics);

            EmailSendTarget target = EmailSendTarget.builder()
                    .targetId("sim-user-1")
                    .targetName("Simulator User")
                    .targetEmail("sim-user@example.com")
                    .senderName("Simulator Sender")
                    .senderEmail("sender@example.com")
                    .title("Simulator Smoke")
                    .body("<p>smoke body</p>")
                    .listSeq(1)
                    .retryCount(0)
                    .sendCode("701")
                    .sendStatus("PENDING")
                    .targetData(new HashMap<>())
                    .build();

            EmailBatchSender sender = new EmailBatchSender(
                    List.of(target),
                    "example.com",
                    "127.0.0.1",
                    smtpSessionManager,
                    emailMimeComposer,
                    resultApplier,
                    List.of(),
                    domainSendMetrics
            );

            int successCount = sender.call();

            assertThat(successCount).isEqualTo(1);
            assertThat(target.getSendStatus()).isEqualTo("SUCCESS");
            assertThat(target.getSendCode()).isEqualTo("250");
            assertThat(simulator.countMessagesForRecipient("sim-user@example.com")).isEqualTo(1);
            assertThat(smtpSessionManager.quitCalled).isTrue();
            assertThat(smtpSessionManager.closeCalled).isTrue();
        }
    }

    private static class TrackingSmtpSessionManager extends SmtpSessionManager {
        private boolean quitCalled = false;
        private boolean closeCalled = false;

        private TrackingSmtpSessionManager(EmailConfig emailConfig,
                                           EmailDomainManager emailDomainManager,
                                           RoutingService routingService) {
            super(emailConfig, emailDomainManager, routingService);
        }

        @Override
        public SmtpCommandResponse sendQuit() {
            quitCalled = true;
            return super.sendQuit();
        }

        @Override
        public void closeSession() {
            closeCalled = true;
            super.closeSession();
        }
    }
}

