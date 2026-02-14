package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomain;
import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendContext;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.ExecutionMode;
import io.github.hotbrkm.smtpengine.agent.email.send.planning.EmailBatchSpec;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchResult;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchResultWriter;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailSendProgress;
import io.github.hotbrkm.smtpengine.agent.email.send.result.ResultPersistenceException;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.routing.RoutingService;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpSessionManager;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpSessionManagerFactory;
import io.github.hotbrkm.smtpengine.agent.email.send.worker.EmailBatchSenderFactory;
import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DispatchLane;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchQueue;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchTask;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailSendEngine runner abort/resume control test")
class EmailSendEngineRunnerControlTest {

    @TempDir
    Path tempDir;
    private final Map<EmailSendEngine, EngineFixture> fixtures = new IdentityHashMap<>();

    @Test
    @DisplayName("abortRunner removes queued batches with same runner/token and marks them as failed")
    void abortRunner_removesQueuedTasksForSameRunnerToken() throws Exception {
        EmailSendEngine engine = createEngine();
        EmailBatchResultWriter runnerTokenA = createMockWriter();
        EmailBatchResultWriter runnerTokenB = createMockWriter();

        try {
            DomainBatchQueue batchQueue = getBatchQueue(engine);
            String runnerId = "runner-1";

            CompletableFuture<EmailBatchResult> staleFuture = new CompletableFuture<>();
            CompletableFuture<EmailBatchResult> keepFuture = new CompletableFuture<>();

            batchQueue.offer(createTask("batch-stale", "example.com", runnerId, runnerTokenA, staleFuture));
            batchQueue.offer(createTask("batch-keep", "example.com", runnerId, runnerTokenB, keepFuture));
            assertThat(engine.getQueuedBatches()).isEqualTo(2);

            DomainBatchTask failedTask = createTask("batch-failed", "example.com", runnerId, runnerTokenA, new CompletableFuture<>());
            ResultPersistenceException cause = new ResultPersistenceException("checkpoint failed");

            invokeAbortRunner(engine, failedTask, cause);

            assertThat(engine.getQueuedBatches()).isEqualTo(1);

            EmailBatchResult staleResult = staleFuture.join();
            assertThat(staleResult.success()).isFalse();
            assertThat(staleResult.exception()).isInstanceOf(ResultPersistenceException.class);
            assertThat(staleResult.exception().getMessage()).contains("checkpoint failed");

            assertThat(keepFuture).isNotDone();
        } finally {
            runnerTokenA.close();
            runnerTokenB.close();
            engine.shutdown();
        }
    }

    @Test
    @DisplayName("Scheduled retry batches after abort are not queued and marked as failed")
    void scheduledRetry_isBlockedAfterAbort() throws Exception {
        EmailSendEngine engine = createEngine();
        EmailBatchResultWriter runnerToken = createMockWriter();

        try {
            String runnerId = "runner-1";
            ResultPersistenceException cause = new ResultPersistenceException("checkpoint failed");
            DomainBatchTask failedTask = createTask("batch-failed", "example.com", runnerId, runnerToken, new CompletableFuture<>());
            invokeAbortRunner(engine, failedTask, cause);

            CompletableFuture<EmailBatchResult> retryFuture = new CompletableFuture<>();
            DomainBatchTask retryTask = createTask("batch-retry", "example.com", runnerId, runnerToken, retryFuture);

            invokeSubmitResourceLimitRetryBatch(engine, retryTask);

            assertThat(engine.getQueuedBatches()).isZero();

            EmailBatchResult retryResult = retryFuture.get(2, TimeUnit.SECONDS);
            assertThat(retryResult.success()).isFalse();
            assertThat(retryResult.exception()).isInstanceOf(ResultPersistenceException.class);
            assertThat(retryResult.exception().getMessage()).contains("checkpoint failed");
        } finally {
            runnerToken.close();
            engine.shutdown();
        }
    }

    @Test
    @DisplayName("registerRunner clears residual queue from previous execution")
    void registerRunner_clearsStaleQueuedTasks() {
        EmailSendEngine engine = createEngine();
        EmailBatchResultWriter oldRunnerToken = createMockWriter();
        EmailBatchResultWriter newRunnerToken = createMockWriter();

        try {
            String runnerId = "runner-1";
            CompletableFuture<EmailBatchResult> future1 = engine.submitBatchAsync(createSpec("batch-1", "example.com", runnerId, oldRunnerToken));
            CompletableFuture<EmailBatchResult> future2 = engine.submitBatchAsync(createSpec("batch-2", "example.com", runnerId, oldRunnerToken));

            assertThat(engine.getQueuedBatches()).isEqualTo(2);

            engine.registerRunner(runnerId, newRunnerToken);

            assertThat(engine.getQueuedBatches()).isZero();

            EmailBatchResult result1 = future1.join();
            EmailBatchResult result2 = future2.join();

            assertThat(result1.success()).isFalse();
            assertThat(result1.exception()).isInstanceOf(IllegalStateException.class);
            assertThat(result1.exception().getMessage()).contains("Dropped stale queued task");

            assertThat(result2.success()).isFalse();
            assertThat(result2.exception()).isInstanceOf(IllegalStateException.class);
            assertThat(result2.exception().getMessage()).contains("Dropped stale queued task");
        } finally {
            oldRunnerToken.close();
            newRunnerToken.close();
            engine.shutdown();
        }
    }

    @Test
    @DisplayName("Request with missing execution_mode returns failed result Future")
    void submitBatchAsync_missingExecutionMode_returnsFailedFuture() {
        EmailSendEngine engine = createEngine();
        try {
            EmailBatchSpec invalidSpec = new EmailBatchSpec("batch-1", "example.com",
                    List.of(EmailSendTarget.builder().targetEmail("user@example.com").targetData(Collections.emptyMap()).build()),
                    "runner-1", createMockWriter(), null);

            CompletableFuture<EmailBatchResult> future = engine.submitBatchAsync(invalidSpec);
            EmailBatchResult result = future.join();

            assertThat(result.success()).isFalse();
            assertThat(result.exception()).isInstanceOf(IllegalArgumentException.class);
            assertThat(result.exception().getMessage()).contains("Missing execution_mode");
        } finally {
            engine.shutdown();
        }
    }

    private EmailSendEngine createEngine() {
        EmailConfig emailConfig = new EmailConfig();
        emailConfig.getSend().setBindAddresses(List.of("127.0.0.1"));
        emailConfig.getSend().setRetryExcludeCode(List.of("250"));
        emailConfig.getSend().setDnsServer(List.of());
        emailConfig.getSend().setExcludedDomain(List.of());
        emailConfig.getSend().setFixedIpOfDomain(new HashMap<>());
        emailConfig.getSend().setTlsEnabledProtocols(List.of("TLSv1.2"));
        emailConfig.getSend().setTlsApplyScope("NONE");
        emailConfig.getSend().setTlsApplyDomain(List.of());

        EmailDomainManager emailDomainManager = new EmailDomainManager(
                List.of(new EmailDomain("example.com", 1, 1, 60, 60, "")),
                LocalDateTime.now()
        );

        EmailConfig.Send send = emailConfig.getSend();
        EngineRuntimeOptions options = EngineRuntimeOptions.fromExplicit(send,
                1,
                100,
                3,
                1L,
                10L,
                EmailConfig.Send.DEFAULT_BATCH_RETRY_BACKOFF_MULTIPLIER);
        EmailSendEngineFactory.Assembly assembly = EmailSendEngineFactory.assemble(
                new EmailBatchSenderFactory(emailConfig, nullSessionFactory()),
                emailDomainManager,
                send,
                options
        );
        EmailSendEngine engine = new EmailSendEngine(assembly);
        fixtures.put(engine, new EngineFixture(assembly.context(), assembly.executionCoordinator(), new RetryScheduler(assembly.context())));
        return engine;
    }

    private SmtpSessionManagerFactory nullSessionFactory() {
        EmailConfig factoryConfig = new EmailConfig();
        factoryConfig.getSend().setDnsServer(List.of("127.0.0.1"));
        return new SmtpSessionManagerFactory(
                factoryConfig,
                new EmailDomainManager(Collections.emptyList(), LocalDateTime.now()),
                new RoutingService(factoryConfig)
        ) {
            @Override
            public SmtpSessionManager create() {
                return null;
            }
        };
    }

    private DomainBatchTask createTask(String batchId, String domain, String runnerId, EmailBatchResultWriter runnerToken, CompletableFuture<EmailBatchResult> resultFuture) {
        return new DomainBatchTask(List.of(EmailSendTarget.builder().targetEmail("user@" + domain).targetData(Collections.emptyMap()).build()),
                domain, batchId, resultFuture, 0, runnerId, runnerToken, createContext(ExecutionMode.BATCH),
                ExecutionMode.BATCH, DispatchLane.forFresh(ExecutionMode.BATCH));
    }

    private EmailBatchSpec createSpec(String batchId, String domain, String runnerId, EmailBatchResultWriter runnerToken) {
        return new EmailBatchSpec(batchId, domain, List.of(EmailSendTarget.builder().targetEmail("user@" + domain).targetData(Collections.emptyMap()).build()),
                runnerId, runnerToken, createContext(ExecutionMode.BATCH));
    }

    private EmailSendContext createContext(ExecutionMode executionMode) {
        return new EmailSendContext(1L, 1L, 1, "INFO", "GENERAL", executionMode);
    }

    private DomainBatchQueue getBatchQueue(EmailSendEngine engine) {
        return fixtureOf(engine).context().batchQueue();
    }

    private void invokeAbortRunner(EmailSendEngine engine, DomainBatchTask failedTask, ResultPersistenceException persistenceException) {
        fixtureOf(engine).executionCoordinator().abortRunner(failedTask, persistenceException);
    }

    private void invokeSubmitResourceLimitRetryBatch(EmailSendEngine engine, DomainBatchTask task) {
        fixtureOf(engine).retryScheduler().submitResourceLimitRetryBatch(task, "attachment in-flight slot", "attachment_slot");
    }

    private EmailBatchResultWriter createMockWriter() {
        return new EmailBatchResultWriter() {
            @Override public void writeResult(EmailSendProgress progress) {}
            @Override public void close() {}
        };
    }

    private EngineFixture fixtureOf(EmailSendEngine engine) {
        EngineFixture fixture = fixtures.get(engine);
        if (fixture == null) {
            throw new IllegalStateException("Missing fixture for engine");
        }
        return fixture;
    }

    private record EngineFixture(EngineRuntimeContext context, ExecutionCoordinator executionCoordinator, RetryScheduler retryScheduler) {
    }
}
