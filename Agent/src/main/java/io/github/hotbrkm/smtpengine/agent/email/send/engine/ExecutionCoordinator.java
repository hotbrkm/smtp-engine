package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;
import io.github.hotbrkm.smtpengine.agent.email.send.result.ResultPersistenceException;
import io.github.hotbrkm.smtpengine.agent.email.send.worker.EmailBatchSender;
import io.github.hotbrkm.smtpengine.agent.email.send.worker.EmailBatchSenderFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DispatchLane;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchTask;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.metrics.DomainSendMetrics;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.resource.BindIpLease;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.resource.BindIpSessionAllocator;

/**
 * Orchestrates the execution lifecycle of individual batches.
 * Handles permit acquisition, sender execution, result application, and resource return.
 */
@Slf4j
final class ExecutionCoordinator {

    private final ExecutionPermitService executionPermitService;

    private final int maxRetryCount;
    private final RunnerExecutionGuard runnerExecutionGuard;
    private final EngineRuntimeState runtimeState;
    private final EmailBatchSenderFactory emailBatchSenderFactory;
    private final EngineExecutors engineExecutors;

    private final RetryScheduler retryScheduler;
    private final BatchResultFinalizer batchResultFinalizer;
    private final BindIpSessionAllocator bindIpSessionAllocator;
    private final DomainSendMetrics domainSendMetrics;

    ExecutionCoordinator(EngineRuntimeContext context, EmailBatchSenderFactory emailBatchSenderFactory,
                         RetryScheduler retryScheduler, ResourceGate resourceGate) {
        this.executionPermitService = new ExecutionPermitService(context, resourceGate, retryScheduler);

        this.maxRetryCount = context.runtimeOptions().maxRetryCount();
        this.runnerExecutionGuard = context.runnerExecutionGuard();
        this.runtimeState = context.runtimeState();
        this.emailBatchSenderFactory = emailBatchSenderFactory;
        this.engineExecutors = context.engineExecutors();

        this.batchResultFinalizer = context.batchResultFinalizer();
        this.retryScheduler = retryScheduler;
        this.bindIpSessionAllocator = context.bindIpSessionAllocator();
        this.domainSendMetrics = context.domainSendMetrics();
    }

    /**
     * Starts the entire flow of a single batch execution (permit acquisition, sender execution, post-processing).
     *
     * @return {@code true} if execution actually starts, {@code false} if resource shortage or guard failure
     */
    boolean executeBatch(DomainBatchTask task, Set<String> noSlotDomains) {
        if (!runnerExecutionGuard.validateOrFail(task, "execute_drop_inactive_runner", "execute_drop_aborted_runner",
                "Dropped stale task before execution", this::completeTaskAsFailure)) {
            return false;
        }

        Optional<ExecutionPermit> permitOptional = executionPermitService.tryAcquire(task, noSlotDomains);
        if (permitOptional.isEmpty()) {
            return false;
        }

        ExecutionPermit permit = permitOptional.get();
        BindIpLease lease = permit.lease();
        log.info("Executing batch [{}] for domain: {} with {} targets, bindIp={}",
                task.getBatchId(), task.getDomain(), task.getBatch().size(), lease.bindIp());

        EmailBatchSender sender = emailBatchSenderFactory.create(task, lease.bindIp());
        Future<Integer> future;
        incrementInFlight(task.getDispatchLane());
        try {
            future = engineExecutors.submitWorker(sender);
        } catch (Exception submitException) {
            // Upon submit failure, in-flight and permit must be immediately reverted to enable the next schedule without leaks.
            decrementInFlight(task.getDispatchLane());
            executionPermitService.release(task, permit);
            throw submitException;
        }

        engineExecutors.supplyOnCompletion(() -> awaitBatchCompletion(task, sender, future, permit));

        return true;
    }

    /**
     * Waits for worker completion and finalizes completion/retry/failure.
     * In the finally block, permit/in-flight are always returned.
     */
    private Integer awaitBatchCompletion(DomainBatchTask task, EmailBatchSender sender, Future<Integer> future, ExecutionPermit permit) {
        try {
            Integer successCount = future.get();
            applyCooldownPolicy(task, sender, successCount);

            if (isTaskAborted(task)) {
                completeTaskAsFailure(task, getAbortCause(task), "batch_completed_after_runner_abort");
                return successCount;
            }

            List<EmailSendTarget> retryTargets = sender.extractRetryTargets();
            if (!retryTargets.isEmpty()) {
                domainSendMetrics.recordRetry(task.getDomain(), retryTargets.size());
            }
            if (!retryTargets.isEmpty() && task.getRetryCount() < maxRetryCount) {
                sender.incrementRetryCount(retryTargets);
                retryScheduler.submitRetryBatch(task, retryTargets);
            } else {
                batchResultFinalizer.handleBatchCompletion(task, successCount, retryTargets.size());
            }

            return successCount;
        } catch (Exception e) {
            ResultPersistenceException persistenceException = extractResultPersistenceException(e);
            if (persistenceException != null) {
                abortRunner(task, persistenceException);
            }
            batchResultFinalizer.handleBatchFailure(task, e);
            return 0;
        } finally {
            decrementInFlight(task.getDispatchLane());
            executionPermitService.release(task, permit);
        }
    }

    /**
     * Updates the bind IP cooldown policy based on batch results.
     */
    private void applyCooldownPolicy(DomainBatchTask task, EmailBatchSender sender, Integer successCount) {
        String bindIp = sender.getBindIp();
        if (bindIp == null || bindIp.isBlank()) {
            return;
        }

        boolean hasAnySuccess = sender.hasAnySuccess() || (successCount != null && successCount > 0);
        int statusCode = sender.getLastErrorStatusCode();
        if (statusCode <= 0) {
            return;
        }

        bindIpSessionAllocator.recordBatchResult(task.getDomain(), bindIp, statusCode, hasAnySuccess);
    }

    private void incrementInFlight(DispatchLane lane) {
        if (lane == null) {
            return;
        }
        runtimeState.inFlightByLane().computeIfAbsent(lane, key -> new AtomicInteger(0)).incrementAndGet();
    }

    private void decrementInFlight(DispatchLane lane) {
        if (lane == null) {
            return;
        }
        AtomicInteger counter = runtimeState.inFlightByLane().get(lane);
        if (counter == null) {
            return;
        }
        int value = counter.decrementAndGet();
        if (value < 0) {
            counter.set(0);
        }
    }

    private boolean isTaskAborted(DomainBatchTask task) {
        return runnerExecutionGuard.isTaskAborted(task);
    }

    private Exception getAbortCause(DomainBatchTask task) {
        return runnerExecutionGuard.getAbortCause(task);
    }

    private void completeTaskAsFailure(DomainBatchTask task, Exception cause, String reason) {
        batchResultFinalizer.completeTaskAsFailure(task, cause, reason);
    }

    void abortRunner(DomainBatchTask failedTask, ResultPersistenceException persistenceException) {
        runnerExecutionGuard.abortRunner( failedTask, persistenceException,
                runnerId -> WaitTrackingSupport.removeWaitTrackingForRunner(runtimeState, runnerId),
                this::completeTaskAsFailure
        );
    }

    private ResultPersistenceException extractResultPersistenceException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ResultPersistenceException persistenceException) {
                return persistenceException;
            }
            current = current.getCause();
        }
        return null;
    }
}
