package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomain;
import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import io.github.hotbrkm.smtpengine.agent.email.mime.AttachmentMedia;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.ExecutionMode;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;
import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DispatchLane;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchTask;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResourceGate ExecutionMode isolation test")
class ResourceGateExecutionModeIsolationTest {

    private ResourceGate resourceGate;
    private EngineRuntimeState runtimeState;
    private EmailSendEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    @DisplayName("When BATCH exhausts attachment limit, REALTIME can still acquire")
    void batchExhausted_realtimeStillAcquiresAttachmentSlot() {
        setup(send -> {
            send.setRealtimeAttachmentMaxInFlight(2);
            send.setBatchAttachmentMaxInFlight(1);
        });

        DomainBatchTask batchTask = attachmentTask("batch-1", ExecutionMode.BATCH);
        DomainBatchTask rtTask = attachmentTask("rt-1", ExecutionMode.REALTIME);

        assertThat(resourceGate.tryAcquireAttachmentSlot(batchTask)).isTrue();
        assertThat(resourceGate.tryAcquireAttachmentSlot(batchTask)).isFalse();

        assertThat(resourceGate.tryAcquireAttachmentSlot(rtTask)).isTrue();
        assertThat(resourceGate.tryAcquireAttachmentSlot(rtTask)).isTrue();
        assertThat(resourceGate.tryAcquireAttachmentSlot(rtTask)).isFalse();
    }

    @Test
    @DisplayName("Per-mode independent counter — acquire/release in one mode does not affect other mode")
    void releaseInOneMode_doesNotAffectOtherMode() {
        setup(send -> {
            send.setRealtimeAttachmentMaxInFlight(2);
            send.setBatchAttachmentMaxInFlight(2);
        });

        DomainBatchTask rtTask = attachmentTask("rt-1", ExecutionMode.REALTIME);
        DomainBatchTask batchTask = attachmentTask("batch-1", ExecutionMode.BATCH);

        // Acquire 2 slots in REALTIME
        assertThat(resourceGate.tryAcquireAttachmentSlot(rtTask)).isTrue();
        assertThat(resourceGate.tryAcquireAttachmentSlot(rtTask)).isTrue();
        assertThat(resourceGate.tryAcquireAttachmentSlot(rtTask)).isFalse();

        // Release 1 slot in REALTIME
        resourceGate.releaseAttachmentSlotIfNeeded(rtTask, true);

        // BATCH counter unaffected — still at 0, so 2 acquires possible
        assertThat(resourceGate.tryAcquireAttachmentSlot(batchTask)).isTrue();
        assertThat(resourceGate.tryAcquireAttachmentSlot(batchTask)).isTrue();
        assertThat(resourceGate.tryAcquireAttachmentSlot(batchTask)).isFalse();

        // Verify REALTIME counter — 1 slot remaining after release, so only 1 acquire possible
        assertThat(resourceGate.tryAcquireAttachmentSlot(rtTask)).isTrue();
        assertThat(resourceGate.tryAcquireAttachmentSlot(rtTask)).isFalse();
    }

    @Test
    @DisplayName("Global fallback — when per-mode setting is 0, uses global value")
    void globalFallback_whenPerModeIsZero_usesGlobalValue() {
        setup(send -> {
            send.setAttachmentMaxInFlight(3);
            // Per-mode is 0 (default) → fallback to global value 3
        });

        DomainBatchTask rtTask = attachmentTask("rt-1", ExecutionMode.REALTIME);
        DomainBatchTask batchTask = attachmentTask("batch-1", ExecutionMode.BATCH);

        // REALTIME: global limit 3
        for (int i = 0; i < 3; i++) {
            assertThat(resourceGate.tryAcquireAttachmentSlot(rtTask)).isTrue();
        }
        assertThat(resourceGate.tryAcquireAttachmentSlot(rtTask)).isFalse();

        // BATCH: global limit 3 (independent counter)
        for (int i = 0; i < 3; i++) {
            assertThat(resourceGate.tryAcquireAttachmentSlot(batchTask)).isTrue();
        }
        assertThat(resourceGate.tryAcquireAttachmentSlot(batchTask)).isFalse();
    }

    @Test
    @DisplayName("Zero limit allows all passes without restriction")
    void zeroLimit_alwaysAcquires() {
        setup(send -> {
            // Global is 0, per-mode is 0 → no limit
        });

        DomainBatchTask batchTask = attachmentTask("batch-1", ExecutionMode.BATCH);

        assertThat(resourceGate.tryAcquireAttachmentSlot(batchTask)).isTrue();
    }

    private void setup(java.util.function.Consumer<EmailConfig.Send> sendCustomizer) {
        EmailConfig emailConfig = new EmailConfig();
        EmailConfig.Send send = emailConfig.getSend();
        send.setBindAddresses(List.of("127.0.0.1"));
        send.setRetryExcludeCode(List.of("250"));
        send.setDnsServer(List.of());
        send.setExcludedDomain(List.of());
        send.setFixedIpOfDomain(new java.util.HashMap<>());
        send.setTlsEnabledProtocols(List.of("TLSv1.2"));
        send.setTlsApplyScope("NONE");
        send.setTlsApplyDomain(List.of());
        if (sendCustomizer != null) {
            sendCustomizer.accept(send);
        }

        EngineRuntimeOptions options = EngineRuntimeOptions.fromExplicit(send, 2, 100, 3,
                EmailConfig.Send.DEFAULT_BATCH_INITIAL_RETRY_DELAY_MS,
                EmailConfig.Send.DEFAULT_BATCH_MAX_RETRY_DELAY_MS,
                EmailConfig.Send.DEFAULT_BATCH_RETRY_BACKOFF_MULTIPLIER);

        EmailDomainManager emailDomainManager = new EmailDomainManager(
                List.of(new EmailDomain("default", 1, 10, 60, 60, "")),
                LocalDateTime.now());
        EngineRuntimeContext context = EngineRuntimeContext.initialize(send, options, emailDomainManager,
                EmailSendEngineFactory.BIND_IP_ALLOCATION_TIMEOUT_CODE,
                EmailSendEngineFactory.DEFAULT_NO_SLOT_REQUEUE_BASE_DELAY_MS,
                EmailSendEngineFactory.DEFAULT_NO_SLOT_REQUEUE_JITTER_MS);

        this.runtimeState = context.runtimeState();
        RetryScheduler retryScheduler = new RetryScheduler(context);
        this.resourceGate = new ResourceGate(context, retryScheduler);
    }

    private DomainBatchTask attachmentTask(String batchId, ExecutionMode mode) {
        return new DomainBatchTask(
                List.of(EmailSendTarget.builder()
                        .targetEmail("user@test.com")
                        .attachments(List.of(new AttachmentMedia("file.txt", "/tmp/file.txt")))
                        .targetData(Collections.emptyMap())
                        .build()),
                "test.com", batchId, new CompletableFuture<>(), 0, "test-runner", null, null,
                mode, mode == ExecutionMode.REALTIME ? DispatchLane.REALTIME_FRESH : DispatchLane.BATCH_FRESH
        );
    }
}
