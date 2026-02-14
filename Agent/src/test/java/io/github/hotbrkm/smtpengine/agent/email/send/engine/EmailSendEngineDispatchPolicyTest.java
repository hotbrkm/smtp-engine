package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.mime.AttachmentMedia;
import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomain;
import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.ExecutionMode;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchResult;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.routing.RoutingService;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpSessionManager;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpSessionManagerFactory;
import io.github.hotbrkm.smtpengine.agent.email.send.worker.EmailBatchSenderFactory;
import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DispatchLane;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchQueue;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchTask;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailSendEngine dispatch policy test")
class EmailSendEngineDispatchPolicyTest {

    private final Map<EmailSendEngine, EngineFixture> fixtures = new IdentityHashMap<>();

    @Test
    @DisplayName("During RT fresh wait, within hold window, BA dispatch limit=0")
    void resolveBatchDispatchLimitPerCycle_withinHoldWindow_returnsZero() throws Exception {
        EmailSendEngine engine = createEngine(10, send -> send.setBatchHoldMaxMs(200L));
        try {
            setField(engine, "batchHoldStartedAtMs", System.currentTimeMillis());
            int limit = invokeResolveBatchDispatchLimitPerCycle(engine, 10, true);
            assertThat(limit).isZero();
        } finally {
            engine.shutdown();
        }
    }

    @Test
    @DisplayName("After hold window passes, BA release percent is applied")
    void resolveBatchDispatchLimitPerCycle_afterHoldWindow_returnsReleaseLimit() throws Exception {
        EmailSendEngine engine = createEngine(10, send -> {
            send.setBatchHoldMaxMs(100L);
            send.setBatchReleasePercentPerCycle(30);
        });
        try {
            setField(engine, "batchHoldStartedAtMs", System.currentTimeMillis() - 150L);
            int limit = invokeResolveBatchDispatchLimitPerCycle(engine, 10, true);
            assertThat(limit).isEqualTo(3);
        } finally {
            engine.shutdown();
        }
    }

    @Test
    @DisplayName("During RT fresh wait, RT retry is limited by cap")
    void canDispatchRetry_withRealtimeFreshPending_appliesCap() throws Exception {
        EmailSendEngine engine = createEngine(10, send -> {
            send.setRealtimeRetryMaxPercent(20);
            send.setFreshReserveMin(1);
            send.setRetryAgingRelaxThresholdMs(60_000L);
        });
        try {
            DomainBatchQueue queue = getBatchQueue(engine);
            queue.offer(task("rt-fresh", "rt.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_FRESH, 0));

            setInFlight(engine, DispatchLane.REALTIME_RETRY, 1);
            boolean allowedAtOne = invokeCanDispatchRetry(engine, DispatchLane.REALTIME_RETRY, 10);
            assertThat(allowedAtOne).isTrue();

            setInFlight(engine, DispatchLane.REALTIME_RETRY, 2);
            boolean blockedAtTwo = invokeCanDispatchRetry(engine, DispatchLane.REALTIME_RETRY, 10);
            assertThat(blockedAtTwo).isFalse();
        } finally {
            engine.shutdown();
        }
    }

    @Test
    @DisplayName("When no RT fresh pending, RT retry can drain slots except freshReserveMin")
    void canDispatchRetry_withoutRealtimeFresh_allowsDrainUntilReserve() throws Exception {
        EmailSendEngine engine = createEngine(10, send -> {
            send.setRealtimeRetryMaxPercent(20);
            send.setFreshReserveMin(1);
            send.setRetryAgingRelaxThresholdMs(60_000L);
        });
        try {
            setInFlight(engine, DispatchLane.REALTIME_RETRY, 8);
            boolean allowedAtEight = invokeCanDispatchRetry(engine, DispatchLane.REALTIME_RETRY, 10);
            assertThat(allowedAtEight).isTrue();

            setInFlight(engine, DispatchLane.REALTIME_RETRY, 9);
            boolean blockedAtNine = invokeCanDispatchRetry(engine, DispatchLane.REALTIME_RETRY, 10);
            assertThat(blockedAtNine).isFalse();
        } finally {
            engine.shutdown();
        }
    }

    @Test
    @DisplayName("When retry aging threshold is exceeded, cap is relaxed to relax percent")
    void canDispatchRetry_withAgingRelax_usesRelaxedCap() throws Exception {
        EmailSendEngine engine = createEngine(10, send -> {
            send.setRealtimeRetryMaxPercent(20);
            send.setRetryAgingRelaxPercent(70);
            send.setRetryAgingRelaxThresholdMs(0L);
            send.setFreshReserveMin(1);
        });
        try {
            DomainBatchQueue queue = getBatchQueue(engine);
            queue.offer(task("rt-fresh", "rt.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_FRESH, 0));
            queue.offer(task("rt-retry", "rt.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_RETRY, 1));

            setInFlight(engine, DispatchLane.REALTIME_RETRY, 6);
            boolean allowedAtSix = invokeCanDispatchRetry(engine, DispatchLane.REALTIME_RETRY, 10);
            assertThat(allowedAtSix).isTrue();

            setInFlight(engine, DispatchLane.REALTIME_RETRY, 7);
            boolean blockedAtSeven = invokeCanDispatchRetry(engine, DispatchLane.REALTIME_RETRY, 10);
            assertThat(blockedAtSeven).isFalse();
        } finally {
            engine.shutdown();
        }
    }

    @Test
    @DisplayName("During batch release smoothing cycle, BA dispatch limit is maintained")
    void resolveBatchDispatchLimitPerCycle_withReleaseSmoothing_appliesPercentThenUnlimited() throws Exception {
        EmailSendEngine engine = createEngine(10, send -> send.setBatchReleasePercentPerCycle(20));
        try {
            setIntField(engine, "batchReleaseSmoothingCycles", 1);
            int limited = invokeResolveBatchDispatchLimitPerCycle(engine, 10, false);
            assertThat(limited).isEqualTo(2);

            invokeAdvanceBatchReleaseSmoothingCycle(engine);
            int unlimited = invokeResolveBatchDispatchLimitPerCycle(engine, 10, false);
            assertThat(unlimited).isEqualTo(Integer.MAX_VALUE);
        } finally {
            engine.shutdown();
        }
    }

    @Test
    @DisplayName("When attachment in-flight limit is reached, executeBatch returns false and delays requeue")
    void executeBatch_whenAttachmentLimitReached_requeuesTask() throws Exception {
        EmailSendEngine engine = createEngine(2, send -> send.setAttachmentMaxInFlight(1));
        try {
            DomainBatchTask attachmentTask = task("att-1", "rt.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_FRESH, 0, true);
            setAttachmentInFlight(engine, 1);

            boolean executed = invokeExecuteBatch(engine, attachmentTask);
            assertThat(executed).isFalse();

            Thread.sleep(450L);
            DomainBatchQueue queue = getBatchQueue(engine);
            assertThat(queue.getTotalBatchCount()).isEqualTo(1);
        } finally {
            engine.shutdown();
        }
    }

    @Test
    @DisplayName("When attachment limit is active, non-attachment batches are not subject to limit")
    void tryAcquireAttachmentSlot_withoutAttachment_ignoresLimit() throws Exception {
        EmailSendEngine engine = createEngine(2, send -> send.setAttachmentMaxInFlight(1));
        try {
            DomainBatchTask nonAttachmentTask = task("non-att", "rt.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_FRESH, 0, false);
            setAttachmentInFlight(engine, 1);

            boolean acquired = invokeTryAcquireAttachmentSlot(engine, nonAttachmentTask);
            assertThat(acquired).isTrue();
            assertThat(getAttachmentInFlight(engine)).isEqualTo(1);
        } finally {
            engine.shutdown();
        }
    }

    @Test
    @DisplayName("When attachment limit wait times out and retries exhausted, batch is terminated")
    void executeBatch_whenAttachmentLimitTimeoutExhausted_completesBatch() throws Exception {
        EmailSendEngine engine = createEngine(2, send -> {
            send.setAttachmentMaxInFlight(1);
            send.setAttachmentAssignWaitTimeoutMs(0L);
        });
        try {
            CompletableFuture<EmailBatchResult> resultFuture = new CompletableFuture<>();
            DomainBatchTask task = task("att-timeout", "rt.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_FRESH, 3,
                    List.of(new AttachmentMedia("sample.txt", "/tmp/sample.txt")),
                    resultFuture);

            setAttachmentInFlight(engine, 1);
            boolean executed = invokeExecuteBatch(engine, task);
            assertThat(executed).isFalse();

            EmailBatchResult result = resultFuture.join();
            assertThat(result.success()).isTrue();
            assertThat(task.getBatch().getFirst().getSendStatus()).isEqualTo("FAILURE");
            assertThat(task.getBatch().getFirst().getErrorMessage()).contains("attachment in-flight slot");

            Thread.sleep(450L);
            DomainBatchQueue queue = getBatchQueue(engine);
            assertThat(queue.getTotalBatchCount()).isZero();
        } finally {
            engine.shutdown();
        }
    }

    private EmailSendEngine createEngine(int workers, java.util.function.Consumer<EmailConfig.Send> sendCustomizer) {
        EmailConfig emailConfig = new EmailConfig();
        EmailConfig.Send send = emailConfig.getSend();
        send.setBindAddresses(List.of("127.0.0.1"));
        send.setRetryExcludeCode(List.of("250"));
        send.setDnsServer(List.of());
        send.setExcludedDomain(List.of());
        send.setFixedIpOfDomain(new HashMap<>());
        send.setTlsEnabledProtocols(List.of("TLSv1.2"));
        send.setTlsApplyScope("NONE");
        send.setTlsApplyDomain(List.of());
        if (sendCustomizer != null) {
            sendCustomizer.accept(send);
        }

        EmailDomainManager emailDomainManager = new EmailDomainManager(
                List.of(new EmailDomain("rt.com", 1, 1, 60, 60, "")),
                LocalDateTime.now()
        );

        EngineRuntimeOptions options = EngineRuntimeOptions.fromExplicit(send,
                workers,
                100,
                3,
                EmailConfig.Send.DEFAULT_BATCH_INITIAL_RETRY_DELAY_MS,
                EmailConfig.Send.DEFAULT_BATCH_MAX_RETRY_DELAY_MS,
                EmailConfig.Send.DEFAULT_BATCH_RETRY_BACKOFF_MULTIPLIER);
        EmailSendEngineFactory.Assembly assembly = EmailSendEngineFactory.assemble(
                new EmailBatchSenderFactory(emailConfig, nullSessionFactory()),
                emailDomainManager,
                send,
                options
        );
        EmailSendEngine engine = new EmailSendEngine(assembly);
        fixtures.put(engine, new EngineFixture(assembly.context(), assembly.dispatchProcessor(), assembly.executionCoordinator()));
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

    private DomainBatchTask task(String batchId, String domain, ExecutionMode mode, DispatchLane lane, int retryCount) {
        return task(batchId, domain, mode, lane, retryCount, false);
    }

    private DomainBatchTask task(String batchId, String domain, ExecutionMode mode, DispatchLane lane, int retryCount, boolean hasAttachment) {
        List<AttachmentMedia> attachments = hasAttachment
                ? List.of(new AttachmentMedia("sample.txt", "/tmp/sample.txt"))
                : List.of();
        return task(batchId, domain, mode, lane, retryCount, attachments);
    }

    private DomainBatchTask task(String batchId, String domain, ExecutionMode mode, DispatchLane lane, int retryCount, List<AttachmentMedia> attachments) {
        return task(batchId, domain, mode, lane, retryCount, attachments, null);
    }

    private DomainBatchTask task(String batchId, String domain, ExecutionMode mode, DispatchLane lane, int retryCount,
                                 List<AttachmentMedia> attachments, CompletableFuture<EmailBatchResult> resultFuture) {
        return new DomainBatchTask(
                List.of(EmailSendTarget.builder()
                        .targetEmail("user@" + domain)
                        .attachments(attachments)
                        .targetData(Collections.emptyMap())
                        .build()),
                domain,
                batchId,
                resultFuture,
                retryCount,
                "test-runner",
                null,
                null,
                mode,
                lane
        );
    }

    @SuppressWarnings("unchecked")
    private void setInFlight(EmailSendEngine engine, DispatchLane lane, int value) {
        Map<DispatchLane, AtomicInteger> map = fixtureOf(engine).context().runtimeState().inFlightByLane();
        map.computeIfAbsent(lane, key -> new AtomicInteger(0)).set(value);
    }

    private DomainBatchQueue getBatchQueue(EmailSendEngine engine) {
        return fixtureOf(engine).context().batchQueue();
    }

    private void setField(EmailSendEngine engine, String fieldName, long value) {
        if ("batchHoldStartedAtMs".equals(fieldName)) {
            fixtureOf(engine).context().runtimeState().setBatchHoldStartedAtMs(value);
            return;
        }
        throw new IllegalArgumentException("Unsupported field: " + fieldName);
    }

    private void setIntField(EmailSendEngine engine, String fieldName, int value) {
        if ("batchReleaseSmoothingCycles".equals(fieldName)) {
            fixtureOf(engine).context().runtimeState().setBatchReleaseSmoothingCycles(value);
            return;
        }
        throw new IllegalArgumentException("Unsupported field: " + fieldName);
    }

    private int invokeResolveBatchDispatchLimitPerCycle(EmailSendEngine engine, int maxWorkers, boolean hasRealtimeFreshPending) {
        return fixtureOf(engine).dispatchProcessor().resolveBatchDispatchLimitPerCycle(maxWorkers, hasRealtimeFreshPending);
    }

    private boolean invokeCanDispatchRetry(EmailSendEngine engine, DispatchLane retryLane, int maxWorkers) {
        return fixtureOf(engine).dispatchProcessor().canDispatchRetry(
                retryLane,
                maxWorkers,
                Collections.emptySet(),
                Collections.emptySet()
        );
    }

    private void invokeAdvanceBatchReleaseSmoothingCycle(EmailSendEngine engine) {
        fixtureOf(engine).dispatchProcessor().advanceBatchReleaseSmoothingCycle();
    }

    private boolean invokeExecuteBatch(EmailSendEngine engine, DomainBatchTask task) {
        return fixtureOf(engine).executionCoordinator().executeBatch(task, new java.util.HashSet<>());
    }

    private boolean invokeTryAcquireAttachmentSlot(EmailSendEngine engine, DomainBatchTask task) {
        EngineRuntimeContext context = fixtureOf(engine).context();
        return new ResourceGate(context, new RetryScheduler(context)).tryAcquireAttachmentSlot(task);
    }

    private void setAttachmentInFlight(EmailSendEngine engine, int value) {
        fixtureOf(engine).context().runtimeState().resourceSlotGroups()
                .get(ExecutionMode.REALTIME).attachmentInFlight().set(value);
    }

    private int getAttachmentInFlight(EmailSendEngine engine) {
        return fixtureOf(engine).context().runtimeState().resourceSlotGroups()
                .get(ExecutionMode.REALTIME).attachmentInFlight().get();
    }

    private EngineFixture fixtureOf(EmailSendEngine engine) {
        EngineFixture fixture = fixtures.get(engine);
        if (fixture == null) {
            throw new IllegalStateException("Missing fixture for engine");
        }
        return fixture;
    }

    private record EngineFixture(EngineRuntimeContext context, DispatchProcessor dispatchProcessor, ExecutionCoordinator executionCoordinator) {
    }
}
