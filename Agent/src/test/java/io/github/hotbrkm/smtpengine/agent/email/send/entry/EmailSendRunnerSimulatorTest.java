package io.github.hotbrkm.smtpengine.agent.email.send.entry;

import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomain;
import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.EmailSendEngine;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.EmailSendEngineFactory;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchRunSummary;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpSessionManagerFactory;
import io.github.hotbrkm.smtpengine.agent.email.send.worker.EmailBatchSenderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailSendRunner test (simulator)")
class EmailSendRunnerSimulatorTest {

    private static final int TEST_SCHEDULER_INTERVAL_MS = 100;
    private static final int TEST_MAX_RETRY_COUNT = 3;
    private static final long TEST_INITIAL_RETRY_DELAY_MS = 200L;
    private static final long TEST_MAX_RETRY_DELAY_MS = 1_000L;
    private static final double TEST_RETRY_BACKOFF_MULTIPLIER = 1.5d;

    @TempDir
    Path spoolDir;

    @DisplayName("Single target success with simulator")
    @Test
    void testExecuteSuccessSingleTarget() throws Exception {
        Path inboxDir = spoolDir.resolve("simulator-inbox-single");
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

                Map<String, Object> target = EmailSendRunnerTestHelper.getTarget("test@example.com", "SimulationUser");
                List<Map<String, Object>> targetDataList = EmailSendRunnerTestHelper.toList(target);

                EmailSendRunner runner = new EmailSendRunner(EmailSendRunnerTestHelper.toTargets(targetDataList), context, emailDomainManager, engine);

                EmailBatchRunSummary summary = runner.execute();

                assertThat(summary.totalBatches()).isEqualTo(1);
                assertThat(summary.successBatches()).isEqualTo(1);
                assertThat(summary.failedBatches()).isZero();

                assertThat(target.get(TargetKey.SEND_STATUS)).isEqualTo("SUCCESS");
                assertThat(target.get(TargetKey.SEND_CODE)).isEqualTo("250");
                assertThat(target.get(TargetKey.ERROR_MESSAGE)).isNull();

                assertThat(simulator.messageCount()).isEqualTo(1);
                assertThat(simulator.countMessagesForRecipient("test@example.com")).isEqualTo(1);
            } finally {
                engine.shutdown();
            }
        }
    }

    @DisplayName("Multiple targets (2) success with simulator")
    @Test
    void testExecuteSuccessMultipleTargets() throws Exception {
        Path inboxDir = spoolDir.resolve("simulator-inbox-multi-target");
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

                Map<String, Object> target1 = EmailSendRunnerTestHelper.getTarget("user1@example.com", "SimulationUser1");
                Map<String, Object> target2 = EmailSendRunnerTestHelper.getTarget("user2@example.com", "SimulationUser2");
                List<Map<String, Object>> targetDataList = EmailSendRunnerTestHelper.toList(target1, target2);

                EmailSendRunner runner = new EmailSendRunner(EmailSendRunnerTestHelper.toTargets(targetDataList), context, emailDomainManager, engine);

                EmailBatchRunSummary summary = runner.execute();

                assertThat(summary.totalBatches()).isEqualTo(1);
                assertThat(summary.successBatches()).isEqualTo(summary.totalBatches());
                assertThat(summary.failedBatches()).isZero();
                int totalSuccess = summary.domainStatsView().values().stream().mapToInt(EmailBatchRunSummary.DomainStats::success).sum();
                assertThat(totalSuccess).isEqualTo(2);

                assertThat(target1.get(TargetKey.SEND_STATUS)).isEqualTo("SUCCESS");
                assertThat(target1.get(TargetKey.SEND_CODE)).isEqualTo("250");
                assertThat(target2.get(TargetKey.SEND_STATUS)).isEqualTo("SUCCESS");
                assertThat(target2.get(TargetKey.SEND_CODE)).isEqualTo("250");

                assertThat(simulator.messageCount()).isEqualTo(2);
                assertThat(simulator.countMessagesForRecipient("user1@example.com")).isEqualTo(1);
                assertThat(simulator.countMessagesForRecipient("user2@example.com")).isEqualTo(1);
            } finally {
                engine.shutdown();
            }
        }
    }

    @DisplayName("Multiple domain targets (2) success with simulator")
    @Test
    void testExecuteSuccessMultipleDomains() throws Exception {
        Path inboxDir = spoolDir.resolve("simulator-inbox-multi-domain");
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

                Map<String, Object> target1 = EmailSendRunnerTestHelper.getTarget("user1@example.com", "SimulationUser1");
                Map<String, Object> target2 = EmailSendRunnerTestHelper.getTarget("user2@example2.com", "SimulationUser2");
                List<Map<String, Object>> targetDataList = EmailSendRunnerTestHelper.toList(target1, target2);

                EmailSendRunner runner = new EmailSendRunner(EmailSendRunnerTestHelper.toTargets(targetDataList), context, emailDomainManager, engine);

                EmailBatchRunSummary summary = runner.execute();

                assertThat(summary.totalBatches()).isEqualTo(2);
                assertThat(summary.successBatches()).isEqualTo(summary.totalBatches());
                assertThat(summary.failedBatches()).isZero();
                int totalSuccess = summary.domainStatsView().values().stream().mapToInt(EmailBatchRunSummary.DomainStats::success).sum();
                assertThat(totalSuccess).isEqualTo(2);

                assertThat(target1.get(TargetKey.SEND_STATUS)).isEqualTo("SUCCESS");
                assertThat(target1.get(TargetKey.SEND_CODE)).isEqualTo("250");
                assertThat(target2.get(TargetKey.SEND_STATUS)).isEqualTo("SUCCESS");
                assertThat(target2.get(TargetKey.SEND_CODE)).isEqualTo("250");

                assertThat(simulator.messageCount()).isEqualTo(2);
                assertThat(simulator.countMessagesForRecipient("user1@example.com")).isEqualTo(1);
                assertThat(simulator.countMessagesForRecipient("user2@example2.com")).isEqualTo(1);
            } finally {
                engine.shutdown();
            }
        }
    }

    @DisplayName("Greylisting causes partial retry and final success")
    @Test
    void testExecuteGreylistingRetryForTwoTargets() throws Exception {
        Path inboxDir = spoolDir.resolve("simulator-inbox-greylisting");
        try (EmbeddedSimulatorServer simulator = EmbeddedSimulatorServer.startWithGreylisting( inboxDir, List.of("whitelist@example.com"))) {
            EmailDomainManager emailDomainManager = EmailSendRunnerTestHelper.getEmailDomainManager();
            EmailConfig emailConfig = createSimulatorConfig(simulator.serverAddress());
            SmtpSessionManagerFactory factory = EmailSendTestFactorySupport.createDefaultFactory(emailConfig);

            EmailSendEngine engine = createEngine(factory, emailConfig);
            engine.start();

            try {
                Map<String, Object> message = EmailSendRunnerTestHelper.getMessage(123456789012345L);
                Map<String, Object> sendRequest = EmailSendRunnerTestHelper.getSendRequest(123456789012345L, 1234567890123456L, 1);
                EmailSendContext context = EmailSendRunnerTestHelper.getContext(message, sendRequest);

                Map<String, Object> greylistedTarget = EmailSendRunnerTestHelper.getTarget("grey@example.com", "GreyUser");
                Map<String, Object> whitelistedTarget = EmailSendRunnerTestHelper.getTarget("whitelist@example.com", "WhiteUser");
                List<Map<String, Object>> firstBatchTargets = EmailSendRunnerTestHelper.toList(greylistedTarget, whitelistedTarget);

                EmailSendRunner runner = new EmailSendRunner(EmailSendRunnerTestHelper.toTargets(firstBatchTargets), context, emailDomainManager, engine);

                EmailBatchRunSummary summary = runner.execute();

                assertThat(summary.totalBatches()).isGreaterThanOrEqualTo(1);
                assertThat(summary.successBatches()).isEqualTo(summary.totalBatches());
                assertThat(summary.failedBatches()).isZero();

                assertThat(whitelistedTarget.get(TargetKey.SEND_STATUS)).isEqualTo("SUCCESS");
                assertThat(whitelistedTarget.get(TargetKey.SEND_CODE)).isEqualTo("250");
                assertThat(whitelistedTarget.get(TargetKey.RETRY_COUNT)).isEqualTo(0);

                assertThat(greylistedTarget.get(TargetKey.SEND_STATUS)).isEqualTo("SUCCESS");
                assertThat(greylistedTarget.get(TargetKey.SEND_CODE)).isEqualTo("250");
                assertThat(greylistedTarget.get(TargetKey.RETRY_COUNT)).isEqualTo(1);

                assertThat(simulator.messageCount()).isEqualTo(2);
                assertThat(simulator.countMessagesForRecipient("grey@example.com")).isEqualTo(1);
                assertThat(simulator.countMessagesForRecipient("whitelist@example.com")).isEqualTo(1);
            } finally {
                engine.shutdown();
            }
        }
    }

    @DisplayName("Connection fails immediately when simulator is not running")
    @Test
    void testExecuteFailureWhenSimulatorIsUnavailable() throws Exception {
        EmailDomainManager emailDomainManager = EmailSendRunnerTestHelper.getEmailDomainManager();
        EmailConfig emailConfig = createSimulatorConfig(EmbeddedSimulatorServer.unusedServerAddress());
        SmtpSessionManagerFactory factory = EmailSendTestFactorySupport.createDefaultFactory(emailConfig);

        EmailSendEngine engine = createEngine(factory, emailConfig);
        engine.start();

        try {
            Map<String, Object> message = EmailSendRunnerTestHelper.getMessage(123456789012345L);
            Map<String, Object> sendRequest = EmailSendRunnerTestHelper.getSendRequest(123456789012345L, 1234567890123456L, 1);
            EmailSendContext context = EmailSendRunnerTestHelper.getContext(message, sendRequest);

            Map<String, Object> target = EmailSendRunnerTestHelper.getTarget("down@example.com", "DownUser");
            EmailSendRunner runner = new EmailSendRunner(EmailSendRunnerTestHelper.toTargets(target), context, emailDomainManager, engine);

            EmailBatchRunSummary summary = runner.execute();

            assertThat(summary.totalBatches()).isEqualTo(1);
            assertThat(summary.successBatches()).isEqualTo(1);
            assertThat(summary.failedBatches()).isZero();

            assertThat(target.get(TargetKey.SEND_STATUS)).isEqualTo("FAILURE");
            assertThat((String) target.get(TargetKey.SEND_CODE)).matches("60[12]|70[34]|888");
            assertThat(target.get(TargetKey.RETRY_COUNT)).isEqualTo(0);
        } finally {
            engine.shutdown();
        }
    }

    @DisplayName("Long greylisting minimum delay causes retry exhaustion failure")
    @Test
    void testExecuteGreylistingRetryExhausted() throws Exception {
        Path inboxDir = spoolDir.resolve("simulator-inbox-greylisting-exhausted");
        try (EmbeddedSimulatorServer simulator = EmbeddedSimulatorServer.startWithGreylisting( inboxDir, List.of(), Duration.ofSeconds(10))) {
            EmailDomainManager emailDomainManager = EmailSendRunnerTestHelper.getEmailDomainManager();
            EmailConfig emailConfig = createSimulatorConfig(simulator.serverAddress());
            SmtpSessionManagerFactory factory = EmailSendTestFactorySupport.createDefaultFactory(emailConfig);

            EmailSendEngine engine = createEngine(factory, emailConfig);
            engine.start();

            try {
                Map<String, Object> message = EmailSendRunnerTestHelper.getMessage(123456789012345L);
                Map<String, Object> sendRequest = EmailSendRunnerTestHelper.getSendRequest(123456789012345L, 1234567890123456L, 1);
                EmailSendContext context = EmailSendRunnerTestHelper.getContext(message, sendRequest);

                Map<String, Object> target = EmailSendRunnerTestHelper.getTarget("retry-exhaust@example.com", "RetryUser");
                EmailSendRunner runner = new EmailSendRunner(EmailSendRunnerTestHelper.toTargets(target), context, emailDomainManager, engine);

                EmailBatchRunSummary summary = runner.execute();

                assertThat(summary.totalBatches()).isEqualTo(1);
                assertThat(summary.successBatches()).isEqualTo(1);
                assertThat(summary.failedBatches()).isZero();

                assertThat(target.get(TargetKey.SEND_STATUS)).isEqualTo("FAILURE");
                assertThat((String) target.get(TargetKey.SEND_CODE)).startsWith("4");
                assertThat(target.get(TargetKey.RETRY_COUNT)).isEqualTo(TEST_MAX_RETRY_COUNT);

                assertThat(simulator.messageCount()).isZero();
                assertThat(simulator.countMessagesForRecipient("retry-exhaust@example.com")).isZero();
            } finally {
                engine.shutdown();
            }
        }
    }

    @DisplayName("5xx response at RCPT causes immediate failure without retry")
    @Test
    void testExecutePermanentFailureWithoutRetry() throws Exception {
        Path inboxDir = spoolDir.resolve("simulator-inbox-permfail");
        try (EmbeddedSimulatorServer simulator = EmbeddedSimulatorServer.startWithPermanentRcptFailure(inboxDir, 550)) {
            EmailDomainManager emailDomainManager = EmailSendRunnerTestHelper.getEmailDomainManager();
            EmailConfig emailConfig = createSimulatorConfig(simulator.serverAddress());
            SmtpSessionManagerFactory factory = EmailSendTestFactorySupport.createDefaultFactory(emailConfig);

            EmailSendEngine engine = createEngine(factory, emailConfig);
            engine.start();

            try {
                Map<String, Object> message = EmailSendRunnerTestHelper.getMessage(123456789012345L);
                Map<String, Object> sendRequest = EmailSendRunnerTestHelper.getSendRequest(123456789012345L, 1234567890123456L, 1);
                EmailSendContext context = EmailSendRunnerTestHelper.getContext(message, sendRequest);

                Map<String, Object> target = EmailSendRunnerTestHelper.getTarget("permfail@example.com", "PermFailUser");
                EmailSendRunner runner = new EmailSendRunner(EmailSendRunnerTestHelper.toTargets(target), context, emailDomainManager, engine);

                EmailBatchRunSummary summary = runner.execute();

                assertThat(summary.totalBatches()).isEqualTo(1);
                assertThat(summary.successBatches()).isEqualTo(1);
                assertThat(summary.failedBatches()).isZero();

                assertThat(target.get(TargetKey.SEND_STATUS)).isEqualTo("FAILURE");
                assertThat(target.get(TargetKey.SEND_CODE)).isEqualTo("550");
                assertThat(target.get(TargetKey.RETRY_COUNT)).isEqualTo(0);
                assertThat(target.get(TargetKey.ERROR_MESSAGE)).isNotNull();

                assertThat(simulator.messageCount()).isZero();
                assertThat(simulator.countMessagesForRecipient("permfail@example.com")).isZero();
            } finally {
                engine.shutdown();
            }
        }
    }

    @DisplayName("MAIL FROM syntax error causes immediate failure")
    @Test
    void testExecuteFailureWhenMailFromIsInvalid() throws Exception {
        Path inboxDir = spoolDir.resolve("simulator-inbox-invalid-mailfrom");
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

                Map<String, Object> target = EmailSendRunnerTestHelper.getTarget("invalid-mailfrom@example.com", "InvalidMailFromUser");
                target.put(TargetKey.SENDER_EMAIL, "invalid sender");
                EmailSendRunner runner = new EmailSendRunner(EmailSendRunnerTestHelper.toTargets(target), context, emailDomainManager, engine);

                EmailBatchRunSummary summary = runner.execute();

                assertThat(summary.totalBatches()).isEqualTo(1);
                assertThat(summary.successBatches()).isEqualTo(1);
                assertThat(summary.failedBatches()).isZero();

                assertThat(target.get(TargetKey.SEND_STATUS)).isEqualTo("FAILURE");
                assertThat(target.get(TargetKey.SEND_CODE)).isNotEqualTo("250");
                assertThat(target.get(TargetKey.ERROR_MESSAGE)).isNotNull();

                assertThat(simulator.messageCount()).isZero();
                assertThat(simulator.countMessagesForRecipient("invalid-mailfrom@example.com")).isZero();
            } finally {
                engine.shutdown();
            }
        }
    }

    @DisplayName("retryExcludeCode(452) response does not retry")
    @Test
    void testExecuteRetryExcludeCodeDoesNotRetry() throws Exception {
        Path inboxDir = spoolDir.resolve("simulator-inbox-retry-exclude");
        try (EmbeddedSimulatorServer simulator = EmbeddedSimulatorServer.startWithFaultInjection(inboxDir, "rcpt-pre", 452)) {
            EmailDomainManager emailDomainManager = EmailSendRunnerTestHelper.getEmailDomainManager();
            EmailConfig emailConfig = createSimulatorConfig(simulator.serverAddress());
            SmtpSessionManagerFactory factory = EmailSendTestFactorySupport.createDefaultFactory(emailConfig);

            EmailSendEngine engine = createEngine(factory, emailConfig);
            engine.start();

            try {
                Map<String, Object> message = EmailSendRunnerTestHelper.getMessage(123456789012345L);
                Map<String, Object> sendRequest = EmailSendRunnerTestHelper.getSendRequest(123456789012345L, 1234567890123456L, 1);
                EmailSendContext context = EmailSendRunnerTestHelper.getContext(message, sendRequest);

                Map<String, Object> target = EmailSendRunnerTestHelper.getTarget("exclude-retry@example.com", "ExcludeRetryUser");
                EmailSendRunner runner = new EmailSendRunner(EmailSendRunnerTestHelper.toTargets(target), context, emailDomainManager, engine);

                EmailBatchRunSummary summary = runner.execute();

                assertThat(summary.totalBatches()).isEqualTo(1);
                assertThat(summary.successBatches()).isEqualTo(1);
                assertThat(summary.failedBatches()).isZero();

                assertThat(target.get(TargetKey.SEND_STATUS)).isEqualTo("FAILURE");
                assertThat(target.get(TargetKey.SEND_CODE)).isEqualTo("452");
                assertThat(target.get(TargetKey.RETRY_COUNT)).isEqualTo(0);

                assertThat(simulator.messageCount()).isZero();
                assertThat(simulator.countMessagesForRecipient("exclude-retry@example.com")).isZero();
            } finally {
                engine.shutdown();
            }
        }
    }

    @DisplayName("Disconnect at RCPT propagates same failure code to remaining targets")
    @Test
    void testExecuteSessionBrokenPropagatesFailureToRemainingTargets() throws Exception {
        Path inboxDir = spoolDir.resolve("simulator-inbox-session-broken");
        try (EmbeddedSimulatorServer simulator = EmbeddedSimulatorServer.startWithFaultInjection( inboxDir, "rcpt-pre", 421, true)) {
            EmailDomainManager emailDomainManager = EmailSendRunnerTestHelper.getEmailDomainManager();
            EmailConfig emailConfig = createSimulatorConfig(simulator.serverAddress());
            SmtpSessionManagerFactory factory = EmailSendTestFactorySupport.createDefaultFactory(emailConfig);

            EmailSendEngine engine = createEngine(factory, emailConfig, 0);
            engine.start();

            try {
                Map<String, Object> message = EmailSendRunnerTestHelper.getMessage(123456789012345L);
                Map<String, Object> sendRequest = EmailSendRunnerTestHelper.getSendRequest(123456789012345L, 1234567890123456L, 1);
                EmailSendContext context = EmailSendRunnerTestHelper.getContext(message, sendRequest);

                Map<String, Object> target1 = EmailSendRunnerTestHelper.getTarget("broken-1@example.com", "BrokenUser1");
                Map<String, Object> target2 = EmailSendRunnerTestHelper.getTarget("broken-2@example.com", "BrokenUser2");
                EmailSendRunner runner = new EmailSendRunner(EmailSendRunnerTestHelper.toTargets(target1, target2), context, emailDomainManager, engine);

                EmailBatchRunSummary summary = runner.execute();

                assertThat(summary.totalBatches()).isEqualTo(1);
                assertThat(summary.successBatches()).isEqualTo(1);
                assertThat(summary.failedBatches()).isZero();

                assertThat(target1.get(TargetKey.SEND_STATUS)).isEqualTo("FAILURE");
                assertThat(target2.get(TargetKey.SEND_STATUS)).isEqualTo("FAILURE");
                assertThat(target2.get(TargetKey.SEND_CODE)).isEqualTo(target1.get(TargetKey.SEND_CODE));
                assertThat(target2.get(TargetKey.ERROR_MESSAGE)).isEqualTo(target1.get(TargetKey.ERROR_MESSAGE));
                assertThat(target1.get(TargetKey.RETRY_COUNT)).isEqualTo(0);
                assertThat(target2.get(TargetKey.RETRY_COUNT)).isEqualTo(0);

                assertThat(simulator.messageCount()).isZero();
                assertThat(simulator.countMessagesForRecipient("broken-1@example.com")).isZero();
                assertThat(simulator.countMessagesForRecipient("broken-2@example.com")).isZero();
            } finally {
                engine.shutdown();
            }
        }
    }

    @DisplayName("DATA_END 5xx response fails without retry")
    @Test
    void testExecuteDataEndPermanentFailureWithoutRetry() throws Exception {
        Path inboxDir = spoolDir.resolve("simulator-inbox-data-end-permfail");
        try (EmbeddedSimulatorServer simulator = EmbeddedSimulatorServer.startWithFaultInjection(inboxDir, "data-end", 553)) {
            EmailDomainManager emailDomainManager = EmailSendRunnerTestHelper.getEmailDomainManager();
            EmailConfig emailConfig = createSimulatorConfig(simulator.serverAddress());
            SmtpSessionManagerFactory factory = EmailSendTestFactorySupport.createDefaultFactory(emailConfig);

            EmailSendEngine engine = createEngine(factory, emailConfig);
            engine.start();

            try {
                Map<String, Object> message = EmailSendRunnerTestHelper.getMessage(123456789012345L);
                Map<String, Object> sendRequest = EmailSendRunnerTestHelper.getSendRequest(123456789012345L, 1234567890123456L, 1);
                EmailSendContext context = EmailSendRunnerTestHelper.getContext(message, sendRequest);

                Map<String, Object> target = EmailSendRunnerTestHelper.getTarget("data-end-permfail@example.com", "DataEndPermFailUser");
                EmailSendRunner runner = new EmailSendRunner(EmailSendRunnerTestHelper.toTargets(target), context, emailDomainManager, engine);

                EmailBatchRunSummary summary = runner.execute();

                assertThat(summary.totalBatches()).isEqualTo(1);
                assertThat(summary.successBatches()).isEqualTo(1);
                assertThat(summary.failedBatches()).isZero();

                assertThat(target.get(TargetKey.SEND_STATUS)).isEqualTo("FAILURE");
                assertThat(target.get(TargetKey.SEND_CODE)).isEqualTo("553");
                assertThat(target.get(TargetKey.RETRY_COUNT)).isEqualTo(0);

                assertThat(simulator.messageCount()).isZero();
                assertThat(simulator.countMessagesForRecipient("data-end-permfail@example.com")).isZero();
            } finally {
                engine.shutdown();
            }
        }
    }

    @DisplayName("DATA_END 4xx response fails after retry exhaustion")
    @Test
    void testExecuteDataEndTemporaryFailureRetryExhausted() throws Exception {
        Path inboxDir = spoolDir.resolve("simulator-inbox-data-end-tempfail");
        try (EmbeddedSimulatorServer simulator = EmbeddedSimulatorServer.startWithFaultInjection(inboxDir, "data-end", 451)) {
            EmailDomainManager emailDomainManager = EmailSendRunnerTestHelper.getEmailDomainManager();
            EmailConfig emailConfig = createSimulatorConfig(simulator.serverAddress());
            SmtpSessionManagerFactory factory = EmailSendTestFactorySupport.createDefaultFactory(emailConfig);

            int maxRetryCount = 2;
            EmailSendEngine engine = createEngine(factory, emailConfig, maxRetryCount);
            engine.start();

            try {
                Map<String, Object> message = EmailSendRunnerTestHelper.getMessage(123456789012345L);
                Map<String, Object> sendRequest = EmailSendRunnerTestHelper.getSendRequest(123456789012345L, 1234567890123456L, 1);
                EmailSendContext context = EmailSendRunnerTestHelper.getContext(message, sendRequest);

                Map<String, Object> target = EmailSendRunnerTestHelper.getTarget("data-end-tempfail@example.com", "DataEndTempFailUser");
                EmailSendRunner runner = new EmailSendRunner(EmailSendRunnerTestHelper.toTargets(target), context, emailDomainManager, engine);

                EmailBatchRunSummary summary = runner.execute();

                assertThat(summary.totalBatches()).isEqualTo(1);
                assertThat(summary.successBatches()).isEqualTo(1);
                assertThat(summary.failedBatches()).isZero();

                assertThat(target.get(TargetKey.SEND_STATUS)).isEqualTo("FAILURE");
                assertThat(target.get(TargetKey.SEND_CODE)).isEqualTo("451");
                assertThat(target.get(TargetKey.RETRY_COUNT)).isEqualTo(maxRetryCount);

                assertThat(simulator.messageCount()).isZero();
                assertThat(simulator.countMessagesForRecipient("data-end-tempfail@example.com")).isZero();
            } finally {
                engine.shutdown();
            }
        }
    }

    @DisplayName("RCPT 421 response fails after retry exhaustion")
    @Test
    void testExecuteRcpt421IsRetryable() throws Exception {
        Path inboxDir = spoolDir.resolve("simulator-inbox-rcpt-421-retry");
        try (EmbeddedSimulatorServer simulator = EmbeddedSimulatorServer.startWithFaultInjection(inboxDir, "rcpt-pre", 421)) {
            EmailDomainManager emailDomainManager = EmailSendRunnerTestHelper.getEmailDomainManager();
            EmailConfig emailConfig = createSimulatorConfig(simulator.serverAddress());
            // 421 is operated as a retryable code.
            emailConfig.getSend().setRetryExcludeCode(
                    emailConfig.getSend().getRetryExcludeCode().stream()
                            .filter(code -> !"421".equals(code))
                            .toList()
            );
            SmtpSessionManagerFactory factory = EmailSendTestFactorySupport.createDefaultFactory(emailConfig);

            int maxRetryCount = 1;
            EmailSendEngine engine = createEngine(factory, emailConfig, maxRetryCount);
            engine.start();

            try {
                Map<String, Object> message = EmailSendRunnerTestHelper.getMessage(123456789012345L);
                Map<String, Object> sendRequest = EmailSendRunnerTestHelper.getSendRequest(123456789012345L, 1234567890123456L, 1);
                EmailSendContext context = EmailSendRunnerTestHelper.getContext(message, sendRequest);

                Map<String, Object> target = EmailSendRunnerTestHelper.getTarget("rcpt-421@example.com", "Rcpt421User");
                EmailSendRunner runner = new EmailSendRunner(EmailSendRunnerTestHelper.toTargets(target), context, emailDomainManager, engine);

                EmailBatchRunSummary summary = runner.execute();

                assertThat(summary.totalBatches()).isEqualTo(1);
                assertThat(summary.successBatches()).isEqualTo(1);
                assertThat(summary.failedBatches()).isZero();

                assertThat(target.get(TargetKey.SEND_STATUS)).isEqualTo("FAILURE");
                assertThat(target.get(TargetKey.SEND_CODE)).isEqualTo("421");
                assertThat(target.get(TargetKey.RETRY_COUNT)).isEqualTo(maxRetryCount);

                assertThat(simulator.messageCount()).isZero();
                assertThat(simulator.countMessagesForRecipient("rcpt-421@example.com")).isZero();
            } finally {
                engine.shutdown();
            }
        }
    }

    @DisplayName("DATA response read timeout fails with 704 code")
    @Test
    void testExecuteDataTimeoutMappedTo704() throws Exception {
        Path inboxDir = spoolDir.resolve("simulator-inbox-data-timeout");
        try (EmbeddedSimulatorServer simulator = EmbeddedSimulatorServer.startWithResponseDelay(inboxDir, "data-pre", Duration.ofSeconds(2))) {
            EmailDomainManager emailDomainManager = EmailSendRunnerTestHelper.getEmailDomainManager();
            EmailConfig emailConfig = createSimulatorConfig(simulator.serverAddress());
            emailConfig.getSend().setDataReadTimeout(1);
            SmtpSessionManagerFactory factory = EmailSendTestFactorySupport.createDefaultFactory(emailConfig);

            int maxRetryCount = 1;
            EmailSendEngine engine = createEngine(factory, emailConfig, maxRetryCount);
            engine.start();

            try {
                Map<String, Object> message = EmailSendRunnerTestHelper.getMessage(123456789012345L);
                Map<String, Object> sendRequest = EmailSendRunnerTestHelper.getSendRequest(123456789012345L, 1234567890123456L, 1);
                EmailSendContext context = EmailSendRunnerTestHelper.getContext(message, sendRequest);

                Map<String, Object> target = EmailSendRunnerTestHelper.getTarget("data-timeout@example.com", "DataTimeoutUser");
                EmailSendRunner runner = new EmailSendRunner(EmailSendRunnerTestHelper.toTargets(target), context, emailDomainManager, engine);

                EmailBatchRunSummary summary = runner.execute();

                assertThat(summary.totalBatches()).isEqualTo(1);
                assertThat(summary.successBatches()).isEqualTo(1);
                assertThat(summary.failedBatches()).isZero();

                assertThat(target.get(TargetKey.SEND_STATUS)).isEqualTo("FAILURE");
                assertThat(target.get(TargetKey.SEND_CODE)).isEqualTo("704");
                assertThat(target.get(TargetKey.RETRY_COUNT)).isEqualTo(maxRetryCount);
                assertThat(target.get(TargetKey.ERROR_MESSAGE)).isNotNull();

                // When only DATA response times out, server save may succeed, resulting in duplicate saves for each retry.
                assertThat(simulator.messageCount()).isEqualTo(maxRetryCount + 1L);
                assertThat(simulator.countMessagesForRecipient("data-timeout@example.com")).isEqualTo(maxRetryCount + 1L);
            } finally {
                engine.shutdown();
            }
        }
    }

    private EmailConfig createSimulatorConfig(String simulatorServerAddress) {
        EmailConfig emailConfig = EmailSendTestFactorySupport.createEmailSimulatorConfig(spoolDir.toString());
        emailConfig.getSend().setSimulatorServer(simulatorServerAddress);
        return emailConfig;
    }

    private EmailSendEngine createEngine(SmtpSessionManagerFactory factory, EmailConfig emailConfig) {
        return createEngine(factory, emailConfig, TEST_MAX_RETRY_COUNT);
    }

    private EmailSendEngine createEngine(SmtpSessionManagerFactory factory, EmailConfig emailConfig, int maxRetryCount) {
        return EmailSendEngineFactory.create(
                2,
                new EmailBatchSenderFactory(emailConfig, factory),
                createDomainManager(2),
                emailConfig.getSend(),
                TEST_SCHEDULER_INTERVAL_MS,
                Math.max(0, maxRetryCount),
                TEST_INITIAL_RETRY_DELAY_MS,
                TEST_MAX_RETRY_DELAY_MS,
                TEST_RETRY_BACKOFF_MULTIPLIER
        );
    }

    private EmailDomainManager createDomainManager(int sessionCount) {
        return new EmailDomainManager(
                List.of(new EmailDomain("default", Math.max(1, sessionCount), 10, 60, 60, "")),
                LocalDateTime.now()
        );
    }
}
