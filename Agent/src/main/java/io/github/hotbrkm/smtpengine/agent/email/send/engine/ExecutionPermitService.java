package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;
import io.github.hotbrkm.smtpengine.agent.email.send.result.SendResult;
import io.github.hotbrkm.smtpengine.agent.email.send.worker.ResultApplier;
import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchQueue;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchTask;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.metrics.DomainSendMetrics;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.resource.BindIpLease;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.resource.BindIpSessionAllocator;

/**
 * Handles permit acquisition/release required for batch execution and post-processing for bind IP no-slot situations.
 */
@Slf4j
final class ExecutionPermitService {

    private final BindIpSessionAllocator bindIpSessionAllocator;
    private final ResourceGate resourceGate;
    private final EngineRuntimeState runtimeState;
    private final RunnerExecutionGuard runnerExecutionGuard;
    private final BatchResultFinalizer batchResultFinalizer;
    private final RetryScheduler retryScheduler;
    private final DomainBatchQueue batchQueue;
    private final EngineExecutors engineExecutors;
    private final EmailConfig.Send sendConfig;
    private final int maxRetryCount;
    private final int bindIpAllocationTimeoutCode;
    private final long noSlotRequeueBaseDelayMs;
    private final long noSlotRequeueJitterMs;
    private final DomainSendMetrics domainSendMetrics;

    ExecutionPermitService(EngineRuntimeContext context, ResourceGate resourceGate, RetryScheduler retryScheduler) {
        this.bindIpSessionAllocator = context.bindIpSessionAllocator();
        this.resourceGate = resourceGate;
        this.runtimeState = context.runtimeState();
        this.runnerExecutionGuard = context.runnerExecutionGuard();
        this.batchResultFinalizer = context.batchResultFinalizer();
        this.retryScheduler = retryScheduler;
        this.batchQueue = context.batchQueue();
        this.engineExecutors = context.engineExecutors();
        this.sendConfig = context.sendConfig();
        this.maxRetryCount = context.runtimeOptions().maxRetryCount();
        this.bindIpAllocationTimeoutCode = context.bindIpAllocationTimeoutCode();
        this.noSlotRequeueBaseDelayMs = context.noSlotRequeueBaseDelayMs();
        this.noSlotRequeueJitterMs = context.noSlotRequeueJitterMs();
        this.domainSendMetrics = context.domainSendMetrics();
    }

    /**
     * Sequentially acquires resources (attachment, bind IP) required for batch execution.
     * <p>
     * If any acquisition fails, immediately releases already acquired resources and proceeds to requeue/retry flow.
     *
     * @param task          Target batch for execution
     * @param noSlotDomains Set of domains with slot shortage in this dispatch cycle
     * @return Permit if all resources acquired, otherwise empty
     */
    Optional<ExecutionPermit> tryAcquire(DomainBatchTask task, Set<String> noSlotDomains) {
        String domain = task.getDomain();

        if (!resourceGate.tryAcquireAttachmentSlot(task)) {
            resourceGate.handleAttachmentLimitUnavailable(task);
            noSlotDomains.add(domain);
            return Optional.empty();
        }

        Optional<BindIpLease> leaseOptional = bindIpSessionAllocator.tryAcquire(domain);
        if (leaseOptional.isEmpty()) {
            // Release previously acquired attachment slot when bind IP acquisition fails.
            resourceGate.releaseAttachmentSlotIfNeeded(task, true);
            handleBindIpUnavailable(task);
            noSlotDomains.add(domain);
            return Optional.empty();
        }

        // On successful acquisition, cleans up wait tracking state to prevent stale wait info in the next attempt.
        WaitTrackingSupport.removeWaitTrackingForTask(runtimeState, task);
        return Optional.of(new ExecutionPermit(leaseOptional.get(), true));
    }

    /**
     * Releases all resources acquired by the permit upon execution termination.
     *
     * @param task   Executed batch
     * @param permit Acquired permit
     */
    void release(DomainBatchTask task, ExecutionPermit permit) {
        if (permit == null) {
            return;
        }
        bindIpSessionAllocator.release(permit.lease());
        resourceGate.releaseAttachmentSlotIfNeeded(task, permit.attachmentSlotAcquired());
    }

    /**
     * Checks if the task belongs to a valid runner at the scheduled requeue time, then adds it to the queue.
     * If invalid, completes it as a failure.
     */
    void enqueueIfRunnable(DomainBatchTask task, String source) {
        if (!validateRunnerTaskOrFail(task, source + "_drop_inactive_runner", source + "_drop_aborted_runner",
                "Dropped scheduled stale task from inactive runner run")) {
            return;
        }
        batchQueue.offer(task);
    }

    /**
     * Performs post-processing (wait requeue/retry/final failure) for tasks that failed to acquire a bind IP slot.
     */
    private void handleBindIpUnavailable(DomainBatchTask task) {
        if (!validateRunnerTaskOrFail(task, "no_slot_drop_inactive_runner", "no_slot_drop_aborted_runner",
                "Dropped stale no-slot task from inactive runner run")) {
            return;
        }

        String batchId = task.getBatchId();
        String waitKey = WaitTrackingSupport.waitTrackingKey(task);
        long now = System.currentTimeMillis();
        long waitStart = runtimeState.bindIpWaitStartByBatchId().computeIfAbsent(waitKey, k -> now);
        long waitedMs = now - waitStart;

        if (waitedMs < Math.max(0L, sendConfig.getBindIpAssignWaitTimeoutMs())) {
            long delay = BatchFailureSupport.computeRequeueDelayMillis(noSlotRequeueBaseDelayMs, noSlotRequeueJitterMs);
            engineExecutors.schedule(() -> enqueueIfRunnable(task, "no_slot_requeue"), delay, TimeUnit.MILLISECONDS);
            log.debug("No bind IP slot available for batch [{}], domain={}, waitedMs={}, requeueDelayMs={}",
                    batchId, task.getDomain(), waitedMs, delay);
            return;
        }

        WaitTrackingSupport.removeWaitTrackingForTask(runtimeState, task);
        if (task.getRetryCount() < maxRetryCount) {
            BatchFailureSupport.incrementRetryCount(task.getBatch());
            retryScheduler.submitNoSlotRetryBatch(task);
            return;
        }

        finalizeNoSlotExhausted(task, waitedMs);
    }

    /**
     * Finalizes the batch that has exhausted both bind IP wait and retries as a failure.
     */
    private void finalizeNoSlotExhausted(DomainBatchTask task, long waitedMs) {
        int finalCode = BatchFailureSupport.resolveFinalStatusCode(task, bindIpAllocationTimeoutCode);
        String timeoutMessage = String.format(Locale.ROOT,
                "%d bind IP allocation timeout: no available bind IP for domain '%s' after %d ms (retry exhausted: %d/%d)",
                bindIpAllocationTimeoutCode, task.getDomain(), waitedMs, task.getRetryCount(), maxRetryCount);
        String finalMessage = BatchFailureSupport.resolveFinalMessage(task, finalCode, bindIpAllocationTimeoutCode, timeoutMessage);

        ResultApplier applier = new ResultApplier(task.getRunnerId(), task.getResultWriter(), domainSendMetrics);
        for (EmailSendTarget target : task.getBatch()) {
            if (target.isUnprocessed()) {
                applier.apply(target, SendResult.failure(finalCode, finalMessage));
            }
        }

        log.warn("Batch [{}] exhausted while waiting bind IP slot. domain={}, code={}, message={}",
                task.getBatchId(), task.getDomain(), finalCode, finalMessage);
        batchResultFinalizer.handleBatchCompletion(task, 0, task.getSize());
    }

    private boolean validateRunnerTaskOrFail(DomainBatchTask task, String inactiveReason, String abortedReason, String inactiveMessage) {
        return runnerExecutionGuard.validateOrFail(task, inactiveReason, abortedReason, inactiveMessage, this::completeTaskAsFailure);
    }

    private void completeTaskAsFailure(DomainBatchTask task, Exception cause, String reason) {
        batchResultFinalizer.completeTaskAsFailure(task, cause, reason);
    }
}
