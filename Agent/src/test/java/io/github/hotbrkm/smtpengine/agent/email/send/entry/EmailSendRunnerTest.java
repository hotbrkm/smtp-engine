package io.github.hotbrkm.smtpengine.agent.email.send.entry;

import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.EmailSendEngine;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.EmailSendEngineFactory;
import io.github.hotbrkm.smtpengine.agent.email.send.planning.EmailBatchSpec;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchResult;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchRunSummary;
import io.github.hotbrkm.smtpengine.agent.email.send.worker.EmailBatchSenderFactory;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendRunnerTestStubs.StubSmtpSessionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EmailSendRunner test")
class EmailSendRunnerTest {

    @TempDir
    Path spoolDir;

    @DisplayName("Verify engine invocation")
    @Test
    void testExecuteCallsEngine() {
        // Given: Engine that records invocation count
        EmailDomainManager emailDomainManager = EmailSendRunnerTestHelper.getEmailDomainManager();
        EmailSendEngine engine = mock(EmailSendEngine.class);
        when(engine.submitBatchAsync(any(EmailBatchSpec.class))).thenAnswer(invocation -> {
            EmailBatchSpec spec = invocation.getArgument(0);
            return CompletableFuture.completedFuture(
                    EmailBatchResult.success(spec.getBatchId(), spec.getDomain(), spec.getEmailSendTargetList().size(), 0)
            );
        });

        Map<String, Object> message = EmailSendRunnerTestHelper.getMessage(123456789012345L);
        Map<String, Object> sendRequest = EmailSendRunnerTestHelper.getSendRequest(123456789012345L, 1234567890123456L, 1);
        EmailSendContext context = EmailSendRunnerTestHelper.getContext(message, sendRequest);

        Map<String, Object> target = EmailSendRunnerTestHelper.getTarget("pending@example.com", "PendingUser");
        List<Map<String, Object>> targetDataList = EmailSendRunnerTestHelper.toList(target);

        EmailSendRunner runner = new EmailSendRunner(EmailSendRunnerTestHelper.toTargets(targetDataList), context, emailDomainManager, engine);

        // When: Execute
        EmailBatchRunSummary summary = runner.execute();

        // Then: Verify engine was invoked
        assertThat(summary.totalBatches()).isEqualTo(1);
        assertThat(summary.successBatches()).isEqualTo(1);
        assertThat(summary.failedBatches()).isZero();

        verify(engine, times(1)).submitBatchAsync(any(EmailBatchSpec.class));
        assertThat(target.get(TargetKey.SEND_STATUS)).isEqualTo("PENDING");
    }

    @DisplayName("Should not invoke engine when target list is empty")
    @Test
    void testExecuteWithEmptyTargetList() {
        // Given: Empty target list
        EmailDomainManager emailDomainManager = EmailSendRunnerTestHelper.getEmailDomainManager();
        EmailSendEngine engine = mock(EmailSendEngine.class);

        Map<String, Object> message = EmailSendRunnerTestHelper.getMessage(123456789012345L);
        Map<String, Object> sendRequest = EmailSendRunnerTestHelper.getSendRequest(123456789012345L, 1234567890123456L, 1);
        EmailSendContext context = EmailSendRunnerTestHelper.getContext(message, sendRequest);

        EmailSendRunner runner = new EmailSendRunner(Collections.emptyList(), context, emailDomainManager, engine);

        // When: Execute
        EmailBatchRunSummary summary = runner.execute();

        // Then: Engine is not invoked
        assertThat(summary.totalBatches()).isZero();
        assertThat(summary.successBatches()).isZero();
        assertThat(summary.failedBatches()).isZero();

        verify(engine, never()).submitBatchAsync(any(EmailBatchSpec.class));
    }

    @DisplayName("Invalid email domain should be marked as FAILURE(800)")
    @Test
    void testExecuteWithInvalidEmailDomain() {
        // Given: Target with invalid email domain and real engine
        EmailDomainManager emailDomainManager = EmailSendRunnerTestHelper.getEmailDomainManager();

        EmailConfig emailConfig = EmailSendTestFactorySupport.createEmailSimulatorConfig(spoolDir.toString());
        EmailSendEngine engine = EmailSendEngineFactory.create(2,
                new EmailBatchSenderFactory(emailConfig, EmailSendRunnerTestStubs.stubFactory(emailConfig, StubSmtpSessionManager::new)),
                emailDomainManager, emailConfig.getSend(), 100, 3);
        
        engine.start();
        
        try {
            Map<String, Object> message = EmailSendRunnerTestHelper.getMessage(123456789012345L);
            Map<String, Object> sendRequest = EmailSendRunnerTestHelper.getSendRequest(123456789012345L, 1234567890123456L, 1);
            EmailSendContext context = EmailSendRunnerTestHelper.getContext(message, sendRequest);

            Map<String, Object> target = EmailSendRunnerTestHelper.getTarget("not-an-email", "InvalidUser");
            List<Map<String, Object>> targetDataList = EmailSendRunnerTestHelper.toList(target);

            List<EmailSendTarget> targets = EmailSendRunnerTestHelper.toTargets(targetDataList);
            EmailSendRunner runner = new EmailSendRunner(targets, context, emailDomainManager, engine);

// When: Execute
            EmailBatchRunSummary summary = runner.execute();

            // Then: Batch executed (1 success batch), but target marked as FAILURE(800)
            // (Note: Batch success indicates engine execution/result reception success, not target success/failure)
            assertThat(summary.totalBatches()).isEqualTo(1);
            assertThat(summary.successBatches()).isEqualTo(1);
            assertThat(summary.failedBatches()).isZero();

            assertThat(target.get(TargetKey.SEND_STATUS)).isEqualTo("FAILURE");
            assertThat(target.get(TargetKey.SEND_CODE)).isEqualTo("800");
            assertThat((String) target.get(TargetKey.ERROR_MESSAGE)).contains("800");
        } finally {
            engine.shutdown();
        }
    }

    @DisplayName("Should propagate exception and stop when engine throws exception (verify EmailSendRunner behavior)")
    @Test
    void testExecuteWhenEngineThrowsException() {
        // Given: Engine that throws exception on submit
        EmailDomainManager emailDomainManager = EmailSendRunnerTestHelper.getEmailDomainManager();
        EmailSendEngine engine = mock(EmailSendEngine.class);
        when(engine.submitBatchAsync(any(EmailBatchSpec.class))).thenThrow(new RuntimeException("engine failure for test"));

        Map<String, Object> message = EmailSendRunnerTestHelper.getMessage(123456789012345L);
        Map<String, Object> sendRequest = EmailSendRunnerTestHelper.getSendRequest(123456789012345L, 1234567890123456L, 1);
        EmailSendContext context = EmailSendRunnerTestHelper.getContext(message, sendRequest);

        Map<String, Object> target = EmailSendRunnerTestHelper.getTarget("exception@example.com", "ErrorUser");
        List<Map<String, Object>> targetDataList = EmailSendRunnerTestHelper.toList(target);

        EmailSendRunner runner = new EmailSendRunner(EmailSendRunnerTestHelper.toTargets(targetDataList), context, emailDomainManager, engine);

        // When/Then: Exception is propagated on execution
        assertThatThrownBy(runner::execute)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("engine failure for test");

        assertThat(target.get(TargetKey.SEND_STATUS)).isEqualTo("PENDING");
    }
}
