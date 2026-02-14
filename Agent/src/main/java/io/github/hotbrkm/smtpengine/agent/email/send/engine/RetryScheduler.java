package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.ExecutionMode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.TimeUnit;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DispatchLane;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchQueue;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchTask;

/**
 * Component responsible for creating and scheduling retry batches with delay.
 */
@Slf4j
final class RetryScheduler {

    private final EngineExecutors engineExecutors;
    private final DomainBatchQueue batchQueue;
    private final RunnerExecutionGuard runnerExecutionGuard;
    private final BatchResultFinalizer batchResultFinalizer;

    private final long initialRetryDelayMillis;
    private final long maxRetryDelayMillis;
    private final double retryBackoffMultiplier;

    RetryScheduler(EngineRuntimeContext context) {
        this.engineExecutors = context.engineExecutors();
        this.batchQueue = context.batchQueue();
        this.runnerExecutionGuard = context.runnerExecutionGuard();
        this.batchResultFinalizer = context.batchResultFinalizer();

        this.initialRetryDelayMillis = context.runtimeOptions().initialRetryDelayMs();
        this.maxRetryDelayMillis = context.runtimeOptions().maxRetryDelayMs();
        this.retryBackoffMultiplier = context.runtimeOptions().retryBackoffMultiplier();
    }

    /**
     * Schedules a standard retry batch for only the partial failure targets.
     */
    void submitRetryBatch(DomainBatchTask originalTask, List<EmailSendTarget> retryTargets) {
        if (!validateRunnerTaskOrFail(originalTask, "batch_retry_skip_inactive_runner",
                "batch_retry_skip_aborted_runner", "Skip batch retry for inactive runner run")) {
            return;
        }

        String batchId = originalTask.getBatchId();
        int nextRetryCount = originalTask.getRetryCount() + 1;
        DomainBatchTask retryTask = buildRetryTask(originalTask, retryTargets, nextRetryCount);
        long delayMillis = scheduleRetryTask(retryTask, nextRetryCount, "batch_retry");
        String retryBatchId = retryTask.getBatchId();
        log.info("Batch [{}] has {} retry targets. Scheduling retry batch [{}] after {} ms (retry count: {})",
                batchId, retryTargets.size(), retryBatchId, delayMillis, nextRetryCount);
    }

    /**
     * Registers the entire batch to the retry queue after bind IP no-slot exhaustion.
     */
    void submitNoSlotRetryBatch(DomainBatchTask originalTask) {
        if (!validateRunnerTaskOrFail(originalTask, "no_slot_retry_skip_inactive_runner",
                "no_slot_retry_skip_aborted_runner", "Skip no-slot retry for inactive runner run")) {
            return;
        }

        String batchId = originalTask.getBatchId();
        int nextRetryCount = originalTask.getRetryCount() + 1;
        DomainBatchTask retryTask = buildRetryTask(originalTask, originalTask.getBatch(), nextRetryCount);
        long delayMillis = scheduleRetryTask(retryTask, nextRetryCount, "no_slot_retry");
        String retryBatchId = retryTask.getBatchId();
        log.info("Batch [{}] bind IP wait timeout. Scheduling retry batch [{}] after {} ms (retry count: {})",
                batchId, retryBatchId, delayMillis, nextRetryCount);
    }

    /**
     * Registers the resource (secure-email/attachment) wait timeout batch to the retry queue.
     */
    void submitResourceLimitRetryBatch(DomainBatchTask originalTask, String resourceLabel, String sourcePrefix) {
        String batchId = originalTask.getBatchId();
        int nextRetryCount = originalTask.getRetryCount() + 1;
        DomainBatchTask retryTask = buildRetryTask(originalTask, originalTask.getBatch(), nextRetryCount);
        long delayMillis = scheduleRetryTask(retryTask, nextRetryCount, sourcePrefix + "_retry");
        String retryBatchId = retryTask.getBatchId();
        log.info("Batch [{}] {} wait timeout. Scheduling retry batch [{}] after {} ms (retry count: {})",
                batchId, resourceLabel, retryBatchId, delayMillis, nextRetryCount);
    }

    /**
     * Calculates retry delay time using exponential backoff rules.
     */
    long computeRetryDelayMillis(int retryCount) {
        if (retryCount <= 1) {
            return initialRetryDelayMillis;
        }
        double factor = Math.pow(retryBackoffMultiplier, retryCount - 1);
        long candidate = (long) (initialRetryDelayMillis * factor);
        if (candidate < 0L) {
            candidate = Long.MAX_VALUE;
        }
        return Math.min(candidate, maxRetryDelayMillis);
    }

    private DomainBatchTask buildRetryTask(DomainBatchTask originalTask, List<EmailSendTarget> targets, int retryCount) {
        ExecutionMode executionMode = originalTask.getExecutionMode();
        DispatchLane retryLane = DispatchLane.forRetry(executionMode);
        String retryBatchId = originalTask.getBatchId() + "-retry" + retryCount;
        return new DomainBatchTask(targets, originalTask.getDomain(), retryBatchId, originalTask.getResultFuture(),
                retryCount, originalTask.getRunnerId(), originalTask.getResultWriter(), originalTask.getEmailSendContext(),
                executionMode, retryLane);
    }

    private long scheduleRetryTask(DomainBatchTask retryTask, int retryCount, String source) {
        long delayMillis = computeRetryDelayMillis(retryCount);
        engineExecutors.schedule(() -> enqueueIfRunnable(retryTask, source), delayMillis, TimeUnit.MILLISECONDS);
        return delayMillis;
    }

    private void enqueueIfRunnable(DomainBatchTask task, String source) {
        if (!validateRunnerTaskOrFail(task, source + "_drop_inactive_runner", source + "_drop_aborted_runner",
                "Dropped scheduled stale task from inactive runner run")) {
            return;
        }
        batchQueue.offer(task);
    }

    private boolean validateRunnerTaskOrFail(DomainBatchTask task, String inactiveReason, String abortedReason, String inactiveMessage) {
        return runnerExecutionGuard.validateOrFail(task, inactiveReason, abortedReason, inactiveMessage, this::completeTaskAsFailure);
    }

    private void completeTaskAsFailure(DomainBatchTask task, Exception cause, String reason) {
        batchResultFinalizer.completeTaskAsFailure(task, cause, reason);
    }
}
