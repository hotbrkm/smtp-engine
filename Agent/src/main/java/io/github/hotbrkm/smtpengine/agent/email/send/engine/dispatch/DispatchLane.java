package io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch;

import io.github.hotbrkm.smtpengine.agent.email.send.entry.ExecutionMode;

/**
 * Execution lane for dispatch priority management.
 */
public enum DispatchLane {
    REALTIME_FRESH, REALTIME_RETRY, BATCH_FRESH, BATCH_RETRY;

    /**
     * Returns whether it is a realtime lane.
     */
    public boolean isRealtime() {
        return this == REALTIME_FRESH || this == REALTIME_RETRY;
    }

    /**
     * Returns whether it is a retry lane.
     */
    public boolean isRetry() {
        return this == REALTIME_RETRY || this == BATCH_RETRY;
    }

    /**
     * Returns the Fresh lane corresponding to the execution mode.
     */
    public static DispatchLane forFresh(ExecutionMode mode) {
        return mode == ExecutionMode.REALTIME ? REALTIME_FRESH : BATCH_FRESH;
    }

    /**
     * Returns the Retry lane corresponding to the execution mode.
     */
    public static DispatchLane forRetry(ExecutionMode mode) {
        return mode == ExecutionMode.REALTIME ? REALTIME_RETRY : BATCH_RETRY;
    }
}
