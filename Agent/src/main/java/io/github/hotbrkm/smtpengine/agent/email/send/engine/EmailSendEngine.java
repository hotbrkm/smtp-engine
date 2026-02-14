package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.send.planning.EmailBatchSpec;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchQueue;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchTask;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.metrics.DomainMetricSnapshot;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.metrics.DomainSendMetrics;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.resource.BindIpSessionAllocator;

/**
 * Email sending engine class
 * <p>
 * Key features:
 * 1. Per-domain batch queue management
 * 2. Concurrent session control based on Domain/Bind IP
 * 3. Parallel processing using Thread Pool
 * 4. Batch sending status tracking
 */
@Slf4j
public class EmailSendEngine {

    private final BatchSubmissionService batchSubmissionService;
    private final BatchResultFinalizer batchResultFinalizer;
    private final RunnerExecutionGuard runnerExecutionGuard;

    private final int schedulerIntervalMs;
    private final DispatchProcessor dispatchProcessor;

    private final EngineRuntimeState runtimeState;
    private final DomainBatchQueue batchQueue;
    private final BindIpSessionAllocator bindIpSessionAllocator;
    private final EngineExecutors engineExecutors;
    private final DomainSendMetrics domainSendMetrics;

    private volatile boolean isRunning = false;

    EmailSendEngine(EmailSendEngineFactory.Assembly assembly) {
        EngineRuntimeContext context = assembly.context();

        this.batchResultFinalizer = context.batchResultFinalizer();
        this.batchSubmissionService = context.batchSubmissionService();
        this.runnerExecutionGuard = context.runnerExecutionGuard();

        this.schedulerIntervalMs = context.runtimeOptions().schedulerIntervalMs();
        this.dispatchProcessor = assembly.dispatchProcessor();

        this.runtimeState = context.runtimeState();
        this.batchQueue = context.batchQueue();
        this.bindIpSessionAllocator = context.bindIpSessionAllocator();
        this.engineExecutors = context.engineExecutors();
        this.domainSendMetrics = context.domainSendMetrics();

        EngineRuntimeOptions runtimeOptions = context.runtimeOptions();
        log.info("EmailSendEngine initialized with workers={}, bindIpCount={}, schedulerIntervalMs={}, maxRetryCount={}",
                runtimeOptions.workerCount(), runtimeOptions.bindIps().size(),
                runtimeOptions.schedulerIntervalMs(), runtimeOptions.maxRetryCount());
        log.debug("EmailSendEngine tuning: bindIps={}, attachmentMaxInFlight[RT={}, BA={}], attachmentAssignWaitTimeoutMs={}, initialRetryDelayMs={}, maxRetryDelayMs={}, retryBackoffMultiplier={}",
                runtimeOptions.bindIps(),
                runtimeOptions.realtimeAttachmentMaxInFlight(), runtimeOptions.batchAttachmentMaxInFlight(),
                runtimeOptions.attachmentAssignWaitTimeoutMs(),
                runtimeOptions.initialRetryDelayMs(), runtimeOptions.maxRetryDelayMs(), runtimeOptions.retryBackoffMultiplier());
    }

    /**
     * Starts the dispatch scheduler.
     */
    public void start() {
        if (isRunning) {
            log.warn("Engine is already running");
            return;
        }
        isRunning = true;
        log.info("Starting EmailSendEngine...");
        engineExecutors.scheduleAtFixedRate(this::dispatchOnce, 0, schedulerIntervalMs, TimeUnit.MILLISECONDS);
        engineExecutors.scheduleAtFixedRate(domainSendMetrics::evict, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * Submits a batch asynchronously.
     */
    public CompletableFuture<EmailBatchResult> submitBatchAsync(EmailBatchSpec emailBatchSpec) {
        return batchSubmissionService.submitBatchAsync(emailBatchSpec);
    }

    /**
     * Registers a runner and marks stale tasks from previous runs as failed.
     */
    public void registerRunner(String runnerId, Object runnerToken) {
        List<DomainBatchTask> staleTasks = runnerExecutionGuard.registerRunner(runnerId, runnerToken);
        for (DomainBatchTask staleTask : staleTasks) {
            batchResultFinalizer.completeTaskAsFailure(staleTask,
                    new IllegalStateException("Dropped stale queued task from previous runner run"), "register_runner_run_drop_stale");
        }
    }

    /**
     * Unregisters a runner.
     */
    public void unregisterRunner(String runnerId) {
        runnerExecutionGuard.unregisterRunner(runnerId);
    }

    private void dispatchOnce() {
        dispatchProcessor.dispatchOnce(isRunning);
    }

    public int getCompletedBatches() {
        return runtimeState.completedBatches();
    }

    public int getFailedBatches() {
        return runtimeState.failedBatches();
    }

    public int getQueuedBatches() {
        return batchQueue.getTotalBatchCount();
    }

    public int getActiveSessions() {
        return bindIpSessionAllocator.getTotalActiveSessionCount();
    }

    public String getStatus() {
        if (!isRunning) {
            return "Stopped";
        }
        if (batchQueue.isEmpty() && bindIpSessionAllocator.getTotalActiveSessionCount() == 0) {
            return "Completed";
        }
        return "Running";
    }

    /**
     * Returns a snapshot of sending metrics for a specific domain over the last 'seconds'.
     */
    public DomainMetricSnapshot getDomainMetrics(String domain, int seconds) {
        return domainSendMetrics.snapshot(domain, seconds);
    }

    /**
     * Returns snapshots of sending metrics for all domains over the last 'seconds'.
     */
    public Map<String, DomainMetricSnapshot> getAllDomainMetrics(int seconds) {
        return domainSendMetrics.snapshotAll(seconds);
    }

    /**
     * Shuts down all executors and waits for remaining tasks to terminate.
     */
    public void shutdown() {
        log.info("Shutting down EmailSendEngine...");
        isRunning = false;
        engineExecutors.shutdown();
        log.info("EmailSendEngine shut down completed");
    }
}
