package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.ExecutionMode;
import io.github.hotbrkm.smtpengine.agent.email.send.result.SendResult;
import io.github.hotbrkm.smtpengine.agent.email.send.worker.ResultApplier;
import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchQueue;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchTask;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.metrics.DomainSendMetrics;

/**
 * Handles acquisition/release of concurrency slots (attachment) per mail type and
 * wait/retry/termination processing upon slot shortage.
 */
@Slf4j
final class ResourceGate {

    private final EmailConfig.Send sendConfig;
    private final int maxRetryCount;
    private final Map<ExecutionMode, ResourceSlotGroup> slotGroups;
    private final EngineExecutors engineExecutors;
    private final long noSlotRequeueBaseDelayMs;
    private final long noSlotRequeueJitterMs;
    private final RunnerExecutionGuard runnerExecutionGuard;
    private final BatchResultFinalizer batchResultFinalizer;
    private final RetryScheduler retryScheduler;
    private final DomainBatchQueue batchQueue;
    private final int bindIpAllocationTimeoutCode;
    private final DomainSendMetrics domainSendMetrics;

    ResourceGate(EngineRuntimeContext context, RetryScheduler retryScheduler) {
        this.sendConfig = context.sendConfig();
        this.maxRetryCount = context.runtimeOptions().maxRetryCount();
        this.slotGroups = context.runtimeState().resourceSlotGroups();
        this.engineExecutors = context.engineExecutors();
        this.noSlotRequeueBaseDelayMs = context.noSlotRequeueBaseDelayMs();
        this.noSlotRequeueJitterMs = context.noSlotRequeueJitterMs();
        this.runnerExecutionGuard = context.runnerExecutionGuard();
        this.batchResultFinalizer = context.batchResultFinalizer();
        this.retryScheduler = retryScheduler;
        this.batchQueue = context.batchQueue();
        this.bindIpAllocationTimeoutCode = context.bindIpAllocationTimeoutCode();
        this.domainSendMetrics = context.domainSendMetrics();
    }

    /**
     * Acquires one attachment concurrency slot.
     */
    boolean tryAcquireAttachmentSlot(DomainBatchTask task) {
        if (!requiresAttachmentConcurrencyLimit(task)) {
            return true;
        }

        ResourceSlotGroup group = slotGroupFor(task);
        AtomicInteger inFlight = group.attachmentInFlight();
        int limit = group.attachmentMaxInFlight();

        while (true) {
            int current = inFlight.get();
            if (current >= limit) {
                return false;
            }
            if (inFlight.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    /**
     * Determines whether attachment slot limits apply.
     */
    boolean requiresAttachmentConcurrencyLimit(DomainBatchTask task) {
        if (task == null || !task.hasAttachment()) {
            return false;
        }
        return slotGroupFor(task).attachmentMaxInFlight() > 0;
    }

    /**
     * Conditionally releases the acquired attachment slot.
     */
    void releaseAttachmentSlotIfNeeded(DomainBatchTask task, boolean acquired) {
        if (!acquired || !requiresAttachmentConcurrencyLimit(task)) {
            return;
        }

        AtomicInteger inFlight = slotGroupFor(task).attachmentInFlight();
        int value = inFlight.decrementAndGet();
        if (value < 0) {
            inFlight.set(0);
        }
    }

    /**
     * Performs wait/retry/termination processing for attachment slot shortage situations.
     */
    void handleAttachmentLimitUnavailable(DomainBatchTask task) {
        ResourceSlotGroup group = slotGroupFor(task);
        handleResourceLimitUnavailable(task, group.attachmentWaitStartByBatchId(),
                Math.max(0L, sendConfig.getAttachmentAssignWaitTimeoutMs()), "attachment in-flight slot",
                "attachment_limit", group.attachmentMaxInFlight(), group.attachmentInFlight().get());
    }

    private ResourceSlotGroup slotGroupFor(DomainBatchTask task) {
        ExecutionMode mode = task.getExecutionMode();
        ResourceSlotGroup group = slotGroups.get(mode);
        if (group == null) {
            throw new IllegalStateException("No ResourceSlotGroup for ExecutionMode: " + mode);
        }
        return group;
    }

    private void handleResourceLimitUnavailable(DomainBatchTask task, Map<String, Long> waitStartByBatchId,
                                                long waitTimeoutMs, String resourceLabel, String sourcePrefix,
                                                int configuredLimit, int currentInFlight) {
        if (!validateRunnerTaskOrFail(task, sourcePrefix + "_drop_inactive_runner", sourcePrefix + "_drop_aborted_runner",
                "Dropped stale " + sourcePrefix + " task from inactive runner run")) {
            return;
        }

        String waitKey = waitTrackingKey(task);
        long now = System.currentTimeMillis();
        long waitStart = waitStartByBatchId.computeIfAbsent(waitKey, key -> now);
        long waitedMs = now - waitStart;
        if (waitedMs < waitTimeoutMs) {
            long delay = BatchFailureSupport.computeRequeueDelayMillis(noSlotRequeueBaseDelayMs, noSlotRequeueJitterMs);
            // Add jitter to requeue delay to mitigate multiple tasks re-entering at the same time.
            engineExecutors.schedule(() -> enqueueIfRunnable(task, sourcePrefix + "_requeue"), delay, TimeUnit.MILLISECONDS);
            log.debug("{} reached for batch [{}], domain={}, mode={}, limit={}, inFlight={}, waitedMs={}, waitTimeoutMs={}, requeueDelayMs={}",
                    resourceLabel, task.getBatchId(), task.getDomain(), task.getExecutionMode(),
                    configuredLimit, currentInFlight, waitedMs, waitTimeoutMs, delay);
            return;
        }

        waitStartByBatchId.remove(waitKey);
        if (task.getRetryCount() < maxRetryCount) {
            BatchFailureSupport.incrementRetryCount(task.getBatch());
            retryScheduler.submitResourceLimitRetryBatch(task, resourceLabel, sourcePrefix);
            return;
        }

        finalizeResourceLimitExhausted(task, waitedMs, resourceLabel);
    }

    private String waitTrackingKey(DomainBatchTask task) {
        return WaitTrackingSupport.waitTrackingKey(task);
    }

    private void enqueueIfRunnable(DomainBatchTask task, String source) {
        if (!validateRunnerTaskOrFail(task, source + "_drop_inactive_runner", source + "_drop_aborted_runner",
                "Dropped scheduled stale task from inactive runner run")) {
            return;
        }
        batchQueue.offer(task);
    }

    private void finalizeResourceLimitExhausted(DomainBatchTask task, long waitedMs, String resourceLabel) {
        int finalCode = BatchFailureSupport.resolveFinalStatusCode(task, bindIpAllocationTimeoutCode);
        String timeoutMessage = String.format(Locale.ROOT,
                "%d resource allocation timeout: no available %s for domain '%s' after %d ms (retry exhausted: %d/%d)",
                bindIpAllocationTimeoutCode, resourceLabel, task.getDomain(), waitedMs, task.getRetryCount(), maxRetryCount);
        String finalMessage = BatchFailureSupport.resolveFinalMessage(task, finalCode, bindIpAllocationTimeoutCode, timeoutMessage);

        ResultApplier applier = new ResultApplier(task.getRunnerId(), task.getResultWriter(), domainSendMetrics);
        for (EmailSendTarget target : task.getBatch()) {
            if (target.isUnprocessed()) {
                applier.apply(target, SendResult.failure(finalCode, finalMessage));
            }
        }

        log.warn("Batch [{}] exhausted while waiting {}. domain={}, mode={}, code={}, message={}",
                task.getBatchId(), resourceLabel, task.getDomain(), task.getExecutionMode(), finalCode, finalMessage);
        batchResultFinalizer.handleBatchCompletion(task, 0, task.getSize());
    }

    private boolean validateRunnerTaskOrFail(DomainBatchTask task, String inactiveReason, String abortedReason, String inactiveMessage) {
        return runnerExecutionGuard.validateOrFail(task, inactiveReason, abortedReason, inactiveMessage, this::completeTaskAsFailure);
    }

    private void completeTaskAsFailure(DomainBatchTask task, Exception cause, String reason) {
        batchResultFinalizer.completeTaskAsFailure(task, cause, reason);
    }
}
