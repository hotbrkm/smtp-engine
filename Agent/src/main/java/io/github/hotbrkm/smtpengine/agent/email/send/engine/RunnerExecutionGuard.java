package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.send.result.ResultPersistenceException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchQueue;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchTask;

/**
 * Guard that validates the validity/aborted state of runner execution units.
 */
@Slf4j
final class RunnerExecutionGuard {

    private final DomainBatchQueue batchQueue;
    private final Map<String, RunnerAbortState> abortedRunnerStates = new ConcurrentHashMap<>();
    private final Map<String, Object> activeRunnerTokens = new ConcurrentHashMap<>();

    RunnerExecutionGuard(DomainBatchQueue batchQueue) {
        this.batchQueue = batchQueue;
    }

    /**
     * Registers the runner as active and reclaims queue tasks remaining from the previous execution.
     */
    List<DomainBatchTask> registerRunner(String runnerId, Object runnerToken) {
        if (runnerId == null || runnerId.isBlank() || runnerToken == null) {
            return List.of();
        }

        activeRunnerTokens.put(runnerId, runnerToken);
        abortedRunnerStates.remove(runnerId);
        return batchQueue.removeByRunnerId(runnerId);
    }

    /**
     * Removes runner state tracking information.
     */
    void unregisterRunner(String runnerId) {
        if (runnerId == null || runnerId.isBlank()) {
            return;
        }
        activeRunnerTokens.remove(runnerId);
        abortedRunnerStates.remove(runnerId);
    }

    /**
     * Registers the runner token only once at submit time.
     */
    void trackRunnerTokenIfAbsent(String runnerId, Object runnerToken) {
        if (runnerId == null || runnerId.isBlank() || runnerToken == null) {
            return;
        }
        activeRunnerTokens.putIfAbsent(runnerId, runnerToken);
    }

    /**
     * Validates if the task belongs to the currently active runner execution.
     */
    boolean isTaskFromActiveRunner(DomainBatchTask task) {
        if (task == null) {
            return false;
        }
        String runnerId = task.getRunnerId();
        Object activeToken = activeRunnerTokens.get(runnerId);
        if (activeToken == null) {
            return true;
        }
        return task.getResultWriter() == activeToken;
    }

    /**
     * Checks if the task belongs to an aborted runner execution.
     */
    boolean isTaskAborted(DomainBatchTask task) {
        if (task == null) {
            return false;
        }
        String runnerId = task.getRunnerId();
        RunnerAbortState state = abortedRunnerStates.get(runnerId);
        return state != null && state.runnerToken() == task.getResultWriter();
    }

    /**
     * Retrieves the abort cause. Returns a default exception if states do not match.
     */
    Exception getAbortCause(DomainBatchTask task) {
        if (task == null || task.getRunnerId() == null || task.getRunnerId().isBlank()) {
            return new IllegalStateException("Runner aborted");
        }
        RunnerAbortState state = abortedRunnerStates.get(task.getRunnerId());
        if (state == null || state.runnerToken() != task.getResultWriter()) {
            return new IllegalStateException("Runner aborted");
        }
        return state.cause();
    }

    /**
     * Validates if the task is in an executable runner state, and immediately terminates via the provided handler upon failure.
     */
    boolean validateOrFail(DomainBatchTask task, String inactiveReason, String abortedReason, String inactiveMessage,
                           TaskFailureHandler taskFailureHandler) {
        if (!isTaskFromActiveRunner(task)) {
            String message = (inactiveMessage == null || inactiveMessage.isBlank())
                    ? "Dropped stale task from inactive runner run"
                    : inactiveMessage;
            taskFailureHandler.onFailure(task, new IllegalStateException(message), inactiveReason);
            return false;
        }
        if (isTaskAborted(task)) {
            taskFailureHandler.onFailure(task, getAbortCause(task), abortedReason);
            return false;
        }
        return true;
    }

    /**
     * Aborts the runner upon result persistence failure and marks waiting tasks of the same runner as failed.
     */
    void abortRunner(DomainBatchTask failedTask, ResultPersistenceException persistenceException,
                     Consumer<String> waitTrackingRemoverForRunner, TaskFailureHandler taskFailureHandler) {
        if (failedTask == null || failedTask.getRunnerId() == null || failedTask.getRunnerId().isBlank()) {
            return;
        }

        String runnerId = failedTask.getRunnerId();
        Object runnerToken = failedTask.getResultWriter();
        RunnerAbortState newState = new RunnerAbortState(runnerToken, persistenceException);
        RunnerAbortState existing = abortedRunnerStates.putIfAbsent(runnerId, newState);
        if (existing != null && existing.runnerToken() == runnerToken) {
            return;
        }
        if (existing != null) {
            abortedRunnerStates.put(runnerId, newState);
        }

        // Immediately removes batches remaining in the queue with the same runner + token to prevent further processing entry.
        List<DomainBatchTask> removedTasks = batchQueue.removeByRunnerId(runnerId, runnerToken);
        for (DomainBatchTask removedTask : removedTasks) {
            taskFailureHandler.onFailure(removedTask, persistenceException, "runner_abort_remove_queued");
        }
        waitTrackingRemoverForRunner.accept(runnerId);

        log.error("Runner [{}] aborted due to checkpoint persistence failure. failedBatchId={}, removedQueuedBatches={}",
                runnerId, failedTask.getBatchId(), removedTasks.size(), persistenceException);
    }

    @FunctionalInterface
    interface TaskFailureHandler {
        void onFailure(DomainBatchTask task, Exception cause, String reason);
    }

    private record RunnerAbortState(Object runnerToken, Exception cause) {
    }
}
