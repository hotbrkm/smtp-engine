package io.github.hotbrkm.smtpengine.agent.email.send.entry;

import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomain;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.EmailSendEngine;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.EmailSendEngineFactory;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchRunSummary;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpSessionManagerFactory;
import io.github.hotbrkm.smtpengine.agent.email.send.worker.EmailBatchSenderFactory;
import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailSendRunner bulk test (simulator)")
class EmailSendRunnerSimulatorBulkTest {

    private static final int BULK_TARGET_COUNT = 1_000;
    private static final int TEST_SCHEDULER_INTERVAL_MS = 100;
    private static final int TEST_MAX_RETRY_COUNT = 3;
    private static final long TEST_INITIAL_RETRY_DELAY_MS = 200L;
    private static final long TEST_MAX_RETRY_DELAY_MS = 1_000L;
    private static final double TEST_RETRY_BACKOFF_MULTIPLIER = 1.5d;

    @TempDir
    Path spoolDir;

    @DisplayName("Send bulk targets (1000 recipients) successfully via simulator")
    @Test
    void testExecuteSuccessBulkTargets1000() throws Exception {
        Path inboxDir = spoolDir.resolve("simulator-inbox-bulk-1000");
        try (EmbeddedSimulatorServer simulator = EmbeddedSimulatorServer.startDefault(inboxDir)) {
            EmailDomainManager emailDomainManager = EmailSendRunnerTestHelper.getEmailDomainManager();
            EmailConfig emailConfig = createSimulatorConfig(simulator.serverAddress());
            SmtpSessionManagerFactory factory = EmailSendTestFactorySupport.createDefaultFactory(emailConfig);

            EmailSendEngine engine = createEngine(factory, emailConfig);
            engine.start();

            try {
                Map<String, Object> message = EmailSendRunnerTestHelper.getMessage(123456789012345L);
                Map<String, Object> sendRequest = EmailSendRunnerTestHelper.getSendRequest(123456789012345L, 1234567890123456L, 1);
                EmailSendContext context = EmailSendRunnerTestHelper.getContext(message, sendRequest);

                List<Map<String, Object>> targets = new ArrayList<>(BULK_TARGET_COUNT);
                for (int i = 1; i <= BULK_TARGET_COUNT; i++) {
                    targets.add(EmailSendRunnerTestHelper.getTarget("bulk-" + i + "@example.com", "BulkUser" + i));
                }

                EmailSendRunner runner = new EmailSendRunner(EmailSendRunnerTestHelper.toTargets(targets), context, emailDomainManager, engine);
                EmailBatchRunSummary summary = runner.execute();

                assertThat(summary.totalBatches()).isGreaterThanOrEqualTo(1);
                assertThat(summary.successBatches()).isEqualTo(summary.totalBatches());
                assertThat(summary.failedBatches()).isZero();

                int totalSuccess = summary.domainStatsView().values().stream()
                        .mapToInt(EmailBatchRunSummary.DomainStats::success)
                        .sum();
                assertThat(totalSuccess).isEqualTo(BULK_TARGET_COUNT);

                for (Map<String, Object> target : targets) {
                    assertThat(target.get(TargetKey.SEND_STATUS)).isEqualTo("SUCCESS");
                    assertThat(target.get(TargetKey.SEND_CODE)).isEqualTo("250");
                    assertThat(target.get(TargetKey.ERROR_MESSAGE)).isNull();
                }

                assertThat(simulator.messageCount()).isEqualTo(BULK_TARGET_COUNT);
            } finally {
                engine.shutdown();
            }
        }
    }

    private EmailConfig createSimulatorConfig(String simulatorServerAddress) {
        EmailConfig emailConfig = EmailSendTestFactorySupport.createEmailSimulatorConfig(spoolDir.toString());
        emailConfig.getSend().setSimulatorServer(simulatorServerAddress);
        emailConfig.getSend().setResultUploadDir(spoolDir.resolve("result-upload").toString());
        return emailConfig;
    }

    private EmailSendEngine createEngine(SmtpSessionManagerFactory factory, EmailConfig emailConfig) {
        return EmailSendEngineFactory.create(4, new EmailBatchSenderFactory(emailConfig, factory),
                new EmailDomainManager(List.of(new EmailDomain("default", 2, 10, 60, 60, "")), LocalDateTime.now()),
                emailConfig.getSend(), TEST_SCHEDULER_INTERVAL_MS, TEST_MAX_RETRY_COUNT, TEST_INITIAL_RETRY_DELAY_MS,
                TEST_MAX_RETRY_DELAY_MS, TEST_RETRY_BACKOFF_MULTIPLIER);
    }
}
