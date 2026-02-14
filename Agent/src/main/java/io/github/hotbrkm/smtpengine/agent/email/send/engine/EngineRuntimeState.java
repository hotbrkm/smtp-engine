package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.send.entry.ExecutionMode;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DispatchLane;

/**
 * Stores shared mutable state during engine execution.
 */
final class EngineRuntimeState {

    private static final int BATCH_STATS_RETENTION_DAYS = 7;
    private final Map<String, Long> bindIpWaitStartByBatchId = new ConcurrentHashMap<>();
    private final Map<DispatchLane, AtomicInteger> inFlightByLane = new ConcurrentHashMap<>();
    private final Map<ExecutionMode, ResourceSlotGroup> resourceSlotGroups;
    private final Map<LocalDate, DailyBatchStats> batchStatsByDate = new ConcurrentHashMap<>();
    private final Clock clock;
    private volatile long batchHoldStartedAtMs = -1L;
    private volatile int batchReleaseSmoothingCycles = 0;

    /**
     * Initializes per-lane counters and per-execution-mode slot groups.
     */
    EngineRuntimeState(EngineRuntimeOptions options) {
        this(options, Clock.systemDefaultZone());
    }

    EngineRuntimeState(EngineRuntimeOptions options, Clock clock) {
        Objects.requireNonNull(options, "options must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        for (DispatchLane lane : DispatchLane.values()) {
            inFlightByLane.put(lane, new AtomicInteger(0));
        }
        Map<ExecutionMode, ResourceSlotGroup> groups = new EnumMap<>(ExecutionMode.class);
        groups.put(ExecutionMode.REALTIME, new ResourceSlotGroup(
                options.realtimeAttachmentMaxInFlight()));
        groups.put(ExecutionMode.BATCH, new ResourceSlotGroup(
                options.batchAttachmentMaxInFlight()));
        this.resourceSlotGroups = Map.copyOf(groups);
    }

    Map<String, Long> bindIpWaitStartByBatchId() {
        return bindIpWaitStartByBatchId;
    }

    Map<DispatchLane, AtomicInteger> inFlightByLane() {
        return inFlightByLane;
    }

    Map<ExecutionMode, ResourceSlotGroup> resourceSlotGroups() {
        return resourceSlotGroups;
    }

    void incrementCompletedBatches() {
        LocalDate today = LocalDate.now(clock);
        purgeExpiredStats(today);
        batchStatsByDate.computeIfAbsent(today, key -> new DailyBatchStats()).completed().incrementAndGet();
    }

    void incrementFailedBatches() {
        LocalDate today = LocalDate.now(clock);
        purgeExpiredStats(today);
        batchStatsByDate.computeIfAbsent(today, key -> new DailyBatchStats()).failed().incrementAndGet();
    }

    int completedBatches() {
        LocalDate today = LocalDate.now(clock);
        purgeExpiredStats(today);
        return batchStatsByDate.values().stream()
                .mapToInt(stats -> stats.completed().get())
                .sum();
    }

    int failedBatches() {
        LocalDate today = LocalDate.now(clock);
        purgeExpiredStats(today);
        return batchStatsByDate.values().stream()
                .mapToInt(stats -> stats.failed().get())
                .sum();
    }

    private void purgeExpiredStats(LocalDate today) {
        LocalDate cutoffExclusive = today.minusDays((long) BATCH_STATS_RETENTION_DAYS - 1);
        batchStatsByDate.keySet().removeIf(day -> day.isBefore(cutoffExclusive));
    }

    long batchHoldStartedAtMs() {
        return batchHoldStartedAtMs;
    }

    void setBatchHoldStartedAtMs(long batchHoldStartedAtMs) {
        this.batchHoldStartedAtMs = batchHoldStartedAtMs;
    }

    int batchReleaseSmoothingCycles() {
        return batchReleaseSmoothingCycles;
    }

    void setBatchReleaseSmoothingCycles(int batchReleaseSmoothingCycles) {
        this.batchReleaseSmoothingCycles = batchReleaseSmoothingCycles;
    }

    private record DailyBatchStats(AtomicInteger completed, AtomicInteger failed) {
        private DailyBatchStats() {
            this(new AtomicInteger(0), new AtomicInteger(0));
        }
    }
}
