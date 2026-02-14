package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchResult;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchTask;

/**
 * Reflects batch completion/failure results and updates the engine aggregation state.
 */
@Slf4j
final class BatchResultFinalizer {

    private final EngineRuntimeState runtimeState;
    private final Consumer<DomainBatchTask> waitTrackingRemover;

    BatchResultFinalizer(EngineRuntimeState runtimeState, Consumer<DomainBatchTask> waitTrackingRemover) {
        this.runtimeState = runtimeState;
        this.waitTrackingRemover = waitTrackingRemover;
    }

    /**
     * Marks the task as completed with failure and cleans up aggregation/wait tracking states.
     */
    void completeTaskAsFailure(DomainBatchTask task, Exception cause, String reason) {
        if (task == null || task.getResultFuture() == null) {
            return;
        }
        Exception effectiveCause = cause != null ? cause : new IllegalStateException("Task aborted: " + reason);
        EmailBatchResult result = EmailBatchResult.failure(task.getBatchId(), task.getDomain(), task.getSize(), effectiveCause);
        if (task.getResultFuture().complete(result)) {
            runtimeState.incrementFailedBatches();
            waitTrackingRemover.accept(task);
            log.warn("Batch [{}] completed as failure due to {}", task.getBatchId(), reason);
        }
    }

    /**
     * Records the successful batch results and updates the completion counter.
     */
    void handleBatchCompletion(DomainBatchTask task, Integer successCount, int retryTargetCount) {
        String domain = task.getDomain();
        String batchId = task.getBatchId();
        int totalTargets = task.getSize();

        waitTrackingRemover.accept(task);
        runtimeState.incrementCompletedBatches();

        if (retryTargetCount > 0) {
            log.info("Batch [{}] completed for domain: {}. Success: {}/{}, Retry exhausted: {}",
                    batchId, domain, successCount, totalTargets, retryTargetCount);
        } else {
            log.info("Batch [{}] completed for domain: {}. Success: {}/{}",
                    batchId, domain, successCount, totalTargets);
        }

        if (task.getResultFuture() != null) {
            EmailBatchResult result = EmailBatchResult.success(batchId, domain, totalTargets, successCount);
            task.getResultFuture().complete(result);
        }
    }

    /**
     * Records the batch that encountered an exception during execution as a failure.
     */
    void handleBatchFailure(DomainBatchTask task, Exception exception) {
        String domain = task.getDomain();
        String batchId = task.getBatchId();
        int totalTargets = task.getSize();

        waitTrackingRemover.accept(task);
        runtimeState.incrementFailedBatches();
        log.error("Batch [{}] failed for domain: {}", batchId, domain, exception);

        if (task.getResultFuture() != null) {
            EmailBatchResult result = EmailBatchResult.failure(batchId, domain, totalTargets, exception);
            task.getResultFuture().complete(result);
        }
    }
}
