package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DispatchLane;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchQueue;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchTask;

/**
 * Dispatch processor that selects domain batches based on worker pool status and queue conditions and delivers them to the executor.
 * Performs Realtime/Batch, Fresh/Retry lane policies, and runner validation together.
 */
@Slf4j
final class DispatchProcessor {

    private static final int DEFAULT_BATCH_HOLD_MULTIPLIER = 2;
    private static final int DEFAULT_BATCH_RELEASE_SMOOTHING_CYCLES = 1;
    private static final List<DispatchLane> REALTIME_ONLY_POLL_ORDER = List.of(DispatchLane.REALTIME_FRESH, DispatchLane.REALTIME_RETRY);
    private static final List<DispatchLane> FULL_POLL_ORDER = List.of(DispatchLane.REALTIME_FRESH, DispatchLane.REALTIME_RETRY, DispatchLane.BATCH_FRESH, DispatchLane.BATCH_RETRY);

    private final DomainBatchQueue batchQueue;
    private final EngineExecutors engineExecutors;
    private final EmailConfig.Send sendConfig;
    private final EngineRuntimeState runtimeState;
    private final int schedulerIntervalMs;
    private final RunnerExecutionGuard runnerExecutionGuard;
    private final BatchResultFinalizer batchResultFinalizer;
    private final BiPredicate<DomainBatchTask, Set<String>> batchExecutor;

    DispatchProcessor(EngineRuntimeContext context, BiPredicate<DomainBatchTask, Set<String>> batchExecutor) {
        this.batchQueue = context.batchQueue();
        this.engineExecutors = context.engineExecutors();
        this.sendConfig = context.sendConfig();
        this.runtimeState = context.runtimeState();
        this.schedulerIntervalMs = context.runtimeOptions().schedulerIntervalMs();
        this.runnerExecutionGuard = context.runnerExecutionGuard();
        this.batchResultFinalizer = context.batchResultFinalizer();
        this.batchExecutor = batchExecutor;
    }

    /**
     * Retrieves executable batches from the queue and dispatches them to the worker pool during one scheduler cycle.
     * <p>
     * Determines the maximum dispatch count for this cycle reflecting realtime backlog, batch hold/relax policies, and runner validity (active/aborted).
     *
     * @param running Engine execution state (dispatch only when true)
     */
    void dispatchOnce(boolean running) {
        if (!running) {
            return;
        }

        try {
            DispatchCycle cycle = initializeCycle();
            dispatchBatches(cycle);
            finalizeCycle(cycle);
        } catch (Exception e) {
            log.error("Error processing queue", e);
        }
    }

    private DispatchCycle initializeCycle() {
        int maxWorkers = engineExecutors.maxWorkerCount();
        DispatchCycle cycle = new DispatchCycle(maxWorkers, Math.max(10, maxWorkers));

        // Must reflect Realtime Fresh waiting status first to ensure consistent batch hold/relax policy for this cycle.
        boolean hasRealtimeFreshAtStart = batchQueue.hasAvailableBatch(DispatchLane.REALTIME_FRESH, cycle.realtimeNoSlotDomains());
        updateBatchHoldState(hasRealtimeFreshAtStart);
        cycle.setBatchDispatchLimit(resolveBatchDispatchLimitPerCycle(maxWorkers, hasRealtimeFreshAtStart));

        return cycle;
    }

    private void dispatchBatches(DispatchCycle cycle) {
        while (cycle.canDispatchMore()) {
            if (!hasAvailableWorkerSlot(cycle)) {
                break;
            }

            DomainBatchTask domainBatchTask = pollNextTask(cycle.realtimeNoSlotDomains(), cycle.batchNoSlotDomains(),
                    cycle.batchDispatchedThisCycle(), cycle.batchDispatchLimit(), cycle.maxWorkers());
            if (domainBatchTask == null) {
                break;
            }

            // Stale/aborted runner tasks are not entered into the execution path and are immediately terminated as failures.
            if (!validateRunnerTask(domainBatchTask)) {
                continue;
            }

            if (tryDispatch(domainBatchTask, cycle)) {
                cycle.markDispatched(isBatchLane(domainBatchTask));
            }
        }
    }

    private boolean hasAvailableWorkerSlot(DispatchCycle cycle) {
        int activeWorkers = engineExecutors.activeWorkerCount();
        if (activeWorkers >= cycle.maxWorkers()) {
            if (cycle.dispatched() == 0) {
                log.trace("Worker pool is full ({}/{}), waiting for available slot", activeWorkers, cycle.maxWorkers());
            }
            return false;
        }
        return true;
    }

    private boolean validateRunnerTask(DomainBatchTask domainBatchTask) {
        return runnerExecutionGuard.validateOrFail(domainBatchTask, "drop_inactive_runner_task", "drop_aborted_runner_task",
                "Dropped stale task from inactive runner run",
                this::completeTaskAsFailure);
    }

    private boolean tryDispatch(DomainBatchTask domainBatchTask, DispatchCycle cycle) {
        // Separates no-slot domains per lane to reduce excessive retry/re-polling in the same cycle.
        Set<String> noSlotDomains = isRealtimeLane(domainBatchTask)
                ? cycle.realtimeNoSlotDomains()
                : cycle.batchNoSlotDomains();
        return batchExecutor.test(domainBatchTask, noSlotDomains);
    }

    private boolean isRealtimeLane(DomainBatchTask domainBatchTask) {
        return domainBatchTask.getDispatchLane() != null && domainBatchTask.getDispatchLane().isRealtime();
    }

    private boolean isBatchLane(DomainBatchTask domainBatchTask) {
        return domainBatchTask.getDispatchLane() != null && !domainBatchTask.getDispatchLane().isRealtime();
    }

    private void finalizeCycle(DispatchCycle cycle) {
        // Consumes the relaxation period in cycle units immediately after hold release.
        advanceBatchReleaseSmoothingCycle();

        if (cycle.dispatched() > 0) {
            log.debug("Dispatched {} batches in this cycle. Active workers: {}/{}, Queue remaining: {}",
                    cycle.dispatched(), engineExecutors.activeWorkerCount(), cycle.maxWorkers(), batchQueue.getTotalBatchCount());
        }
    }

    /**
     * Selects the next batch that fits the current cycle policy (Realtime priority, Batch limit, Retry allowance).
     *
     * @param realtimeNoSlotDomains Set of domains excluded due to worker slot shortage in realtime lane
     * @param batchNoSlotDomains Set of domains excluded due to worker slot shortage in batch lane
     * @param batchDispatchedThisCycle Number of batches already dispatched in this cycle
     * @param batchDispatchLimit Maximum allowed batch dispatch count for this cycle
     * @param maxWorkers Maximum worker pool size
     * @return Next executable batch, or {@code null} if none
     */
    private DomainBatchTask pollNextTask(Set<String> realtimeNoSlotDomains, Set<String> batchNoSlotDomains,
                                         int batchDispatchedThisCycle, int batchDispatchLimit, int maxWorkers) {
        boolean hasRealtimeFresh = batchQueue.hasAvailableBatch(DispatchLane.REALTIME_FRESH, realtimeNoSlotDomains);
        boolean strictRealtimeOnly = hasRealtimeFresh && (batchDispatchLimit <= 0);
        List<DispatchLane> pollOrder = strictRealtimeOnly ? REALTIME_ONLY_POLL_ORDER : FULL_POLL_ORDER;
        for (DispatchLane lane : pollOrder) {
            if (!canDispatchLane(lane, batchDispatchedThisCycle, batchDispatchLimit, maxWorkers,
                    realtimeNoSlotDomains, batchNoSlotDomains)) {
                continue;
            }
            Set<String> excludedDomains = lane.isRealtime() ? realtimeNoSlotDomains : batchNoSlotDomains;
            DomainBatchTask task = batchQueue.poll(lane, excludedDomains);
            if (task != null) {
                return task;
            }
        }
        return null;
    }

    /**
     * Determines if the specified lane can be dispatched in the current cycle.
     * Retry lane additionally applies {@link #canDispatchRetry(DispatchLane, int, Set, Set)} policy.
     */
    private boolean canDispatchLane(DispatchLane lane, int batchDispatchedThisCycle, int batchDispatchLimit, int maxWorkers,
                                    Set<String> realtimeNoSlotDomains, Set<String> batchNoSlotDomains) {
        if (lane == null) {
            return false;
        }
        if (!lane.isRealtime() && batchDispatchedThisCycle >= batchDispatchLimit) {
            return false;
        }
        if (!lane.isRetry()) {
            return true;
        }
        return canDispatchRetry(lane, maxWorkers, realtimeNoSlotDomains, batchNoSlotDomains);
    }

    /**
     * Calculates whether Retry lane dispatch is allowed.
     * <p>
     * Determines Retry allowance limit by combining in-flight count within the same mode (Realtime/Batch),
     * Fresh reservation minimum, Retry occupancy cap, and Retry wait aging relaxation threshold.
     *
     * @param retryLane Target Retry lane
     * @param maxWorkers Maximum worker pool size
     * @param realtimeNoSlotDomains Realtime lane excluded domain set
     * @param batchNoSlotDomains Batch lane excluded domain set
     * @return {@code true} if Retry dispatch is possible under current conditions
     */
    boolean canDispatchRetry(DispatchLane retryLane, int maxWorkers, Set<String> realtimeNoSlotDomains, Set<String> batchNoSlotDomains) {
        boolean realtimeLane = retryLane.isRealtime();
        int modeInFlight = realtimeLane ? getRealtimeInFlightCount() : getBatchInFlightCount();
        int otherModeInFlight = realtimeLane ? getBatchInFlightCount() : getRealtimeInFlightCount();
        int retryInFlight = getInFlightCount(retryLane);
        int modeCapacity = Math.max(0, maxWorkers - otherModeInFlight);
        if (modeInFlight >= modeCapacity) {
            return false;
        }

        boolean freshPending = realtimeLane
                ? batchQueue.hasAvailableBatch(DispatchLane.REALTIME_FRESH, realtimeNoSlotDomains)
                : batchQueue.hasAvailableBatch(DispatchLane.BATCH_FRESH, batchNoSlotDomains);
        int freshReserveMin = Math.max(0, sendConfig.getFreshReserveMin());

        int retryCapPercent = realtimeLane
                ? normalizePercent(sendConfig.getRealtimeRetryMaxPercent())
                : normalizePercent(sendConfig.getBatchRetryMaxPercent());

        long oldestRetryAge = batchQueue.getOldestAgeMillis(retryLane,
                realtimeLane ? realtimeNoSlotDomains : batchNoSlotDomains);
        long relaxThresholdMs = Math.max(0L, sendConfig.getRetryAgingRelaxThresholdMs());
        if (oldestRetryAge >= relaxThresholdMs) {
            retryCapPercent = Math.max(retryCapPercent, normalizePercent(sendConfig.getRetryAgingRelaxPercent()));
        }

        int retryLimit;
        if (freshPending) {
            retryLimit = (int) Math.floor(modeCapacity * (retryCapPercent / 100.0d));
            retryLimit = Math.min(retryLimit, Math.max(0, modeCapacity - freshReserveMin));
            if (modeCapacity > 0) {
                retryLimit = Math.max(1, retryLimit);
            }
        } else {
            retryLimit = Math.max(0, modeCapacity - freshReserveMin);
        }

        return retryInFlight < retryLimit;
    }

    /**
     * Updates batch hold start time and relaxation cycle state based on Realtime Fresh waiting status.
     */
    private void updateBatchHoldState(boolean hasRealtimeFreshPending) {
        if (hasRealtimeFreshPending) {
            if (runtimeState.batchHoldStartedAtMs() < 0L) {
                runtimeState.setBatchHoldStartedAtMs(System.currentTimeMillis());
            }
            return;
        }

        if (runtimeState.batchHoldStartedAtMs() >= 0L) {
            runtimeState.setBatchHoldStartedAtMs(-1L);
            runtimeState.setBatchReleaseSmoothingCycles(DEFAULT_BATCH_RELEASE_SMOOTHING_CYCLES);
        }
    }

    /**
     * Calculates the upper limit of how many batch lanes can be dispatched in this cycle.
     * <p>
     * Returns 0 if Realtime Fresh is waiting before hold time elapses,
     * and allows only a limited ratio in the relaxation period immediately after hold release.
     *
     * @param maxWorkers Maximum worker pool size
     * @param hasRealtimeFreshPending Realtime Fresh waiting status
     * @return Batch dispatch allowance limit (Unlimited is {@link Integer#MAX_VALUE})
     */
    int resolveBatchDispatchLimitPerCycle(int maxWorkers, boolean hasRealtimeFreshPending) {
        if (maxWorkers <= 0) {
            return 0;
        }
        if (hasRealtimeFreshPending) {
            long holdMaxMs = resolveBatchHoldMaxMs();
            long holdElapsedMs = runtimeState.batchHoldStartedAtMs() >= 0L
                    ? System.currentTimeMillis() - runtimeState.batchHoldStartedAtMs()
                    : 0L;
            if (holdElapsedMs < holdMaxMs) {
                return 0;
            }
            return computeBatchReleasePerCycle(maxWorkers);
        }
        if (runtimeState.batchReleaseSmoothingCycles() > 0) {
            return computeBatchReleasePerCycle(maxWorkers);
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Decrements the batch release smoothing cycle counter by one step.
     */
    void advanceBatchReleaseSmoothingCycle() {
        int cycles = runtimeState.batchReleaseSmoothingCycles();
        if (cycles > 0) {
            runtimeState.setBatchReleaseSmoothingCycles(cycles - 1);
        }
    }

    private long resolveBatchHoldMaxMs() {
        long configured = sendConfig.getBatchHoldMaxMs();
        if (configured > 0L) {
            return configured;
        }
        return Math.max(10L, (long) schedulerIntervalMs * DEFAULT_BATCH_HOLD_MULTIPLIER);
    }

    private int computeBatchReleasePerCycle(int maxWorkers) {
        int percent = normalizePercent(sendConfig.getBatchReleasePercentPerCycle());
        int computed = (int) Math.floor(maxWorkers * (percent / 100.0d));
        return Math.max(1, computed);
    }

    private int normalizePercent(int value) {
        return Math.min(100, Math.max(0, value));
    }

    private int getInFlightCount(DispatchLane lane) {
        AtomicInteger counter = runtimeState.inFlightByLane().get(lane);
        return counter != null ? Math.max(0, counter.get()) : 0;
    }

    private int getRealtimeInFlightCount() {
        return getInFlightCount(DispatchLane.REALTIME_FRESH) + getInFlightCount(DispatchLane.REALTIME_RETRY);
    }

    private int getBatchInFlightCount() {
        return getInFlightCount(DispatchLane.BATCH_FRESH) + getInFlightCount(DispatchLane.BATCH_RETRY);
    }

    private void completeTaskAsFailure(DomainBatchTask task, Exception cause, String reason) {
        batchResultFinalizer.completeTaskAsFailure(task, cause, reason);
    }

    private static final class DispatchCycle {
        private final int maxWorkers;
        private final int maxDispatchPerCycle;
        private final Set<String> realtimeNoSlotDomains = new HashSet<>();
        private final Set<String> batchNoSlotDomains = new HashSet<>();
        private int batchDispatchLimit;
        private int dispatched;
        private int batchDispatchedThisCycle;

        private DispatchCycle(int maxWorkers, int maxDispatchPerCycle) {
            this.maxWorkers = maxWorkers;
            this.maxDispatchPerCycle = maxDispatchPerCycle;
        }

        private int maxWorkers() {
            return maxWorkers;
        }

        private Set<String> realtimeNoSlotDomains() {
            return realtimeNoSlotDomains;
        }

        private Set<String> batchNoSlotDomains() {
            return batchNoSlotDomains;
        }

        private int batchDispatchLimit() {
            return batchDispatchLimit;
        }

        private void setBatchDispatchLimit(int batchDispatchLimit) {
            this.batchDispatchLimit = batchDispatchLimit;
        }

        private int dispatched() {
            return dispatched;
        }

        private int batchDispatchedThisCycle() {
            return batchDispatchedThisCycle;
        }

        private boolean canDispatchMore() {
            return dispatched < maxDispatchPerCycle;
        }

        private void markDispatched(boolean batchLane) {
            dispatched++;
            if (batchLane) {
                batchDispatchedThisCycle++;
            }
        }
    }
}
